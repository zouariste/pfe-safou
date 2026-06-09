package com.cisternino.domain.entities;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores the change-analysis results for a task (AP12 — Change-Analysis-Engine).
 * One analysis per task, comparing current version against the original.
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskChangeAnalysisDTO {

    private UUID id;
    private UUID taskId;
    private UUID taskOriginalId;
    private LocalDateTime analyzedAt;

    // ── Text-level metrics ──
    private Double textLevenshteinRatio;
    private Double textJaccardSimilarity;
    private Integer textWordsAdded;
    private Integer textWordsRemoved;
    private Integer textWordsModified;
    private Integer textLengthDelta;

    // ── Code-level metrics ──
    private Double codeLevenshteinRatio;
    private Integer codeLinesAdded;
    private Integer codeLinesRemoved;
    private Integer codeLinesModified;
    private Integer codeLinesReordered;
    private Integer codeLocDelta;
    private Integer codeComplexityDelta;
    private Double codeTokenChangeRatio;

    // ── AST-level metrics ──
    private Integer astNodesAdded;
    private Integer astNodesRemoved;
    private Integer astNodesModified;
    private Double astStructuralScore;
    private String astChangeTypes;       // JSONB stored as String
    private String astDistractorChanges; // JSONB stored as String

    // ── Overall ──
    private Double overallChangeScore;
    private String changeCategory;
    private Integer teacherRating;
}
