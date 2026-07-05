package com.cisternino.api.dto;

import com.cisternino.domain.entities.SplitPlan;
import lombok.*;

import java.util.List;

/**
 * Request body for POST /tasks/manual — creating a task sequence without LLM.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ManualTaskRequest {

    /** Programming language (required). */
    private String language;

    /** Concepts covered by this sequence (required, at least one). */
    private List<String> concepts;

    /** The individual tier tasks (1–3 entries expected). */
    private List<ManualTierEntry> tiers;

    /**
     * One tier inside a manual sequence.
     */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ManualTierEntry {
        /** Tier level (1, 2, or 3). */
        private int tierLevel;
        /** Problem statement / instructions for this tier. */
        private String statement;
        /** The code solution (the lines that become Parsons blocks). */
        private String solution;
        /**
         * AP31: optional inline-split entries for this tier (one per split line).
         * Each must reconstruct its solution line (prefix + join(fragments) + suffix).
         */
        private List<SplitPlan.SplitEntry> splits;
    }
}
