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
package de.uni_passau.fim.se2.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import de.uni_passau.fim.se2.application.service.OwnershipService;
import de.uni_passau.fim.se2.application.service.WorksheetAccessService;
import de.uni_passau.fim.se2.application.service.WorksheetService;
import de.uni_passau.fim.se2.domain.entities.UserDTO.UserRole;
import de.uni_passau.fim.se2.domain.entities.WorksheetDTO;
import de.uni_passau.fim.se2.domain.model.WorksheetVisibility;

@DisplayName("opening a private worksheet through a class assignment")
class WorksheetControllerAccessTest {

    private static final UUID SHEET = UUID.randomUUID();
    private static final UUID OWNER = UUID.randomUUID();
    private static final UUID VIEWER = UUID.randomUUID();

    private WorksheetService worksheetService;
    private OwnershipService ownershipService;
    private WorksheetAccessService accessService;
    private WorksheetController controller;

    @BeforeEach
    void setUp() {
        worksheetService = mock(WorksheetService.class);
        ownershipService = mock(OwnershipService.class);
        accessService = mock(WorksheetAccessService.class);
        controller = new WorksheetController(worksheetService, ownershipService, null, null, null, accessService);

        when(worksheetService.findWorksheetById(SHEET)).thenReturn(
            Optional.of(
                WorksheetDTO.builder().id(SHEET).ownerId(OWNER).visibility(WorksheetVisibility.PRIVATE).build()
            )
        );
        when(ownershipService.getCurrentUserId()).thenReturn(VIEWER);
        when(ownershipService.getCurrentUserRole()).thenReturn(UserRole.STUDENT);
    }

    @Test
    @DisplayName("a student it was released to can open it")
    void releasedStudentOpensIt() {
        when(accessService.isReleasedToCurrentStudent(SHEET)).thenReturn(true);

        ResponseEntity<WorksheetDTO> response = controller.getWorksheetById(SHEET);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("the teacher of a class it is assigned to can open it")
    void classTeacherOpensIt() {
        when(ownershipService.getCurrentUserRole()).thenReturn(UserRole.TEACHER);
        when(accessService.isAssignedToCurrentTeacher(SHEET)).thenReturn(true);

        ResponseEntity<WorksheetDTO> response = controller.getWorksheetById(SHEET);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @DisplayName("anyone else gets the usual 404")
    void othersGetNotFound() {
        when(accessService.isReleasedToCurrentStudent(SHEET)).thenReturn(false);

        ResponseEntity<WorksheetDTO> response = controller.getWorksheetById(SHEET);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    @DisplayName("being assigned does not let a student copy it")
    void assignedStudentCannotCopy() {
        when(accessService.isReleasedToCurrentStudent(SHEET)).thenReturn(true);
        doThrow(new AccessDeniedException("denied")).when(ownershipService).denyStudents(any());

        assertThrows(AccessDeniedException.class, () -> controller.copyWorksheet(SHEET));
        verify(worksheetService, never()).copyWorksheet(any(), any());
    }

    @Test
    @DisplayName("being assigned does not let a teacher copy it")
    void teacherCannotCopyThroughAnAssignment() {
        when(ownershipService.getCurrentUserRole()).thenReturn(UserRole.TEACHER);
        when(accessService.isAssignedToCurrentTeacher(SHEET)).thenReturn(true);

        ResponseEntity<WorksheetDTO> response = controller.copyWorksheet(SHEET);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(worksheetService, never()).copyWorksheet(any(), any());
    }
}
