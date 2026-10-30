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
package de.uni_passau.fim.se2.infrastructure.persistence.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import de.uni_passau.fim.se2.domain.entities.ClassDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.testsupport.TestDatabase;

/**
 * The worksheets on a class page come in the order they were assigned, newest last, until the teacher reorders them.
 */
@DisplayName("ordering the worksheets assigned to a class against real Postgres")
class ClassWorksheetOrderDatabaseTest {

    private static JdbcTemplate jdbc;
    private static PostgreClassDao dao;
    private static UUID teacher;

    private UUID classId;

    @BeforeAll
    static void connect() {
        Assumptions.assumeTrue(
            TestDatabase.isAvailable(),
            "no test database: set SPRING_DATASOURCE_URL or start Docker"
        );
        jdbc = TestDatabase.jdbc();
        dao = new PostgreClassDao(jdbc);

        teacher = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO users (user_id, email, first_name, last_name, password_hash,"
                + " role, is_verified, is_active) VALUES (?,?,?,?,?,CAST(? AS user_role),?,?)",
            teacher, "class-order@example.invalid", "T", "T", "unused", "TEACHER", true, true
        );
    }

    @AfterAll
    static void cleanup() {
        if (jdbc == null) {
            return; // connect() aborted: there was no test database
        }
        // class_worksheets cascades from both sides.
        jdbc.update("DELETE FROM classes WHERE owner_id = ?", teacher);
        jdbc.update("DELETE FROM worksheets WHERE owner_id = ?", teacher);
        jdbc.update("DELETE FROM users WHERE user_id = ?", teacher);
    }

    @BeforeEach
    void newClass() {
        classId = dao.save(ClassDTO.builder().name("Order").description("").ownerId(teacher).build()).getId();
    }

    @Test
    @DisplayName("assigning appends, whatever the worksheet is called")
    void assigningAppends() {
        UUID zebra = worksheet("Zebra");
        UUID apple = worksheet("Apple");
        UUID mango = worksheet("Mango");

        dao.addWorksheet(classId, zebra);
        dao.addWorksheet(classId, apple);
        dao.addWorksheet(classId, mango);

        assertEquals(List.of(zebra, apple, mango), assignedOrder());
    }

    @Test
    @DisplayName("assigning a worksheet twice keeps its place")
    void reassigningKeepsPlace() {
        UUID first = worksheet("First");
        UUID second = worksheet("Second");
        dao.addWorksheet(classId, first);
        dao.addWorksheet(classId, second);

        dao.addWorksheet(classId, first);

        assertEquals(List.of(first, second), assignedOrder());
    }

    @Test
    @DisplayName("a reorder sticks, and later assignments still go to the end")
    void reorderSticks() {
        UUID a = worksheet("A");
        UUID b = worksheet("B");
        UUID c = worksheet("C");
        dao.addWorksheet(classId, a);
        dao.addWorksheet(classId, b);
        dao.addWorksheet(classId, c);

        dao.reorderWorksheets(classId, List.of(c, a, b));
        UUID d = worksheet("D");
        dao.addWorksheet(classId, d);

        assertEquals(List.of(c, a, b, d), assignedOrder());
    }

    @Test
    @DisplayName("a reorder naming a different set of worksheets is rejected and changes nothing")
    void staleReorderRejected() {
        UUID a = worksheet("A");
        UUID b = worksheet("B");
        dao.addWorksheet(classId, a);
        dao.addWorksheet(classId, b);

        assertThrows(DomainValidationException.class, () -> dao.reorderWorksheets(classId, List.of(b)));
        assertThrows(
            DomainValidationException.class,
            () -> dao.reorderWorksheets(classId, List.of(b, a, UUID.randomUUID()))
        );
        assertEquals(List.of(a, b), assignedOrder());
    }

    private List<UUID> assignedOrder() {
        return dao.findByIdWithDetails(classId).orElseThrow().getWorksheets().stream()
            .map(ClassDTO.WorksheetInfo::getWorksheetId)
            .toList();
    }

    private static UUID worksheet(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO worksheets (id, name, owner_id) VALUES (?, ?, ?)", id, name, teacher);
        return id;
    }
}
