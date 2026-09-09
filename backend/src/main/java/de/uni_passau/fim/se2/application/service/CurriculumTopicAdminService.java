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

import de.uni_passau.fim.se2.domain.entities.CurriculumTopicDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.CurriculumTopicRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Admin use case: extend and edit the curriculum tree — top-level entries
 * (Jahrgangsstufen or university modules) and the Lernbereiche below them.
 * Validation lives
 * here, as in {@link CurriculumExampleAdminService}: not-found conditions throw
 * {@link IllegalArgumentException} (mapped to 404 by the controller), invalid
 * input throws {@link DomainValidationException} (400 via the global handler).
 *
 * <p>German is the source language and English is optional throughout — a topic
 * without a translation falls back to German in the UI rather than rendering
 * blank, the same rule db/init/11_migration_curriculum_i18n.sql follows.
 *
 * <p>A top-level topic comes in two shapes. A Jahrgangsstufe has a grade level
 * and gets its name derived from it; a module ("Programmieren 1") has no grade
 * number and carries its own name. Which shape it is follows from whether a
 * grade level was given, independently of {@code educationLevel} — nothing
 * stops a university semester from being numbered, or a school entry from
 * being named.
 */
@Service
public class CurriculumTopicAdminService {

    /** curriculum_topics.track is varchar(30); learning_area is varchar(255). */
    private static final int TRACK_MAX = 30;
    private static final int LEARNING_AREA_MAX = 255;

    /** Grade 5-13 covers the Gymnasium; the upper bound only rules out typos. */
    private static final int GRADE_MIN = 1;
    private static final int GRADE_MAX = 99;

    /** Mirrors the curriculum_topics_education_level_check constraint. */
    private static final Set<String> EDUCATION_LEVELS = Set.of("SCHOOL", "UNIVERSITY");
    private static final String DEFAULT_EDUCATION_LEVEL = "SCHOOL";

    private final CurriculumTopicRepository topicRepo;

    public CurriculumTopicAdminService(CurriculumTopicRepository topicRepo) {
        this.topicRepo = topicRepo;
    }

    /**
     * Creates a top-level curriculum entry: a Jahrgangsstufe when a grade level
     * is given, otherwise a named module. A Jahrgangsstufe's name is derived
     * from grade level and track rather than typed, because that is exactly
     * what the curriculum page displays for it — asking for a name too would
     * let the two drift apart. A module has no such derivation, so it keeps the
     * name it was given.
     */
    public CurriculumTopicDTO createTopLevelTopic(CurriculumTopicDTO dto) {
        Integer grade = dto.getGradeLevel();
        String name = trimToNull(dto.getName());
        if (grade == null && name == null) {
            throw new DomainValidationException("Either gradeLevel or name is required.");
        }
        if (grade != null && (grade < GRADE_MIN || grade > GRADE_MAX)) {
            throw new DomainValidationException(
                    "gradeLevel must be between " + GRADE_MIN + " and " + GRADE_MAX + ".");
        }

        String track = trimToNull(dto.getTrack());
        String trackEn = trimToNull(dto.getTrackEn());
        requireAtMost(track, TRACK_MAX, "track");
        requireAtMost(trackEn, TRACK_MAX, "trackEn");

        CurriculumTopicDTO topic = new CurriculumTopicDTO();
        topic.setId(UUID.randomUUID());
        topic.setParentId(null);
        topic.setGradeLevel(grade);
        topic.setTrack(track);
        topic.setTrackEn(trackEn);
        if (grade != null) {
            topic.setName(gradeLabel("Jgst. ", grade, track));
            topic.setNameEn(gradeLabel("Grade ", grade, trackEn != null ? trackEn : track));
        } else {
            topic.setName(name);
            topic.setNameEn(trimToNull(dto.getNameEn()));
        }
        topic.setDescription(trimToNull(dto.getDescription()));
        topic.setDescriptionEn(trimToNull(dto.getDescriptionEn()));
        topic.setLearningArea(null);
        topic.setEducationLevel(normalizeEducationLevel(dto.getEducationLevel()));
        topic.setSortOrder(dto.getSortOrder() > 0 ? dto.getSortOrder() : nextTopLevelSortOrder(grade));

        requireNameIsFree(topic.getName(), null);
        topicRepo.save(topic);
        return topic;
    }

    /**
     * Creates a Lernbereich under an existing top-level entry. Grade level,
     * track and education level are inherited from the parent, so a sub-topic
     * cannot contradict the Jahrgangsstufe or module it hangs under.
     */
    public CurriculumTopicDTO createSubTopic(UUID parentId, CurriculumTopicDTO dto) {
        CurriculumTopicDTO parent = topicRepo.findById(parentId)
                .orElseThrow(() -> new IllegalArgumentException("Curriculum topic not found: " + parentId));
        if (parent.getParentId() != null) {
            // The curriculum page renders exactly two levels; a deeper topic
            // would be created but never reachable.
            throw new DomainValidationException(
                    "Sub-topics can only be added to a top-level curriculum entry.");
        }

        String name = trimToNull(dto.getName());
        if (name == null) {
            throw new DomainValidationException("name is required.");
        }
        String learningArea = trimToNull(dto.getLearningArea());
        requireAtMost(learningArea, LEARNING_AREA_MAX, "learningArea");

        CurriculumTopicDTO topic = new CurriculumTopicDTO();
        topic.setId(UUID.randomUUID());
        topic.setParentId(parent.getId());
        topic.setGradeLevel(parent.getGradeLevel());
        topic.setTrack(parent.getTrack());
        topic.setTrackEn(parent.getTrackEn());
        topic.setName(name);
        topic.setNameEn(trimToNull(dto.getNameEn()));
        topic.setDescription(trimToNull(dto.getDescription()));
        topic.setDescriptionEn(trimToNull(dto.getDescriptionEn()));
        topic.setLearningArea(learningArea);
        topic.setEducationLevel(normalizeEducationLevel(parent.getEducationLevel()));
        topic.setSortOrder(dto.getSortOrder() > 0 ? dto.getSortOrder() : nextChildSortOrder(parent.getId()));

        requireNameIsFree(topic.getName(), null);
        topicRepo.save(topic);
        return topic;
    }

    /**
     * Edits an existing entry, at either level. Which fields an edit may touch
     * follows from where the topic sits: a top-level entry owns its grade
     * level, track and education level, while a sub-topic inherits all three
     * and owns its learning area instead. Neither can be moved to a different
     * parent — that would be a different operation, and the page offers no way
     * to ask for it.
     *
     * <p>A top-level entry may also change shape: clearing its grade level
     * turns a Jahrgangsstufe into a module, which then needs a name of its own,
     * and giving a module a grade level turns it back.
     */
    public CurriculumTopicDTO update(UUID id, CurriculumTopicDTO dto) {
        CurriculumTopicDTO existing = topicRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Curriculum topic not found: " + id));

        return existing.getParentId() == null
                ? updateTopLevel(existing, dto)
                : updateSubTopic(existing, dto);
    }

    private CurriculumTopicDTO updateTopLevel(CurriculumTopicDTO existing, CurriculumTopicDTO dto) {
        Integer grade = dto.getGradeLevel();
        String name = trimToNull(dto.getName());
        if (grade == null && name == null) {
            throw new DomainValidationException("Either gradeLevel or name is required.");
        }
        if (grade != null && (grade < GRADE_MIN || grade > GRADE_MAX)) {
            throw new DomainValidationException(
                    "gradeLevel must be between " + GRADE_MIN + " and " + GRADE_MAX + ".");
        }

        String track = trimToNull(dto.getTrack());
        String trackEn = trimToNull(dto.getTrackEn());
        requireAtMost(track, TRACK_MAX, "track");
        requireAtMost(trackEn, TRACK_MAX, "trackEn");

        CurriculumTopicDTO topic = new CurriculumTopicDTO();
        topic.setId(existing.getId());
        topic.setParentId(null);
        topic.setGradeLevel(grade);
        topic.setTrack(track);
        topic.setTrackEn(trackEn);
        if (grade != null) {
            topic.setName(gradeLabel("Jgst. ", grade, track));
            topic.setNameEn(gradeLabel("Grade ", grade, trackEn != null ? trackEn : track));
        } else {
            topic.setName(name);
            topic.setNameEn(trimToNull(dto.getNameEn()));
        }
        topic.setDescription(trimToNull(dto.getDescription()));
        topic.setDescriptionEn(trimToNull(dto.getDescriptionEn()));
        topic.setLearningArea(null);
        topic.setEducationLevel(normalizeEducationLevel(dto.getEducationLevel()));
        // Re-sorting is not part of editing: a grade whose number changes would
        // otherwise jump to a position the admin never asked for.
        topic.setSortOrder(dto.getSortOrder() > 0 ? dto.getSortOrder() : existing.getSortOrder());

        requireNameIsFree(topic.getName(), existing.getId());
        topicRepo.save(topic);
        cascadeToChildren(topic);
        return topic;
    }

    private CurriculumTopicDTO updateSubTopic(CurriculumTopicDTO existing, CurriculumTopicDTO dto) {
        String name = trimToNull(dto.getName());
        if (name == null) {
            throw new DomainValidationException("name is required.");
        }
        String learningArea = trimToNull(dto.getLearningArea());
        requireAtMost(learningArea, LEARNING_AREA_MAX, "learningArea");

        CurriculumTopicDTO topic = new CurriculumTopicDTO();
        topic.setId(existing.getId());
        topic.setParentId(existing.getParentId());
        // Inherited, not edited: these keep coming from the parent.
        topic.setGradeLevel(existing.getGradeLevel());
        topic.setTrack(existing.getTrack());
        topic.setTrackEn(existing.getTrackEn());
        topic.setEducationLevel(normalizeEducationLevel(existing.getEducationLevel()));
        topic.setName(name);
        topic.setNameEn(trimToNull(dto.getNameEn()));
        topic.setDescription(trimToNull(dto.getDescription()));
        topic.setDescriptionEn(trimToNull(dto.getDescriptionEn()));
        topic.setLearningArea(learningArea);
        topic.setSortOrder(dto.getSortOrder() > 0 ? dto.getSortOrder() : existing.getSortOrder());

        requireNameIsFree(topic.getName(), existing.getId());
        topicRepo.save(topic);
        return topic;
    }

    /**
     * Sub-topics keep their own copy of the fields they inherit, so that a row
     * can be classified without looking at its parent. Editing the parent has
     * to push those copies along, or a Jahrgangsstufe moved to grade 10 would
     * leave its Lernbereiche claiming grade 9 — to the topic list, and to the
     * generation prompt.
     */
    private void cascadeToChildren(CurriculumTopicDTO parent) {
        for (CurriculumTopicDTO child : topicRepo.findByParentId(parent.getId())) {
            boolean unchanged = Objects.equals(child.getGradeLevel(), parent.getGradeLevel())
                    && Objects.equals(child.getTrack(), parent.getTrack())
                    && Objects.equals(child.getTrackEn(), parent.getTrackEn())
                    && Objects.equals(child.getEducationLevel(), parent.getEducationLevel());
            if (unchanged) {
                continue;
            }
            child.setGradeLevel(parent.getGradeLevel());
            child.setTrack(parent.getTrack());
            child.setTrackEn(parent.getTrackEn());
            child.setEducationLevel(parent.getEducationLevel());
            topicRepo.save(child);
        }
    }

    private static String gradeLabel(String prefix, int grade, String track) {
        return track == null ? prefix + grade : prefix + grade + " — " + track;
    }

    /**
     * curriculum_topics.name is UNIQUE, so a clash would otherwise surface as a
     * 500 from the driver. Checked case-insensitively: two Jahrgangsstufen whose
     * names differ only in case would be indistinguishable in the topic list.
     * {@code selfId} is the topic being edited, which may of course keep its own
     * name; it is null when creating.
     */
    private void requireNameIsFree(String name, UUID selfId) {
        String candidate = name.toLowerCase(Locale.ROOT);
        boolean taken = topicRepo.findAll().stream()
                .filter(t -> !t.getId().equals(selfId))
                .anyMatch(t -> t.getName() != null && t.getName().toLowerCase(Locale.ROOT).equals(candidate));
        if (taken) {
            throw new DomainValidationException("A curriculum topic named \"" + name + "\" already exists.");
        }
    }

    private static String normalizeEducationLevel(String value) {
        String level = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (level.isEmpty()) {
            return DEFAULT_EDUCATION_LEVEL;
        }
        if (!EDUCATION_LEVELS.contains(level)) {
            throw new DomainValidationException("educationLevel must be one of: SCHOOL, UNIVERSITY.");
        }
        return level;
    }

    /**
     * Jahrgangsstufe 12 sorts at 12; a second track for grade 12 sorts at 1012,
     * a third at 2012. A module has no number to sort by, so it goes last.
     */
    private int nextTopLevelSortOrder(Integer grade) {
        List<CurriculumTopicDTO> siblings = topicRepo.findTopLevel();
        if (grade == null) {
            return siblings.stream().mapToInt(CurriculumTopicDTO::getSortOrder).max().orElse(0) + 1;
        }
        int order = grade;
        while (hasSortOrder(siblings, order)) {
            order += 1000;
        }
        return order;
    }

    private int nextChildSortOrder(UUID parentId) {
        return topicRepo.findByParentId(parentId).stream()
                .mapToInt(CurriculumTopicDTO::getSortOrder)
                .max()
                .orElse(0) + 1;
    }

    private static boolean hasSortOrder(List<CurriculumTopicDTO> topics, int order) {
        return topics.stream().anyMatch(t -> t.getSortOrder() == order);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void requireAtMost(String value, int max, String field) {
        if (value != null && value.length() > max) {
            throw new DomainValidationException(field + " must be at most " + max + " characters.");
        }
    }
}
