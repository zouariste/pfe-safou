package com.cisternino.domain.entities;

import lombok.*;

import java.util.List;

/**
 * AP20: Constraints that control what programming constructs are allowed/forbidden
 * in generated task sequences. Stored as JSONB in the sequences table.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationConstraints {

    /**
     * Constructs that MUST NOT appear in generated code.
     * e.g. ["ternary", "lambda", "list comprehension"]
     */
    private List<String> blacklist;

    /**
     * If non-empty, ONLY these constructs may appear.
     * e.g. ["if/else", "for", "while", "print"]
     */
    private List<String> whitelist;

    /**
     * Controls how steeply difficulty increases across tiers.
     * Values: "gentle", "moderate", "steep"
     */
    private String difficultySlope;

    /**
     * Free-text teacher instructions injected into the generation prompt.
     * e.g. "Use German variable names", "Focus on string manipulation"
     */
    private String additionalContext;

    /**
     * Returns true if any constraint is actually set.
     */
    public boolean hasAny() {
        return (blacklist != null && !blacklist.isEmpty())
                || (whitelist != null && !whitelist.isEmpty())
                || (difficultySlope != null && !difficultySlope.isBlank())
                || (additionalContext != null && !additionalContext.isBlank());
    }
}
