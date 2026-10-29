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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.uni_passau.fim.se2.api.dto.TaskEditRequest;
import de.uni_passau.fim.se2.application.service.analysis.ChangeAnalysisService;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.interfaces.TaskOriginalRepository;
import de.uni_passau.fim.se2.domain.interfaces.TaskRepository;
import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceRepository;

/**
 * AP22: editing a task's context blocks, which the teacher can also move lines into and out of.
 */
class TaskEditServiceTest {

    private static final UUID TASK = UUID.randomUUID();

    private TaskRepository taskRepo;
    private TaskEditService service;

    @BeforeEach
    void setUp() {
        taskRepo = mock(TaskRepository.class);
        TaskOriginalRepository originalRepo = mock(TaskOriginalRepository.class);
        // An original exists, so no adjustment rating is needed.
        when(originalRepo.existsByTaskId(TASK)).thenReturn(true);
        doAnswer(invocation -> {
            TaskDTO target = invocation.getArgument(0);
            target.setContextBefore("import java.util.*;");
            target.setContextAfter("}");
            return null;
        }).when(taskRepo).loadById(any());

        service = new TaskEditService(
            taskRepo, originalRepo, mock(TieredTaskSequenceRepository.class), mock(OwnershipService.class),
            mock(ChangeAnalysisService.class), new ObjectMapper()
        );
    }

    private static TaskEditRequest request(String before, String after) {
        TaskEditRequest request = new TaskEditRequest();
        request.setProblemStatement("Sum two ints");
        request.setCode("return a + b;");
        request.setContextBefore(before);
        request.setContextAfter(after);
        return request;
    }

    @Test
    void storesNormalisedContext() {
        service.editTask(TASK, request("\npublic class Sum {\n    int sum(int a, int b) {  \n", "    }\n}\n"));

        verify(taskRepo).updateContext(TASK, "public class Sum {\n    int sum(int a, int b) {", "    }\n}");
    }

    @Test
    void blankContextClearsIt() {
        service.editTask(TASK, request("", " \n"));

        verify(taskRepo).updateContext(eq(TASK), isNull(), isNull());
    }

    @Test
    void leavesContextAloneWhenNotSent() {
        service.editTask(TASK, request(null, null));

        verify(taskRepo, never()).updateContext(any(), any(), any());
    }

    @Test
    void aMissingSideKeepsItsCurrentValue() {
        service.editTask(TASK, request("public class Sum {", null));

        verify(taskRepo).updateContext(TASK, "public class Sum {", "}");
    }
}
