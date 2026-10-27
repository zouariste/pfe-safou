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

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import de.uni_passau.fim.se2.domain.entities.TaskDTO;
import de.uni_passau.fim.se2.domain.entities.TieredTaskSequenceDTO;
import de.uni_passau.fim.se2.domain.exceptions.NotFoundException;
import de.uni_passau.fim.se2.testsupport.TestDatabase;

/**
 * Loading a task or sequence that does not exist raises {@link NotFoundException}, which the API answers with 404.
 * Before, the task DAO let Spring's {@code EmptyResultDataAccessException} escape and the sequence DAO threw an
 * {@code IllegalArgumentException}; both fell through to the catch-all handler as a 500.
 */
@DisplayName("loading unknown tasks and sequences against real Postgres")
class NotFoundDatabaseTest {

    private static JdbcTemplate jdbc;

    @BeforeAll
    static void connect() {
        Assumptions.assumeTrue(
            TestDatabase.isAvailable(),
            "no test database: set SPRING_DATASOURCE_URL or start Docker"
        );
        jdbc = TestDatabase.jdbc();
    }

    @Test
    @DisplayName("an unknown task ID is not found")
    void unknownTask() {
        TaskDTO task = new TaskDTO();
        task.setId(UUID.randomUUID());
        PostgreTaskDao dao = new PostgreTaskDao(jdbc);
        assertThrows(NotFoundException.class, () -> dao.loadById(task));
    }

    @Test
    @DisplayName("an unknown sequence ID is not found")
    void unknownSequence() {
        TieredTaskSequenceDTO sequence = new TieredTaskSequenceDTO();
        sequence.setId(UUID.randomUUID());
        PostgreSequenceDAO dao = new PostgreSequenceDAO(jdbc, new ObjectMapper());
        assertThrows(NotFoundException.class, () -> dao.loadById(sequence));
    }
}
