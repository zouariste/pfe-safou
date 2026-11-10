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
package de.uni_passau.fim.se2.domain.model;

import java.util.List;

/**
 * Tells the teacher that a generated sequence has tasks longer than the line limit.
 *
 * <p>
 * Such a sequence is still saved and returned rather than regenerated automatically: each regeneration runs the whole
 * LLM pipeline again, which on a slow self-hosted model takes minutes. The teacher decides instead whether to keep the
 * sequence, discard it, or try again.
 *
 * @param maxLoc the line limit that applied
 * @param tasks  the tasks over the limit, in tier order
 */
public record LocLimitWarning(int maxLoc, List<TaskLoc> tasks) {

    /**
     * A task over the line limit.
     *
     * @param tierLevel   the task's tier
     * @param linesOfCode the task's lines of code
     */
    public record TaskLoc(int tierLevel, int linesOfCode) {
    }
}
