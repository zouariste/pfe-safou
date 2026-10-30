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
 * The outcome of one distractor generation call (AP15).
 *
 * @param lines the distractor lines, each ending with {@code #distractor}
 * @param plan  the distractor plan JSON ({@code {"type", "rationale"}}) the lines were built from: the one passed in,
 *              the one the model chose when none was, or null if it chose none we recognise
 */
public record GeneratedDistractors(List<String> lines, String plan) {

    /** Nothing generated. */
    public static final GeneratedDistractors NONE = new GeneratedDistractors(List.of(), null);

    /** @return the lines as stored in the distractors column, one per line */
    public String text() {
        return String.join("\n", lines);
    }
}
