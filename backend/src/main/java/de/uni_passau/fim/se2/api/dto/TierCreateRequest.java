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
package de.uni_passau.fim.se2.api.dto;

import java.util.List;

import de.uni_passau.fim.se2.domain.entities.SplitPlan;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for POST /api/sequence/{id}/tasks: a new tier, written by the teacher.
 *
 * <p>
 * The same fields as {@link TaskEditRequest}, minus the adjustment rating — there is no generated original to rate —
 * plus where the tier goes.
 */
@Getter
@Setter
public class TierCreateRequest {

    private String problemStatement;
    private String code;

    /** Optional distractor lines (newline-separated, each ending with #distractor). */
    private String distractors;

    /** Optional inline-split entries; each must reconstruct its {@code code} line. */
    private List<SplitPlan.SplitEntry> splits;

    /** AP22: Optional read-only code above the puzzle. */
    private String contextBefore;

    /** AP22: Optional read-only code below the puzzle. */
    private String contextAfter;

    /**
     * The tier level the new tier takes, from 1 to one past the current last tier. Tiers at or above it move up by one.
     * Null appends it at the end.
     */
    private Integer tierLevel;
}
