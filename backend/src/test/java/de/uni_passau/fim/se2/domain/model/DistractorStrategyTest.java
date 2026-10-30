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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.uni_passau.fim.se2.domain.model.DistractorStrategy.Type;

/**
 * The tier mapping has to match what the sequence identity files tell the automatic creator, since every other path
 * that creates distractors picks its strategy from it.
 */
class DistractorStrategyTest {

    private static final List<Type> OBVIOUS = List.of(Type.LOGIC_FLIP, Type.STRUCTURAL_ILLOGICAL);
    private static final List<Type> DETAIL = List.of(Type.OFF_BY_ONE, Type.VARIABLE_MISMATCH);
    private static final List<Type> SUBTLE = List.of(Type.BOUNDARY_IDENTITY, Type.REDUNDANT_ZOMBIE);

    @Test
    @DisplayName("three tiers go obvious, detail, subtle (identity file Sequence)")
    void threeTiers() {
        assertEquals(OBVIOUS, DistractorStrategy.preferredTypes(1, 3));
        assertEquals(DETAIL, DistractorStrategy.preferredTypes(2, 3));
        assertEquals(SUBTLE, DistractorStrategy.preferredTypes(3, 3));
    }

    @Test
    @DisplayName("five tiers go obvious, obvious, detail, subtle, any (identity file Sequence5Tier)")
    void fiveTiers() {
        assertEquals(OBVIOUS, DistractorStrategy.preferredTypes(1, 5));
        assertEquals(OBVIOUS, DistractorStrategy.preferredTypes(2, 5));
        assertEquals(DETAIL, DistractorStrategy.preferredTypes(3, 5));
        assertEquals(SUBTLE, DistractorStrategy.preferredTypes(4, 5));
        assertEquals(List.of(Type.values()), DistractorStrategy.preferredTypes(5, 5));
    }

    @Test
    @DisplayName("short manual sequences use the three-tier mapping")
    void shortSequences() {
        assertEquals(OBVIOUS, DistractorStrategy.preferredTypes(1, 1));
        assertEquals(DETAIL, DistractorStrategy.preferredTypes(2, 2));
    }

    @Test
    @DisplayName("tiers outside the mapping still get an answer")
    void outOfRange() {
        assertEquals(OBVIOUS, DistractorStrategy.preferredTypes(0, 3));
        assertEquals(SUBTLE, DistractorStrategy.preferredTypes(4, 3));
        assertEquals(SUBTLE, DistractorStrategy.preferredTypes(4, 4));
        assertEquals(List.of(Type.values()), DistractorStrategy.preferredTypes(7, 7));
    }

    @Test
    @DisplayName("the three-tier mapping renders as the Sequence identity used to spell it out")
    void threeTierText() {
        assertEquals(
            """
                - Tier 1: prefer LOGIC_FLIP or STRUCTURAL_ILLOGICAL (obvious errors)
                - Tier 2: prefer OFF_BY_ONE or VARIABLE_MISMATCH (detail errors)
                - Tier 3: prefer BOUNDARY_IDENTITY or REDUNDANT_ZOMBIE (subtle traps)""",
            DistractorStrategy.tierMappingText(3)
        );
    }

    @Test
    @DisplayName("the five-tier mapping renders as the Sequence5Tier identity used to spell it out")
    void fiveTierText() {
        assertEquals(
            """
                - Tier 1–2: prefer LOGIC_FLIP or STRUCTURAL_ILLOGICAL (obvious errors)
                - Tier 3: prefer OFF_BY_ONE or VARIABLE_MISMATCH (detail errors)
                - Tier 4: prefer BOUNDARY_IDENTITY or REDUNDANT_ZOMBIE (subtle traps)
                - Tier 5: Any type — choose the most deceptive for the specific code structure""",
            DistractorStrategy.tierMappingText(5)
        );
    }

    @Test
    @DisplayName("the taxonomy renders one line per type")
    void taxonomyText() {
        assertEquals(
            """
                - LOGIC_FLIP: Invert operators (<→>, ==→!=, &&→||). Tests predicate logic.
                - OFF_BY_ONE: Wrong loop bounds or range limits. Tests boundary understanding.
                - VARIABLE_MISMATCH: Use existing but wrong variable. Tests data flow comprehension.
                - BOUNDARY_IDENTITY: Wrong initial values or base cases. Tests recursive thinking.
                - STRUCTURAL_ILLOGICAL: Code illegal in context (break outside loop). Tests control flow rules.
                - REDUNDANT_ZOMBIE: Plausible but unnecessary/harmful extra lines. Tests ability to identify ballast.""",
            DistractorStrategy.taxonomyText()
        );
    }

    @Test
    @DisplayName("only exact taxonomy names are known types")
    void knownTypes() {
        assertTrue(Type.isKnown("OFF_BY_ONE"));
        assertFalse(Type.isKnown("off_by_one"));
        assertFalse(Type.isKnown("WRONG_SHAPE"));
        assertFalse(Type.isKnown(null));
    }
}
