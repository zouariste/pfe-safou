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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import de.uni_passau.fim.se2.api.dto.TierCreateRequest;
import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TaskOriginalDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.TaskOriginalRepository;
import de.uni_passau.fim.se2.domain.interfaces.TaskRepository;
import de.uni_passau.fim.se2.domain.interfaces.TieredTaskSequenceRepository;

/**
 * Adding and deleting tiers.
 *
 * <p>
 * What can go wrong here is mostly arithmetic and order: tiers that end up sharing a level or leaving a hole, a tier
 * count that no longer matches, and writes that happen before a refusal.
 */
class SequenceTierServiceTest {

    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID SEQUENCE = UUID.randomUUID();
    private static final UUID NEW_TASK = UUID.randomUUID();

    private TieredTaskSequenceRepository sequenceRepo;
    private TaskRepository taskRepo;
    private TaskOriginalRepository originalRepo;
    private OwnershipService ownershipService;
    private ManualTaskService manualTaskService;
    private SequenceTierService service;

    /** The sequence's current tiers, as {@code loadTasksBySequenceId} returns them. */
    private List<TaskDTO> tiers;

    @BeforeEach
    void setUp() {
        sequenceRepo = mock(TieredTaskSequenceRepository.class);
        taskRepo = mock(TaskRepository.class);
        originalRepo = mock(TaskOriginalRepository.class);
        ownershipService = mock(OwnershipService.class);
        manualTaskService = mock(ManualTaskService.class);
        service = new SequenceTierService(sequenceRepo, taskRepo, originalRepo, ownershipService, manualTaskService);

        // The real repositories fill the DTO they are handed rather than returning one.
        doAnswer(invocation -> {
            TieredTaskSequenceDTO target = invocation.getArgument(0);
            target.setOwnerId(OWNER);
            target.setProgrammingLanguage("java");
            return null;
        }).when(sequenceRepo).loadById(any());

        tiers = new ArrayList<>(List.of(tier(1), tier(2), tier(3)));
        when(taskRepo.loadTasksBySequenceId(any())).thenAnswer(invocation -> tiers);

        when(manualTaskService.buildTask(anyString(), anyString(), anyInt(), any(), anyString(), any(), any(), any()))
            .thenAnswer(
                invocation -> TaskDTO.builder()
                    .statement(invocation.getArgument(0))
                    .solution(invocation.getArgument(1))
                    .tierLevel(invocation.getArgument(2))
                    .sequenceId(invocation.getArgument(5))
                    .build()
            );
        // save assigns the id, as PostgreTaskDao does.
        doAnswer(invocation -> {
            ((TaskDTO) invocation.getArgument(0)).setId(NEW_TASK);
            return null;
        }).when(taskRepo).save(any());
    }

    private static TaskDTO tier(int level) {
        return TaskDTO.builder().id(UUID.randomUUID()).sequenceId(SEQUENCE).tierLevel(level).build();
    }

    private static TierCreateRequest request(Integer level) {
        TierCreateRequest request = new TierCreateRequest();
        request.setProblemStatement("Sum an array");
        request.setCode("int s = 0;");
        request.setTierLevel(level);
        return request;
    }

    @Nested
    @DisplayName("adding a tier")
    class Add {

        @Test
        @DisplayName("in the middle moves the tiers at and above it up, then saves it at that level")
        void insertsInTheMiddle() {
            service.addTier(SEQUENCE, request(2));

            ArgumentCaptor<TaskDTO> saved = ArgumentCaptor.forClass(TaskDTO.class);
            InOrder order = inOrder(taskRepo);
            order.verify(taskRepo).shiftTierLevels(SEQUENCE, 2, +1);
            order.verify(taskRepo).save(saved.capture());
            assertAll(
                () -> assertEquals(2, saved.getValue().getTierLevel()),
                () -> verify(sequenceRepo).updateTierCount(SEQUENCE, 4)
            );
        }

        @Test
        @DisplayName("without a level goes at the end")
        void appendsByDefault() {
            service.addTier(SEQUENCE, request(null));

            verify(taskRepo).shiftTierLevels(SEQUENCE, 4, +1);
            verify(manualTaskService)
                .buildTask(anyString(), anyString(), eq(4), any(), eq("java"), eq(SEQUENCE), any(), any());
        }

        @Test
        @DisplayName("passes the context blocks on to the task builder")
        void passesContext() {
            TierCreateRequest request = request(null);
            request.setContextBefore("public class Sum {");
            request.setContextAfter("}");

            service.addTier(SEQUENCE, request);

            verify(manualTaskService).buildTask(
                anyString(), anyString(), anyInt(), any(), anyString(), any(),
                eq("public class Sum {"), eq("}")
            );
        }

        @Test
        @DisplayName("stores the teacher's text as the original, with no rating")
        void savesUnratedOriginal() {
            service.addTier(SEQUENCE, request(1));

            ArgumentCaptor<TaskOriginalDTO> original = ArgumentCaptor.forClass(TaskOriginalDTO.class);
            verify(originalRepo).save(original.capture());
            assertAll(
                () -> assertEquals(NEW_TASK, original.getValue().getTaskId()),
                () -> assertEquals("Sum an array", original.getValue().getOriginalProblemStatement()),
                () -> assertNull(
                    original.getValue().getAdjustmentRating(),
                    "nobody generated this tier, so there is nothing to rate"
                )
            );
        }

        @Test
        @DisplayName("is refused once the sequence has the maximum number of tiers")
        void refusesBeyondMaximum() {
            tiers.add(tier(4));
            tiers.add(tier(5));

            assertThrows(DomainValidationException.class, () -> service.addTier(SEQUENCE, request(null)));
            verify(taskRepo, never()).save(any());
        }

        @Test
        @DisplayName("is refused at a level that would leave a hole or is below 1")
        void refusesOutOfRangeLevel() {
            assertAll(
                () -> assertThrows(DomainValidationException.class, () -> service.addTier(SEQUENCE, request(5))),
                () -> assertThrows(DomainValidationException.class, () -> service.addTier(SEQUENCE, request(0)))
            );
            verify(taskRepo, never()).shiftTierLevels(any(), anyInt(), anyInt());
        }

        @Test
        @DisplayName("is refused for someone who does not own the sequence, before anything is written")
        void refusesNonOwner() {
            doThrow(new AccessDeniedException("not yours"))
                .when(ownershipService).checkOwnership(OWNER, "sequence");

            assertThrows(AccessDeniedException.class, () -> service.addTier(SEQUENCE, request(1)));
            verify(taskRepo, never()).shiftTierLevels(any(), anyInt(), anyInt());
            verify(taskRepo, never()).save(any());
        }

        @Test
        @DisplayName("is refused without a statement")
        void refusesBlankStatement() {
            TierCreateRequest blank = request(1);
            blank.setProblemStatement("  ");

            assertThrows(DomainValidationException.class, () -> service.addTier(SEQUENCE, blank));
            verifyNoInteractions(taskRepo);
        }
    }

    @Nested
    @DisplayName("deleting a tier")
    class Delete {

        private void stubLoad(TaskDTO task) {
            doAnswer(invocation -> {
                TaskDTO target = invocation.getArgument(0);
                target.setSequenceId(task.getSequenceId());
                target.setTierLevel(task.getTierLevel());
                return null;
            }).when(taskRepo).loadById(any());
        }

        @Test
        @DisplayName("soft-deletes it and moves the tiers above it down")
        void softDeletesAndCloses() {
            TaskDTO second = tiers.get(1);
            stubLoad(second);

            service.deleteTier(second.getId());

            InOrder order = inOrder(taskRepo);
            order.verify(taskRepo).softDelete(second.getId());
            order.verify(taskRepo).shiftTierLevels(SEQUENCE, 3, -1);
            verify(sequenceRepo).updateTierCount(SEQUENCE, 2);
        }

        @Test
        @DisplayName("is refused for the last tier left")
        void refusesLastTier() {
            TaskDTO only = tier(1);
            tiers = new ArrayList<>(List.of(only));
            stubLoad(only);

            assertThrows(DomainValidationException.class, () -> service.deleteTier(only.getId()));
            verify(taskRepo, never()).softDelete(any());
        }

        @Test
        @DisplayName("is refused for a tier that is already deleted")
        void refusesAlreadyDeleted() {
            // loadById still finds it; the sequence's current tiers do not.
            TaskDTO deleted = tier(2);
            stubLoad(deleted);

            assertThrows(DomainValidationException.class, () -> service.deleteTier(deleted.getId()));
            verify(taskRepo, never()).softDelete(any());
        }

        @Test
        @DisplayName("is refused for someone who does not own the sequence")
        void refusesNonOwner() {
            TaskDTO second = tiers.get(1);
            stubLoad(second);
            doThrow(new AccessDeniedException("not yours"))
                .when(ownershipService).checkOwnership(OWNER, "sequence");

            assertThrows(AccessDeniedException.class, () -> service.deleteTier(second.getId()));
            verify(taskRepo, never()).softDelete(any());
        }
    }
}
