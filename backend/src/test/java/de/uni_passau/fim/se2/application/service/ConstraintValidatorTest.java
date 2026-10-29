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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import de.uni_passau.fim.se2.domain.entities.GenerationConstraints;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;

/**
 * AP20: the blacklist and whitelist pickers suggest concept-catalogue slugs, so the validator has to understand them,
 * not only its own construct keys.
 */
class ConstraintValidatorTest {

    private static final String SUM_LOOP = """
        int sum(int[] xs) {
            int s = 0;
            for (int i = 0; i < xs.length; i++) {
                s += xs[i];
            }
            return s;
        }""";

    private final ConstraintValidator validator = new ConstraintValidator();

    private List<String> validate(String code, List<String> blacklist, List<String> whitelist) {
        TieredTaskSequenceDTO sequence = TieredTaskSequenceDTO.builder()
            .tasks(List.of(TaskDTO.builder().tierLevel(1).solution(code).build()))
            .build();
        return validator.validate(
            sequence, GenerationConstraints.builder().blacklist(blacklist).whitelist(whitelist).build()
        );
    }

    @Test
    void blacklistedCatalogueSlugIsDetectedThroughItsAlias() {
        List<String> violations = validate("xs.forEach(x -> total(x));", List.of("lambda-expression"), null);

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("lambda-expression"));
    }

    @Test
    void whitelistOfCatalogueSlugsCoversTheirConstructs() {
        // for-loop must also allow the looser "for" key, array covers int[]
        assertEquals(List.of(), validate(SUM_LOOP, null, List.of("for-loop", "array")));
    }

    @Test
    void whitelistAliasCoversTheConstruct() {
        String code = "switch (day) { case 1: return 7; default: return 0; }";

        assertEquals(List.of(), validate(code, null, List.of("switch-case")));
    }

    @Test
    void constructOutsideTheWhitelistIsStillReported() {
        List<String> violations = validate(SUM_LOOP, null, List.of("for-loop"));

        assertEquals(1, violations.size());
        assertTrue(violations.get(0).contains("'array'"));
    }
}
