package de.uni_passau.fim.se2.api.dto;

import de.uni_passau.fim.se2.domain.entities.SplitPlan;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * Request body for PUT /api/tasks/{taskId} (AP9).
 */
@Getter
@Setter
public class TaskEditRequest {
    private String problemStatement;
    private String code;

    /**
     * Required on first edit only (1-5).
     * Rates how much adjustment the generated content needed.
     */
    private Integer adjustmentRating;

    /** AP15: Optional distractor lines (newline-separated, each ending with #distractor). */
    private String distractors;

    /**
     * AP31: Optional inline-split entries. When non-null the split plan is replaced with
     * these (an empty list clears it); when null the existing split plan is left untouched.
     * Each entry must reconstruct its {@code code} line (prefix + join(fragments) + suffix).
     */
    private List<SplitPlan.SplitEntry> splits;
}
