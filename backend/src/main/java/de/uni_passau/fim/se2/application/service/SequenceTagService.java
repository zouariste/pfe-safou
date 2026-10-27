/*
 * Copyright (C) 2026 CodeOwl contributors
 *
 * This file is part of CodeOwl.
 *
 * CodeOwl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * CodeOwl is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with CodeOwl. If not, see <http://www.gnu.org/licenses/>.
 */
package de.uni_passau.fim.se2.application.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.uni_passau.fim.se2.domain.entities.SequenceTagDTO;
import de.uni_passau.fim.se2.domain.interfaces.SequenceTagRepository;
import de.uni_passau.fim.se2.domain.model.ConceptCatalog;

/**
 * Central service for sequence tags (AP19 Teil A), shown to users as <em>topics</em>.
 *
 * <p>
 * Topics describe what a sequence is about and are disjoint from its concepts (which live in
 * {@code tiered_task_sequence_concepts}). Two sources, stored in the same table:
 * <ul>
 * <li><b>llm</b> &mdash; semantic tags derived from an LLM call. Written asynchronously via
 * {@link de.uni_passau.fim.se2.application.events.SequenceGeneratedEvent} (see AsyncConfig).</li>
 * <li><b>manual</b> &mdash; teacher-set. Must never be overwritten by auto-retag.</li>
 * </ul>
 *
 * <p>
 * All tag names are normalized to lowercase-trimmed slugs by the DAO layer, so callers can pass user-typed variants
 * ("Fibonacci", " fibonacci ", "FIBONACCI") without worry. The canonical name is German; LLM tags also carry an English
 * slug in {@code topic_translations}.
 */
@Service
public class SequenceTagService {

    private static final Logger log = LoggerFactory.getLogger(SequenceTagService.class);

    private final SequenceTagRepository tagRepo;
    private final ConceptCatalog conceptCatalog;

    public SequenceTagService(SequenceTagRepository tagRepo, ConceptCatalog conceptCatalog) {
        this.tagRepo = tagRepo;
        this.conceptCatalog = conceptCatalog;
    }

    // ─────────────────────────────────────────────────────────────────────
    // LLM semantic tagging
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Writes semantic LLM tags (source='llm'). Primary and alias are both stored so they all contribute to search;
     * primaries get a higher confidence.
     *
     * <p>
     * Tags that name a concept — in German or in their English translation — are dropped: the prompt asks the LLM not
     * to emit them, this makes sure, so topics and concepts stay disjoint.
     *
     * @param translations canonical (German) tag name → English slug; may be null or partial
     * @param sequenceId   the sequence to tag
     * @param primaryTags  the main topic tags
     * @param aliases      alternative names for the same topics
     */
    @SuppressWarnings({ "checkstyle:MethodLength", "checkstyle:NPathComplexity" })
    public void writeLlmTags(
        UUID sequenceId, List<String> primaryTags, List<String> aliases,
        Map<String, String> translations
    ) {
        if (sequenceId == null) {
            return;
        }

        Map<String, String> english = translations == null ? Map.of() : translations;
        Set<String> conceptNames = conceptCatalog.getConceptNamesAsTopicSlugs();
        Predicate<String> namesConcept = tag -> conceptNames.contains(tag)
            || conceptNames.contains(ConceptCatalog.toTopicSlug(english.get(tag)));

        List<SequenceTagDTO> tags = new java.util.ArrayList<>();
        if (primaryTags != null) {
            for (String p : primaryTags) {
                if (p == null || p.isBlank()) {
                    continue;
                }
                tags.add(new SequenceTagDTO(sequenceId, p.trim().toLowerCase(Locale.ROOT), "llm", 0.9f));
            }
        }
        if (aliases != null) {
            for (String a : aliases) {
                if (a == null || a.isBlank()) {
                    continue;
                }
                tags.add(new SequenceTagDTO(sequenceId, a.trim().toLowerCase(Locale.ROOT), "llm", 0.6f));
            }
        }
        int before = tags.size();
        tags.removeIf(t -> namesConcept.test(t.getTagName()));
        if (tags.size() < before) {
            log.debug("Dropped {} LLM tags naming a concept for sequence {}", before - tags.size(), sequenceId);
        }
        if (tags.isEmpty()) {
            return;
        }

        try {
            // Replace previous LLM tags so a re-tag doesn't accumulate stale ones.
            tagRepo.replaceForSource(sequenceId, "llm", tags);
            log.debug(
                "LLM-tagged sequence {} with {} tags ({} primary, {} alias)",
                sequenceId, tags.size(),
                primaryTags == null ? 0 : primaryTags.size(),
                aliases == null ? 0 : aliases.size()
            );
        }
        catch (Exception e) {
            log.warn("LLM tag write failed for sequence {}: {}", sequenceId, e.getMessage());
            return;
        }

        Map<String, String> keptTranslations = new java.util.HashMap<>();
        for (SequenceTagDTO t : tags) {
            String en = english.get(t.getTagName());
            if (en != null) {
                keptTranslations.put(t.getTagName(), en);
            }
        }
        if (!keptTranslations.isEmpty()) {
            try {
                tagRepo.upsertTranslations(keptTranslations);
            }
            catch (Exception e) {
                log.warn("Topic translation write failed for sequence {}: {}", sequenceId, e.getMessage());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Manual tag CRUD (teacher-facing)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Adds a manually-set tag. Idempotent via ON CONFLICT DO NOTHING.
     *
     * @param sequenceId the sequence to tag
     * @param tagName    the tag to add
     */
    public void addManualTag(UUID sequenceId, String tagName) {
        if (sequenceId == null || tagName == null || tagName.isBlank()) {
            return;
        }
        tagRepo.upsertAll(
            List.of(
                new SequenceTagDTO(sequenceId, tagName.trim().toLowerCase(Locale.ROOT), "manual")
            )
        );
    }

    /**
     * Removes a tag regardless of source. The teacher is the final authority.
     *
     * @param sequenceId the sequence
     * @param tagName    the tag to remove
     */
    public void removeTag(UUID sequenceId, String tagName) {
        tagRepo.delete(sequenceId, tagName);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Read helpers
    // ─────────────────────────────────────────────────────────────────────

    public List<SequenceTagDTO> getTagsFor(UUID sequenceId) {
        return tagRepo.findBySequenceId(sequenceId);
    }

    public Map<UUID, List<SequenceTagDTO>> getTagsForAll(List<UUID> sequenceIds) {
        return tagRepo.findBySequenceIds(sequenceIds);
    }

    public List<SequenceTagRepository.TagCount> getTagCloud() {
        return tagRepo.findAllTagsWithCounts();
    }

    public List<String> autocomplete(String prefix, int limit) {
        return tagRepo.findTagsByPrefix(prefix, limit);
    }

    /**
     * Canonical tag name → English slug, for localizing topic chips in the UI.
     *
     * @return the English slug for each canonical tag name
     */
    public Map<String, String> getTopicTranslations() {
        return tagRepo.findAllTranslations();
    }

    /**
     * Groups tags by source for a single sequence — convenient for UI rendering which typically shows manual and LLM
     * tags differently from auto ones.
     *
     * @param sequenceId the sequence
     * @return the sequence's tag names, keyed by source
     */
    public Map<String, List<String>> getTagsGroupedBySource(UUID sequenceId) {
        return tagRepo.findBySequenceId(sequenceId).stream()
            .collect(
                Collectors.groupingBy(
                    SequenceTagDTO::getSource,
                    Collectors.mapping(SequenceTagDTO::getTagName, Collectors.toList())
                )
            );
    }

    // ─────────────────────────────────────────────────────────────────────
    // AP19 A16: admin — new-topics report + tag merge
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Tags that appeared for the first time within the past {@code daysBack} days. Used by the admin dashboard's weekly
     * "new topics" widget — a cheap signal for "the LLM is coining something we should canonicalise" or "teachers are
     * tagging a new recurring theme".
     *
     * @param daysBack how far to look back (clamped to 1..90 days)
     * @param limit    max rows (clamped by the DAO to 1..200)
     * @return the new tags, most used first
     */
    public List<SequenceTagRepository.NewTopic> getNewTopics(int daysBack, int limit) {
        int days = Math.max(1, Math.min(daysBack, 90));
        Instant cutoff = Instant.now().minus(Duration.ofDays(days));
        return tagRepo.findNewTopicsSince(cutoff, limit);
    }

    /**
     * Renames a tag across the whole system. Intended for admins cleaning up near-duplicates ("rekursion" →
     * "recursion", "fib-folge" → "fibonacci"). Idempotent — merging a tag into itself or into a non-existent source is
     * a safe no-op.
     *
     * @param fromTagName the tag to merge away
     * @param toTagName   the tag to merge it into
     * @return total rows affected (duplicates dropped + renames)
     */
    public int mergeTags(String fromTagName, String toTagName) {
        int affected = tagRepo.mergeTags(fromTagName, toTagName);
        log.info("Merged tag '{}' into '{}' (rows affected: {})", fromTagName, toTagName, affected);
        return affected;
    }
}
