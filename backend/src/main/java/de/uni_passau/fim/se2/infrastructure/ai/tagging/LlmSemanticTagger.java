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
package de.uni_passau.fim.se2.infrastructure.ai.tagging;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.uni_passau.fim.se2.domain.interfaces.AiModelClient;
import de.uni_passau.fim.se2.infrastructure.ai.IdentityLoader;

/**
 * AP19 Teil A: LLM-powered semantic tagger.
 *
 * <p>
 * Emits domain / algorithm / data-structure tags (e.g. "fibonacci", "binary-search", "caesar-chiffre"). Deliberately
 * separated from the AP25 structural concept detection, which covers syntax-level constructs (for-loop, recursion,
 * if-else, …).
 *
 * <p>
 * Each tag comes back as a {@code {"de": …, "en": …}} pair: the German slug is the canonical tag name, the English one
 * is stored as its translation for the English UI.
 *
 * <p>
 * Always invoked from a background thread via {@link de.uni_passau.fim.se2.application.events.SemanticTaggingListener}.
 * LLM cost is ~0.5 cent per sequence.
 */
@Component
public class LlmSemanticTagger {

    private static final Logger log = LoggerFactory.getLogger(LlmSemanticTagger.class);

    private static final int MAX_INPUT_CHARS = 6000;   // safety cap before sending to LLM
    private static final int MAX_SUMMARY_CHARS = 500;

    private final AiModelClient aiModelClient;
    private final IdentityLoader identityLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LlmSemanticTagger(AiModelClient aiModelClient, IdentityLoader identityLoader) {
        this.aiModelClient = aiModelClient;
        this.identityLoader = identityLoader;
    }

    /**
     * Returns a semantic tag result for the given sequence content, or {@code null} on failure.
     *
     * <p>
     * Failure modes (all logged, all return null):
     * <ul>
     * <li>Identity file missing</li>
     * <li>LLM call throws, or the provider does not support semantic tagging</li>
     * <li>LLM response not valid JSON</li>
     * <li>LLM returns empty {@code primary_tags}</li>
     * </ul>
     *
     * @param language          the programming language of the sequence
     * @param combinedStatement the problem statements of all tasks
     * @param combinedCode      the solutions of all tasks
     * @return the tags, or null on failure
     */
    public SemanticTagResult tag(String language, String combinedStatement, String combinedCode) {
        if (combinedCode == null || combinedCode.isBlank()) {
            return null;
        }

        String identity;
        try {
            identity = identityLoader.load("SequenceSemanticTagger");
        }
        catch (Exception e) {
            log.warn("SequenceSemanticTagger identity not found: {}", e.getMessage());
            return null;
        }

        String userPrompt = buildUserPrompt(language, combinedStatement, combinedCode);

        String response;
        try {
            // A single blocking call — caller is already on a background thread.
            response = aiModelClient.generateSemanticTags(identity, userPrompt);
        }
        catch (Exception e) {
            log.warn("LLM call for semantic tagging failed: {}", e.getMessage());
            return null;
        }

        return parse(response);
    }

    // ─────────────────────────────────────────────────────────────────────

    private String buildUserPrompt(String language, String combinedStatement, String combinedCode) {
        String statementPart = combinedStatement == null ? "" : combinedStatement.trim();
        String codePart = combinedCode.trim();

        // Clamp to MAX_INPUT_CHARS: prefer keeping the statement intact, then clip the code.
        if (statementPart.length() > 1500) {
            statementPart = statementPart.substring(0, 1500) + "…";
        }
        int budget = MAX_INPUT_CHARS - statementPart.length();
        if (codePart.length() > budget) {
            codePart = codePart.substring(0, Math.max(100, budget)) + "\n…";
        }

        return """
            Language: %s

            Combined task statements across all tiers:
            %s

            Combined code solutions across all tiers:
            ```
            %s
            ```

            Follow the output format strictly. No commentary.
            """.formatted(language == null ? "unknown" : language, statementPart, codePart);
    }

    @SuppressWarnings("checkstyle:NPathComplexity")
    private SemanticTagResult parse(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        String json = response.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        }
        catch (JsonProcessingException | RuntimeException e) {
            log.warn(
                "Semantic tagger returned non-JSON, ignoring. Raw response (trunc): {}",
                truncate(response, 200)
            );
            return null;
        }

        Map<String, String> translations = new LinkedHashMap<>();
        List<String> primary = extractTags(root.get("primary_tags"), translations);
        List<String> aliases = extractTags(root.get("aliases"), translations);

        if (primary.isEmpty()) {
            log.debug("Semantic tagger returned no primary_tags");
            return null;
        }

        String summary = null;
        JsonNode summaryNode = root.get("short_description");
        if (summaryNode != null && summaryNode.isTextual()) {
            String s = summaryNode.asText().trim();
            if (!s.isEmpty()) {
                summary = s.length() > MAX_SUMMARY_CHARS ? s.substring(0, MAX_SUMMARY_CHARS) : s;
            }
        }

        return new SemanticTagResult(primary, aliases, translations, summary);
    }

    /**
     * Reads a tag array. Entries are {@code {"de": …, "en": …}} objects; plain strings are accepted too (treated as the
     * German slug, without translation). English slugs are collected into {@code translations}, keyed by the German
     * slug.
     */
    private List<String> extractTags(JsonNode node, Map<String, String> translations) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (JsonNode n : node) {
            String de;
            String en = null;
            if (n.isTextual()) {
                de = slugify(n.asText());
            }
            else if (n.isObject()) {
                de = slugify(n.path("de").asText(""));
                en = slugify(n.path("en").asText(""));
            }
            else {
                continue;
            }
            if (de.isEmpty() || out.contains(de)) {
                continue;
            }
            out.add(de);
            if (en != null && !en.isEmpty()) {
                translations.putIfAbsent(de, en);
            }
        }
        return out;
    }

    /** Lowercase hyphenated slug; defence-in-depth against characters the slug spec forbids. */
    private static String slugify(String raw) {
        String v = raw.trim().toLowerCase(Locale.ROOT);
        return v.replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
    }

    private String truncate(String s, int n) {
        if (s == null) {
            return "";
        }
        return s.length() <= n ? s : s.substring(0, n) + "…";
    }

    /**
     * Result of a single semantic tagging pass.
     *
     * @param translations German tag slug → English tag slug, for every tag the LLM translated
     */
    public record SemanticTagResult(
        List<String> primaryTags, List<String> aliases,
        Map<String, String> translations, String shortDescription
    ) {
    }
}
