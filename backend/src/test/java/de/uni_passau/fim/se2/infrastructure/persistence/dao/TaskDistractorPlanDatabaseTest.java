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

import java.util.UUID;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import de.uni_passau.fim.se2.testsupport.TestDatabase;

/**
 * Storing a distractor plan on an existing task. {@code distractor_plan} is {@code jsonb}, which Postgres does not
 * accept from a plain string parameter, so a missing cast only shows up against a real database: the on-demand
 * distractor buttons answered with a 500 after the model had already replied.
 */
@DisplayName("storing a distractor plan against real Postgres")
class TaskDistractorPlanDatabaseTest {

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
    @DisplayName("a plan is stored as jsonb on the task")
    void storesPlan() {
        UUID task = insertTask();

        new PostgreTaskDao(jdbc)
            .updateDistractorPlan(task, "{\"type\": \"OFF_BY_ONE\", \"rationale\": \"loop bound\"}");

        assertEquals(
            "OFF_BY_ONE",
            jdbc.queryForObject("SELECT distractor_plan->>'type' FROM tasks WHERE id = ?", String.class, task)
        );
    }

    private static UUID insertTask() {
        UUID version = UUID.randomUUID();
        jdbc.update("INSERT INTO versions (id, version_name) VALUES (?, ?)", version, "distractor-plan-test");
        UUID sequence = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO tiered_task_sequences (id, programming_language, version_id) VALUES (?, ?, ?)",
            sequence, "python", version
        );
        UUID task = UUID.randomUUID();
        jdbc.update(
            "INSERT INTO tasks (id, statement, solution, tier_level, cyclomatic_complexity, sequence_id)"
                + " VALUES (?, ?, ?, ?, ?, ?)",
            task, "statement", "x = 1", 1, 1, sequence
        );
        return task;
    }
}
