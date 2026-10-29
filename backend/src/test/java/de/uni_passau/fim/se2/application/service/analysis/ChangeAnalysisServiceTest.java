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
package de.uni_passau.fim.se2.application.service.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TaskOriginalDTO;
import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer;
import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer.AstAnalysisResult;
import de.uni_passau.fim.se2.domain.interfaces.CodeAnalyzer.ComplexityResult;
import de.uni_passau.fim.se2.domain.interfaces.TaskChangeAnalysisRepository;
import de.uni_passau.fim.se2.domain.interfaces.TaskOriginalRepository;
import de.uni_passau.fim.se2.domain.interfaces.TaskRepository;

/**
 * The complexity delta of a teacher edit.
 *
 * <p>
 * It used to hold the edited task's own cyclomatic complexity, so an untouched task reported a "delta" of 4 and
 * research exports could not tell edits that add branching from edits that do not.
 */
class ChangeAnalysisServiceTest {

    private static final UUID TASK = UUID.randomUUID();
    private static final String LANG = "java";
    private static final String ORIGINAL_CODE = "int x = 1;";
    private static final String WITH_BRANCH = "int x = 1;\nif (x > 0) { x--; }";

    private CodeAnalyzer codeAnalyzer;
    private TaskOriginalRepository originalRepo;
    private TaskRepository taskRepo;
    private ChangeAnalysisService service;

    @BeforeEach
    void setUp() {
        codeAnalyzer = mock(CodeAnalyzer.class);
        originalRepo = mock(TaskOriginalRepository.class);
        taskRepo = mock(TaskRepository.class);
        service = new ChangeAnalysisService(
            new SyntacticAnalyzer(), codeAnalyzer, new ChangeScoreCalculator(), originalRepo, taskRepo,
            mock(TaskChangeAnalysisRepository.class), new ObjectMapper()
        );

        when(codeAnalyzer.analyzeChanges(anyString(), anyString(), any()))
            .thenReturn(new AstAnalysisResult(0, 0, 0, 0.0, List.of(), Map.of()));
        when(codeAnalyzer.computeComplexity(eq(ORIGINAL_CODE), eq(LANG))).thenReturn(complexity(1, 1));
        when(codeAnalyzer.computeComplexity(eq(WITH_BRANCH), eq(LANG))).thenReturn(complexity(2, 2));
        when(originalRepo.findByTaskId(TASK)).thenReturn(
            Optional.of(
                TaskOriginalDTO.builder().id(UUID.randomUUID()).taskId(TASK)
                    .originalProblemStatement("Statement").originalCode(ORIGINAL_CODE).adjustmentRating(3).build()
            )
        );
    }

    @Test
    void addingABranchYieldsADeltaOfOne() {
        currentTask(WITH_BRANCH, 7);

        assertEquals(1, service.analyzeTask(TASK).getCodeComplexityDelta());
    }

    @Test
    void unchangedCodeYieldsZeroRegardlessOfTheStoredMetric() {
        currentTask(ORIGINAL_CODE, 4);

        assertEquals(0, service.analyzeTask(TASK).getCodeComplexityDelta());
    }

    @Test
    void codeTheAnalyzerCannotParseYieldsNoDelta() {
        when(codeAnalyzer.computeComplexity(eq(WITH_BRANCH), eq(LANG))).thenReturn(ComplexityResult.zero());
        currentTask(WITH_BRANCH, 2);

        assertNull(service.analyzeTask(TASK).getCodeComplexityDelta());
    }

    /** Makes {@code loadById} fill in the edited task, including a stored complexity the delta must not reuse. */
    private void currentTask(String solution, int storedComplexity) {
        doAnswer(inv -> {
            TaskDTO target = inv.getArgument(0);
            target.setStatement("Statement");
            target.setSolution(solution);
            target.setProgrammingLanguage(LANG);
            target.setCyclomaticComplexity(storedComplexity);
            return null;
        }).when(taskRepo).loadById(any(TaskDTO.class));
    }

    private static ComplexityResult complexity(int linesOfCode, int cyclomaticComplexity) {
        return new ComplexityResult(linesOfCode, cyclomaticComplexity, 0, 0, 0);
    }
}
