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

import java.util.Locale;

/**
 * Shows code to the LLM with a 1-based number in front of every line, for prompts whose answer names lines by number.
 * Left to count lines itself, a model miscounts on longer code (smaller models more often than GPT) and the answer
 * marks the wrong lines.
 */
final class NumberedCode {

    /** How the prompts explain the format; they put it right before the code. */
    static final String EXPLANATION = "Each line is shown as \"<line number> | <code>\". The number and the bar are "
        + "not part of the code; use the number to refer to the line.";

    private NumberedCode() {
    }

    /**
     * Numbers the lines of the code, counting from 1 and splitting on {@code \n} the way the annotators apply the
     * answer, so blank lines are numbered too.
     *
     * @param code the code
     * @return the code with {@code "<n> | "} in front of every line
     */
    static String of(String code) {
        String[] lines = code.split("\n", -1);
        int width = String.valueOf(lines.length).length();
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                out.append('\n');
            }
            out.append(String.format(Locale.ROOT, "%" + width + "d | %s", i + 1, lines[i]));
        }
        return out.toString();
    }
}
