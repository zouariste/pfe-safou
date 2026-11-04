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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import de.uni_passau.fim.se2.testsupport.TestDatabase;

/**
 * Which students a class assignment lets through to an otherwise private worksheet.
 *
 * <p>
 * Against a real database because the answer depends on the join across classes, students and assignments, which a mock
 * would only repeat back.
 */
@DisplayName("a private worksheet assigned to a class")
class WorksheetAccessServiceDatabaseTest {

    private static final Duration DAY = Duration.ofDays(1);

    private static JdbcTemplate jdbc;
    private static UUID teacher;
    private static final List<UUID> STUDENTS = new ArrayList<>();
    private static final List<UUID> CLASSES = new ArrayList<>();
    private static final List<UUID> WORKSHEETS = new ArrayList<>();

    private OwnershipService ownershipService;
    private WorksheetAccessService service;
    private UUID worksheet;

    @BeforeAll
    static void wire() {
        Assumptions.assumeTrue(
            TestDatabase.isAvailable(),
            "no test database: set SPRING_DATASOURCE_URL or start Docker"
        );
        jdbc = TestDatabase.jdbc();
        teacher = user("TEACHER");
    }

    @AfterAll
    static void cleanup() {
        if (jdbc == null) {
            return; // wire() aborted: there was no test database
        }
        CLASSES.forEach(id -> jdbc.update("DELETE FROM classes WHERE id = ?", id));
        WORKSHEETS.forEach(id -> jdbc.update("DELETE FROM worksheets WHERE id = ?", id));
        STUDENTS.forEach(id -> jdbc.update("DELETE FROM users WHERE user_id = ?", id));
        jdbc.update("DELETE FROM users WHERE user_id = ?", teacher);
    }

    @BeforeEach
    void setUp() {
        ownershipService = mock(OwnershipService.class);
        service = new WorksheetAccessService(jdbc, ownershipService);

        worksheet = UUID.randomUUID();
        WORKSHEETS.add(worksheet);
        jdbc.update(
            "INSERT INTO worksheets (id, name, description, owner_id, visibility) VALUES (?, ?, ?, ?, ?)",
            worksheet, "Rekursion", "", teacher, "PRIVATE"
        );
    }

    @Test
    @DisplayName("opens for a student of the class once published")
    void publishedAssignment() {
        UUID student = student();
        assign(classOf(student), true, null, null);

        assertTrue(releasedTo(student));
    }

    @Test
    @DisplayName("stays closed to a student of the class until it is released")
    void unreleasedAssignment() {
        UUID student = student();
        assign(classOf(student), false, Instant.now().plus(DAY), null);

        assertFalse(releasedTo(student));
    }

    @Test
    @DisplayName("opens by itself when the release date passes")
    void releaseDatePassed() {
        UUID student = student();
        assign(classOf(student), false, Instant.now().minus(DAY), null);

        assertTrue(releasedTo(student));
    }

    @Test
    @DisplayName("stays readable after the deadline")
    void pastDeadline() {
        UUID student = student();
        assign(classOf(student), true, null, Instant.now().minus(DAY));

        assertTrue(releasedTo(student));
    }

    @Test
    @DisplayName("stays closed to a student of another class")
    void otherClass() {
        UUID student = student();
        classOf(student);
        assign(classOf(student()), true, null, null);

        assertFalse(releasedTo(student));
    }

    @Test
    @DisplayName("opens if any of the student's classes has released it")
    void severalClasses() {
        UUID student = student();
        assign(classOf(student), false, null, null);
        assign(classOf(student), true, null, null);

        assertTrue(releasedTo(student));
    }

    @Test
    @DisplayName("grants a teacher nothing")
    void teacherIsNotAStudent() {
        UUID classMember = student();
        assign(classOf(classMember), true, null, null);
        when(ownershipService.isStudent()).thenReturn(false);
        when(ownershipService.getCurrentUserId()).thenReturn(classMember);

        assertFalse(service.isReleasedToCurrentStudent(worksheet));
    }

    @Test
    @DisplayName("grants an anonymous visitor nothing")
    void anonymous() {
        assign(classOf(student()), true, null, null);

        assertFalse(service.isReleasedToCurrentStudent(worksheet));
    }

    @Test
    @DisplayName("opens for the teacher of the class, even before it is released")
    void classTeacher() {
        assign(classOf(student()), false, null, null);

        assertTrue(assignedToTeacher(teacher));
    }

    @Test
    @DisplayName("stays closed to a teacher of a class it is not assigned to")
    void otherTeacher() {
        UUID otherTeacher = user("TEACHER");
        STUDENTS.add(otherTeacher);
        jdbc.update(
            "INSERT INTO classes (id, name, owner_id) VALUES (?, ?, ?)",
            registeredClass(), "6a", otherTeacher
        );
        assign(classOf(student()), true, null, null);

        assertFalse(assignedToTeacher(otherTeacher));
    }

    @Test
    @DisplayName("grants a student of the class nothing through the teacher check")
    void studentIsNotATeacher() {
        assign(classOf(student()), true, null, null);
        when(ownershipService.isTeacher()).thenReturn(false);
        when(ownershipService.getCurrentUserId()).thenReturn(teacher);

        assertFalse(service.isAssignedToCurrentTeacher(worksheet));
    }

    private boolean assignedToTeacher(UUID user) {
        when(ownershipService.isTeacher()).thenReturn(true);
        when(ownershipService.getCurrentUserId()).thenReturn(user);
        return service.isAssignedToCurrentTeacher(worksheet);
    }

    private static UUID registeredClass() {
        UUID id = UUID.randomUUID();
        CLASSES.add(id);
        return id;
    }

    private boolean releasedTo(UUID student) {
        when(ownershipService.isStudent()).thenReturn(true);
        when(ownershipService.getCurrentUserId()).thenReturn(student);
        return service.isReleasedToCurrentStudent(worksheet);
    }

    private static UUID student() {
        UUID id = user("STUDENT");
        STUDENTS.add(id);
        return id;
    }

    private static UUID user(String role) {
        UUID id = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO users (user_id, email, first_name, last_name, password_hash,"
                + " role, is_verified, is_active) VALUES (?,?,?,?,?,CAST(? AS user_role),?,?)",
            id, id + "@example.invalid", "T", "T", "unused", role, true, true
        );
        return id;
    }

    private static UUID classOf(UUID student) {
        UUID id = UUID.randomUUID();
        CLASSES.add(id);
        jdbc.update("INSERT INTO classes (id, name, owner_id) VALUES (?, ?, ?)", id, "5b", teacher);
        jdbc.update("INSERT INTO class_students (class_id, student_id) VALUES (?, ?)", id, student);
        return id;
    }

    private void assign(UUID classId, boolean published, Instant releaseAt, Instant deadlineAt) {
        jdbc.update(
            "INSERT INTO class_worksheets (class_id, worksheet_id, position, published, release_at, deadline_at)"
                + " VALUES (?, ?, 0, ?, ?, ?)",
            classId, worksheet, published, timestamp(releaseAt), timestamp(deadlineAt)
        );
    }

    private static Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
