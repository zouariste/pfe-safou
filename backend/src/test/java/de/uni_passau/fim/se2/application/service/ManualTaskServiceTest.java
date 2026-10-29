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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.uni_passau.fim.se2.api.dto.ManualTaskRequest;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer;
import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer.ComplexityResult;

/**
 * AP22: context mode in manual creation — the read-only blocks around the puzzle.
 */
class ManualTaskServiceTest {

    private static final String SOLUTION = "int s = a + b;\nreturn s;";

    private CodeAnalyzer codeAnalyzer;
    private ManualTaskService service;

    @BeforeEach
    void setUp() {
        codeAnalyzer = mock(CodeAnalyzer.class);
        when(codeAnalyzer.computeComplexity(anyString(), anyString())).thenReturn(ComplexityResult.zero());
        service = new ManualTaskService(mock(TaskPersistenceService.class), codeAnalyzer, new ObjectMapper());
    }

    private TaskDTO build(String before, String after) {
        return service.buildTask("Sum two ints", SOLUTION, 1, null, "java", UUID.randomUUID(), before, after);
    }

    @Test
    void carriesContextOntoTheTask() {
        TaskDTO task = build("public class Sum {\n    static int sum(int a, int b) {", "    }\n}");

        assertAll(
            () -> assertEquals("public class Sum {\n    static int sum(int a, int b) {", task.getContextBefore()),
            () -> assertEquals("    }\n}", task.getContextAfter()),
            () -> assertEquals(SOLUTION, task.getSolution())
        );
    }

    @Test
    void blankOrMissingContextIsStoredAsNull() {
        TaskDTO blank = build("  \n\t\n", "");
        TaskDTO missing = build(null, null);

        assertAll(
            () -> assertNull(blank.getContextBefore()),
            () -> assertNull(blank.getContextAfter()),
            () -> assertNull(missing.getContextBefore()),
            () -> assertNull(missing.getContextAfter())
        );
    }

    @Test
    void dropsSurroundingBlankLinesButKeepsIndentation() {
        TaskDTO task = build("\n  \n    }\n}\n\n", null);

        assertEquals("    }\n}", task.getContextBefore());
    }

    @Test
    void metricsAreComputedOnThePuzzleOnly() {
        build("public class Sum {", "}");

        verify(codeAnalyzer).computeComplexity(eq(SOLUTION), eq("java"));
    }

    @Test
    void createManualSequencePassesContextPerTier() {
        ManualTaskRequest.ManualTierEntry tier = new ManualTaskRequest.ManualTierEntry(
            1, "Sum two ints", SOLUTION, null, "public class Sum {", "}"
        );
        ManualTaskRequest request = new ManualTaskRequest("java", List.of("arithmetic"), List.of(tier));

        TieredTaskSequenceDTO sequence = service.createManualSequence(request, UUID.randomUUID());

        TaskDTO task = sequence.getTasks().get(0);
        assertAll(
            () -> assertEquals("public class Sum {", task.getContextBefore()),
            () -> assertEquals("}", task.getContextAfter())
        );
    }
}
