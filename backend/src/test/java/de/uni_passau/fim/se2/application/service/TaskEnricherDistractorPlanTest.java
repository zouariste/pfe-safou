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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.node.TextNode;

import de.uni_passau.fim.se2.domain.entities.SolveContract;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.interfaces.AiModelClient;
import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer;
import de.uni_passau.fim.se2.domain.model.GeneratedDistractors;

/**
 * A task without a distractor plan gets the one the distractor call chose, and that call learns how many tiers the
 * sequence has, so it can pick from the right tier mapping.
 */
@DisplayName("TaskEnricher and distractor plans")
class TaskEnricherDistractorPlanTest {

    private static final String SOLUTION = "int x = 1;";
    private static final String CHOSEN = "{\"type\":\"LOGIC_FLIP\",\"rationale\":\"simple comparison\"}";

    @Test
    @DisplayName("a generated task the sequence prompt did not plan keeps the plan the distractor call chose")
    void unplannedTaskGetsChosenPlan() throws Exception {
        AiModelClient client = stubbedClient();
        when(client.generateDistractors(any(), any(), any(), anyInt(), anyInt(), isNull(), anyInt()))
            .thenReturn(new GeneratedDistractors(List.of("int x = 2; #distractor"), CHOSEN));

        TieredTaskSequenceDTO enriched = enricher(client).enrichWithSolutions(
            TieredTaskSequenceDTO.builder()
                .programmingLanguage("Java")
                .tasks(List.of(task(1), task(2), task(3), task(4), task(5)))
                .build(),
            true, false, false
        );

        TaskDTO first = enriched.getTasks().get(0);
        assertEquals(CHOSEN, first.getDistractorPlan());
        assertEquals("int x = 2; #distractor", first.getDistractors());
        // Five tasks: the five-tier mapping applies.
        verify(client).generateDistractors(eq(SOLUTION), eq("Java"), anyString(), eq(1), eq(5), isNull(), anyInt());
    }

    @Test
    @DisplayName("on-demand generation returns the chosen plan alongside the validated lines")
    void onDemandReturnsPlan() throws Exception {
        AiModelClient client = stubbedClient();
        when(client.generateDistractors(any(), any(), any(), anyInt(), anyInt(), isNull(), anyInt()))
            .thenReturn(new GeneratedDistractors(List.of("int x = 2;", SOLUTION), CHOSEN));

        GeneratedDistractors result = enricher(client)
            .generateDistractorsForTask(SOLUTION, "Java", "Set x", 1, 3, null, 1);

        // The line identical to the solution is filtered; the suffix is added.
        assertEquals(List.of("int x = 2; #distractor"), result.lines());
        assertEquals(CHOSEN, result.plan());
    }

    @Test
    @DisplayName("a distractor spanning several lines becomes one marked line each, so none joins the solution")
    void multiLineDistractorIsSplit() throws Exception {
        AiModelClient client = stubbedClient();
        when(client.generateDistractors(any(), any(), any(), anyInt(), anyInt(), isNull(), anyInt()))
            .thenReturn(
                new GeneratedDistractors(
                    List.of("if (x > 0) {\n    x = 0; #distractor", "int y = 0;\r\nint x = 1; #distractor"), CHOSEN
                )
            );

        GeneratedDistractors result = enricher(client)
            .generateDistractorsForTask(SOLUTION, "Java", "Set x", 2, 3, null, 10);

        // The line that is part of the solution is dropped, as a single-line one would be.
        assertEquals(
            List.of("if (x > 0) { #distractor", "x = 0; #distractor", "int y = 0; #distractor"), result.lines()
        );
        assertEquals(3, result.text().split("\n").length);
    }

    @Test
    @DisplayName("a failed call yields nothing rather than throwing")
    void failureYieldsNone() throws Exception {
        AiModelClient client = stubbedClient();
        when(client.generateDistractors(any(), any(), any(), anyInt(), anyInt(), any(), anyInt()))
            .thenThrow(new java.io.IOException("boom"));

        GeneratedDistractors result = enricher(client)
            .generateDistractorsForTask(SOLUTION, "Java", "Set x", 1, 3, null, 1);

        assertEquals(GeneratedDistractors.NONE, result);
    }

    private static AiModelClient stubbedClient() throws Exception {
        AiModelClient client = mock(AiModelClient.class);
        when(client.solveProblem(any())).thenReturn(TextNode.valueOf(SOLUTION));
        return client;
    }

    private static TaskEnricher enricher(AiModelClient client) {
        CodeSanitizerService sanitizer = mock(CodeSanitizerService.class);
        when(sanitizer.removeComments(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(sanitizer.removeScaffolding(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        InterchangeableAnnotator annotator = mock(InterchangeableAnnotator.class);
        when(annotator.annotate(any(), any())).thenAnswer(inv -> inv.getArgument(0));

        CodeAnalyzer analyzer = mock(CodeAnalyzer.class);
        when(analyzer.computeComplexity(any(), any()))
            .thenReturn(new CodeAnalyzer.ComplexityResult(1, 1, 1, 10, 1));

        return new TaskEnricher(client, annotator, mock(ContextAnnotator.class), sanitizer, analyzer);
    }

    private static TaskDTO task(int tier) {
        return TaskDTO.builder()
            .statement("Set x, tier " + tier)
            .contract(
                SolveContract.builder()
                    .language("Java")
                    .taskStatement("Set x")
                    .functionSignature("void solve()")
                    .purposeBehavior("Sets x")
                    .prePostConditions("None")
                    .example(new SolveContract.ExamplePair("", ""))
                    .build()
            )
            .tierLevel(tier)
            .build();
    }
}
