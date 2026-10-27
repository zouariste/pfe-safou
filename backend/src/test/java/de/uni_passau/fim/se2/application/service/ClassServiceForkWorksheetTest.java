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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import de.uni_passau.fim.se2.domain.entities.ClassDTO;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.ClassRepository;
import de.uni_passau.fim.se2.domain.interfaces.UserRepository;

/**
 * A teacher taking their own copy of someone else's worksheet that their class uses.
 *
 * <p>
 * The copy itself is {@link WorksheetCloneService}'s business. What only this service can get wrong is who may ask,
 * which assignment moves, and that nothing is copied when the request is refused.
 */
class ClassServiceForkWorksheetTest {

    private static final UUID TEACHER = UUID.randomUUID();
    private static final UUID AUTHOR = UUID.randomUUID();
    private static final UUID CLASS_ID = UUID.randomUUID();
    private static final UUID SOURCE = UUID.randomUUID();
    private static final UUID COPY = UUID.randomUUID();

    private ClassRepository classRepository;
    private OwnershipService ownershipService;
    private WorksheetService worksheetService;
    private WorksheetCloneService cloneService;
    private ClassService service;

    @BeforeEach
    void setUp() {
        classRepository = mock(ClassRepository.class);
        ownershipService = mock(OwnershipService.class);
        worksheetService = mock(WorksheetService.class);
        cloneService = mock(WorksheetCloneService.class);
        service = new ClassService(
            classRepository,
            mock(UserRepository.class),
            ownershipService,
            mock(TokenService.class),
            mock(FeedbackLogService.class),
            mock(TempPasswordGenerator.class),
            mock(PasswordHasherService.class),
            worksheetService,
            cloneService,
            mock(TaskPersistenceService.class),
            mock(TaskSubmissionService.class)
        );

        when(ownershipService.getCurrentUserId()).thenReturn(TEACHER);
        when(classRepository.findByIdWithDetails(CLASS_ID)).thenReturn(Optional.of(classUsing(SOURCE)));
        when(worksheetService.loadWorksheetById(SOURCE))
            .thenReturn(WorksheetDTO.builder().id(SOURCE).ownerId(AUTHOR).build());
        when(cloneService.copyShallow(SOURCE, TEACHER))
            .thenReturn(WorksheetDTO.builder().id(COPY).ownerId(TEACHER).build());
    }

    private static ClassDTO classUsing(UUID worksheetId) {
        return ClassDTO.builder()
            .id(CLASS_ID)
            .ownerId(TEACHER)
            .worksheets(List.of(ClassDTO.WorksheetInfo.builder().worksheetId(worksheetId).build()))
            .build();
    }

    @Test
    @DisplayName("the class moves onto a copy owned by the teacher")
    void movesClassOntoCopy() {
        WorksheetDTO copy = service.forkAssignedWorksheet(CLASS_ID, SOURCE);

        assertAll(
            () -> assertEquals(COPY, copy.getId()),
            () -> verify(cloneService).copyShallow(SOURCE, TEACHER),
            () -> verify(classRepository).replaceWorksheet(CLASS_ID, SOURCE, COPY)
        );
    }

    @Test
    @DisplayName("someone else's class is refused before anything is copied")
    void refusesForeignClass() {
        doThrow(new AccessDeniedException("not yours"))
            .when(ownershipService).checkOwnership(TEACHER, "class");

        assertThrows(
            AccessDeniedException.class,
            () -> service.forkAssignedWorksheet(CLASS_ID, SOURCE)
        );
        verify(cloneService, never()).copyShallow(any(), any());
    }

    @Test
    @DisplayName("a worksheet the class does not use is refused, so the copy cannot be a way in")
    void refusesUnassignedWorksheet() {
        UUID elsewhere = UUID.randomUUID();

        assertThrows(
            DomainValidationException.class,
            () -> service.forkAssignedWorksheet(CLASS_ID, elsewhere)
        );
        verify(cloneService, never()).copyShallow(any(), any());
    }

    @Test
    @DisplayName("the teacher's own worksheet is refused: it is already editable")
    void refusesOwnWorksheet() {
        when(worksheetService.loadWorksheetById(SOURCE))
            .thenReturn(WorksheetDTO.builder().id(SOURCE).ownerId(TEACHER).build());

        assertThrows(
            DomainValidationException.class,
            () -> service.forkAssignedWorksheet(CLASS_ID, SOURCE)
        );
        verify(cloneService, never()).copyShallow(any(), any());
        verify(classRepository, never()).replaceWorksheet(any(), any(), any());
    }
}
