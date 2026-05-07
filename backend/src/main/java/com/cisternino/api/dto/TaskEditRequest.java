package com.cisternino.api.dto;

import lombok.Getter;
import lombok.Setter;

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
}
