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
package de.uni_passau.fim.se2.application.util;

/**
 * AP22 (context mode): helpers for the read-only {@code context_before} / {@code context_after} blocks around a puzzle.
 */
public final class ContextBlocks {

    private ContextBlocks() {
    }

    /**
     * Drops blank lines around a context block and trailing whitespace, but keeps the leading indentation of the first
     * line. A missing or blank block becomes {@code null}, so "no context" is stored the way the generated path stores
     * it.
     *
     * @param context the context block as typed by the teacher (may be {@code null})
     * @return the normalised block, or {@code null} when there is none
     */
    public static String normalize(final String context) {
        if (context == null || context.isBlank()) {
            return null;
        }
        return context.replaceFirst("^(?:[ \\t]*\\R)+", "").stripTrailing();
    }
}
