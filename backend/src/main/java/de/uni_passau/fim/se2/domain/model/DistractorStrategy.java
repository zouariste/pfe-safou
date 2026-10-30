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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Which kinds of distractor suit which tier (AP15).
 *
 * <p>
 * The single source for the distractor taxonomy and the tier mapping. The automatic creator's identity files
 * ({@code PromptStuff/Identity/Sequence}, {@code Sequence5Tier}) carry placeholders that are filled from
 * {@link #taxonomyText()} and {@link #tierMappingText(int)}, and the sequence prompt picks a {@code distractorPlan} per
 * task from them. Every other path that generates distractors — the manual creator, the on-demand buttons on the
 * sequence page, tiers added later — has no such plan, so the distractor call picks one itself from
 * {@link #preferredTypes}.
 */
public final class DistractorStrategy {

    /** The distractor taxonomy, with the description the prompts use. */
    public enum Type {

        LOGIC_FLIP("Invert operators (<→>, ==→!=, &&→||). Tests predicate logic."),
        OFF_BY_ONE("Wrong loop bounds or range limits. Tests boundary understanding."),
        VARIABLE_MISMATCH("Use existing but wrong variable. Tests data flow comprehension."),
        BOUNDARY_IDENTITY("Wrong initial values or base cases. Tests recursive thinking."),
        STRUCTURAL_ILLOGICAL("Code illegal in context (break outside loop). Tests control flow rules."),
        REDUNDANT_ZOMBIE("Plausible but unnecessary/harmful extra lines. Tests ability to identify ballast.");

        private final String description;

        Type(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }

        /**
         * Whether a type name a model returned is one of ours.
         *
         * @param name the name to check, possibly null
         * @return true if it names a type exactly
         */
        public static boolean isKnown(String name) {
            return name != null && Arrays.stream(values()).anyMatch(t -> t.name().equals(name));
        }
    }

    /** How hard a tier's distractors should be to spot. */
    private enum Level {

        OBVIOUS("obvious errors", List.of(Type.LOGIC_FLIP, Type.STRUCTURAL_ILLOGICAL)),
        DETAIL("detail errors", List.of(Type.OFF_BY_ONE, Type.VARIABLE_MISMATCH)),
        SUBTLE("subtle traps", List.of(Type.BOUNDARY_IDENTITY, Type.REDUNDANT_ZOMBIE)),
        ANY(null, List.of(Type.values()));

        private final String label;
        private final List<Type> types;

        Level(String label, List<Type> types) {
            this.label = label;
            this.types = types;
        }
    }

    private DistractorStrategy() {
    }

    /**
     * The types preferred for one tier of a sequence.
     *
     * <p>
     * Up to three tiers follow the three-tier mapping (obvious, detail, subtle); longer sequences follow the five-tier
     * one, where the top tier may use any type. Tiers past the end of a mapping get its last entry, so a sequence an
     * owner has grown beyond its original length still gets an answer.
     *
     * @param tierLevel the task's tier, from 1
     * @param tierCount how many tiers the sequence has
     * @return the preferred types, never empty
     */
    public static List<Type> preferredTypes(int tierLevel, int tierCount) {
        return level(tierLevel, tierCount).types;
    }

    private static Level level(int tierLevel, int tierCount) {
        if (tierCount <= 3) {
            if (tierLevel <= 1) {
                return Level.OBVIOUS;
            }
            return tierLevel == 2 ? Level.DETAIL : Level.SUBTLE;
        }
        if (tierLevel <= 2) {
            return Level.OBVIOUS;
        }
        if (tierLevel == 3) {
            return Level.DETAIL;
        }
        return tierLevel == 4 ? Level.SUBTLE : Level.ANY;
    }

    /** @return the taxonomy as a prompt list, one {@code - TYPE: description} line per type */
    public static String taxonomyText() {
        return taxonomyText(List.of(Type.values()));
    }

    /**
     * @param types the types to list
     * @return the given types as a prompt list, one {@code - TYPE: description} line per type
     */
    public static String taxonomyText(List<Type> types) {
        return types.stream()
            .map(t -> "- " + t.name() + ": " + t.description())
            .collect(Collectors.joining("\n"));
    }

    /** @return the type names, comma-separated, for a prompt's "one of …" */
    public static String typeNames() {
        return Arrays.stream(Type.values()).map(Type::name).collect(Collectors.joining(", "));
    }

    /**
     * The tier mapping for a sequence of the given length, as prompt lines. Adjacent tiers with the same preference
     * share a line ({@code - Tier 1–2: prefer …}).
     *
     * @param tierCount how many tiers the sequence has
     * @return one line per run of tiers
     */
    public static String tierMappingText(int tierCount) {
        StringBuilder text = new StringBuilder();
        int from = 1;
        while (from <= tierCount) {
            Level level = level(from, tierCount);
            int to = from;
            while (to < tierCount && level(to + 1, tierCount) == level) {
                to++;
            }
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append("- Tier ").append(from == to ? String.valueOf(from) : from + "–" + to).append(": ");
            if (level == Level.ANY) {
                text.append("Any type — choose the most deceptive for the specific code structure");
            }
            else {
                text.append("prefer ")
                    .append(level.types.stream().map(Type::name).collect(Collectors.joining(" or ")))
                    .append(" (").append(level.label).append(')');
            }
            from = to + 1;
        }
        return text.toString();
    }
}
