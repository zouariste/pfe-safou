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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.uni_passau.fim.se2.domain.entities.TaskSubmissionDTO;
import de.uni_passau.fim.se2.domain.exceptions.DomainValidationException;
import de.uni_passau.fim.se2.domain.interfaces.TaskSubmissionRepository;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Repository
public class PostgreTaskSubmissionDao implements TaskSubmissionRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PostgreTaskSubmissionDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private final RowMapper<TaskSubmissionDTO> rowMapper = (rs, rowNum) -> TaskSubmissionDTO.builder()
            .userId(rs.getObject("user_id", UUID.class))
            .taskId(rs.getObject("task_id", UUID.class))
            .blockOrder(readList(rs.getString("block_order"), new TypeReference<List<String>>() {}))
            .indentLevels(readList(rs.getString("indent_levels"), new TypeReference<List<Integer>>() {}))
            .solvedAt(rs.getTimestamp("solved_at") != null ? rs.getTimestamp("solved_at").toLocalDateTime() : null)
            .updatedAt(rs.getTimestamp("updated_at") != null ? rs.getTimestamp("updated_at").toLocalDateTime() : null)
            .build();

    @Override
    public void save(TaskSubmissionDTO submission) {
        if (submission == null || submission.getUserId() == null || submission.getTaskId() == null) {
            throw new DomainValidationException("Task submission needs a user and a task.");
        }

        // One row per student and task: solving again replaces the arrangement
        // rather than adding to a history. The audit trail is learning_events.
        String sql = """
                INSERT INTO task_submissions (user_id, task_id, block_order, indent_levels,
                                              solved_at, updated_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, task_id) DO UPDATE
                SET block_order   = EXCLUDED.block_order,
                    indent_levels = EXCLUDED.indent_levels,
                    solved_at     = EXCLUDED.solved_at,
                    updated_at    = CURRENT_TIMESTAMP
                """;

        jdbc.update(sql,
                submission.getUserId(),
                submission.getTaskId(),
                toJsonb(submission.getBlockOrder()),
                toJsonb(submission.getIndentLevels()));

        log.debug("Stored solved arrangement for user={} task={}",
                submission.getUserId(), submission.getTaskId());
    }

    @Override
    public Optional<TaskSubmissionDTO> find(UUID userId, UUID taskId) {
        if (userId == null || taskId == null) return Optional.empty();

        String sql = """
                SELECT user_id, task_id, block_order, indent_levels, solved_at, updated_at
                FROM task_submissions
                WHERE user_id = ? AND task_id = ?
                """;

        return jdbc.query(sql, rowMapper, userId, taskId).stream().findFirst();
    }

    /** Wraps a list as a jsonb parameter; a null list is stored as an empty array. */
    private PGobject toJsonb(Object value) {
        try {
            PGobject json = new PGobject();
            json.setType("jsonb");
            json.setValue(objectMapper.writeValueAsString(value != null ? value : List.of()));
            return json;
        } catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new DomainValidationException("Could not serialise task submission: " + e.getMessage());
        }
    }

    /** A row written by an older or broken client must not break loading the page. */
    private <T> List<T> readList(String json, TypeReference<List<T>> type) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            log.warn("Unreadable task submission payload, treating as empty: {}", e.getMessage());
            return List.of();
        }
    }
}
