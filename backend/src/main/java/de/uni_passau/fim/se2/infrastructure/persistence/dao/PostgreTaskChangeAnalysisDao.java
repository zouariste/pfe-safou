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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.postgresql.util.PGobject;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import de.uni_passau.fim.se2.domain.entities.TaskChangeAnalysisDTO;
import de.uni_passau.fim.se2.domain.interfaces.TaskChangeAnalysisRepository;

@Repository
public class PostgreTaskChangeAnalysisDao implements TaskChangeAnalysisRepository {

    private final JdbcTemplate jdbc;

    public PostgreTaskChangeAnalysisDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String UPSERT_SQL = """
            INSERT INTO task_change_analyses (
                id, task_id, task_original_id,
                text_levenshtein_ratio, text_jaccard_similarity,
                text_words_added, text_words_removed, text_words_modified, text_length_delta,
                code_levenshtein_ratio,
                code_lines_added, code_lines_removed, code_lines_modified, code_lines_reordered,
                code_loc_delta, code_complexity_delta, code_token_change_ratio,
                ast_nodes_added, ast_nodes_removed, ast_nodes_modified,
                ast_structural_score, ast_change_types, ast_distractor_changes,
                overall_change_score, change_category, teacher_rating
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (task_id) DO UPDATE SET
                task_original_id        = EXCLUDED.task_original_id,
                analyzed_at             = CURRENT_TIMESTAMP,
                text_levenshtein_ratio  = EXCLUDED.text_levenshtein_ratio,
                text_jaccard_similarity = EXCLUDED.text_jaccard_similarity,
                text_words_added        = EXCLUDED.text_words_added,
                text_words_removed      = EXCLUDED.text_words_removed,
                text_words_modified     = EXCLUDED.text_words_modified,
                text_length_delta       = EXCLUDED.text_length_delta,
                code_levenshtein_ratio  = EXCLUDED.code_levenshtein_ratio,
                code_lines_added        = EXCLUDED.code_lines_added,
                code_lines_removed      = EXCLUDED.code_lines_removed,
                code_lines_modified     = EXCLUDED.code_lines_modified,
                code_lines_reordered    = EXCLUDED.code_lines_reordered,
                code_loc_delta          = EXCLUDED.code_loc_delta,
                code_complexity_delta   = EXCLUDED.code_complexity_delta,
                code_token_change_ratio = EXCLUDED.code_token_change_ratio,
                ast_nodes_added         = EXCLUDED.ast_nodes_added,
                ast_nodes_removed       = EXCLUDED.ast_nodes_removed,
                ast_nodes_modified      = EXCLUDED.ast_nodes_modified,
                ast_structural_score    = EXCLUDED.ast_structural_score,
                ast_change_types        = EXCLUDED.ast_change_types,
                ast_distractor_changes  = EXCLUDED.ast_distractor_changes,
                overall_change_score    = EXCLUDED.overall_change_score,
                change_category         = EXCLUDED.change_category,
                teacher_rating          = EXCLUDED.teacher_rating
        """;

    private static final RowMapper<TaskChangeAnalysisDTO> ROW_MAPPER = PostgreTaskChangeAnalysisDao::mapRow;

    private static TaskChangeAnalysisDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        TaskChangeAnalysisDTO dto = new TaskChangeAnalysisDTO();
        dto.setId(rs.getObject("id", UUID.class));
        dto.setTaskId(rs.getObject("task_id", UUID.class));
        dto.setTaskOriginalId(rs.getObject("task_original_id", UUID.class));
        dto.setAnalyzedAt(
            rs.getTimestamp("analyzed_at") != null
                ? rs.getTimestamp("analyzed_at").toLocalDateTime()
                : null
        );

        // Text-level metrics
        dto.setTextLevenshteinRatio(nullableDouble(rs, "text_levenshtein_ratio"));
        dto.setTextJaccardSimilarity(nullableDouble(rs, "text_jaccard_similarity"));
        dto.setTextWordsAdded(nullableInt(rs, "text_words_added"));
        dto.setTextWordsRemoved(nullableInt(rs, "text_words_removed"));
        dto.setTextWordsModified(nullableInt(rs, "text_words_modified"));
        dto.setTextLengthDelta(nullableInt(rs, "text_length_delta"));

        // Code-level metrics
        dto.setCodeLevenshteinRatio(nullableDouble(rs, "code_levenshtein_ratio"));
        dto.setCodeLinesAdded(nullableInt(rs, "code_lines_added"));
        dto.setCodeLinesRemoved(nullableInt(rs, "code_lines_removed"));
        dto.setCodeLinesModified(nullableInt(rs, "code_lines_modified"));
        dto.setCodeLinesReordered(nullableInt(rs, "code_lines_reordered"));
        dto.setCodeLocDelta(nullableInt(rs, "code_loc_delta"));
        dto.setCodeComplexityDelta(nullableInt(rs, "code_complexity_delta"));
        dto.setCodeTokenChangeRatio(nullableDouble(rs, "code_token_change_ratio"));

        // AST-level metrics
        dto.setAstNodesAdded(nullableInt(rs, "ast_nodes_added"));
        dto.setAstNodesRemoved(nullableInt(rs, "ast_nodes_removed"));
        dto.setAstNodesModified(nullableInt(rs, "ast_nodes_modified"));
        dto.setAstStructuralScore(nullableDouble(rs, "ast_structural_score"));
        dto.setAstChangeTypes(rs.getString("ast_change_types"));
        dto.setAstDistractorChanges(rs.getString("ast_distractor_changes"));

        // Overall
        dto.setOverallChangeScore(nullableDouble(rs, "overall_change_score"));
        dto.setChangeCategory(rs.getString("change_category"));
        dto.setTeacherRating(nullableInt(rs, "teacher_rating"));

        return dto;
    }

    @Override
    public void save(TaskChangeAnalysisDTO a) {
        UUID id = a.getId() != null ? a.getId() : UUID.randomUUID();
        a.setId(id);

        jdbc.update(
            UPSERT_SQL,
            id,
            a.getTaskId(),
            a.getTaskOriginalId(),
            a.getTextLevenshteinRatio(),
            a.getTextJaccardSimilarity(),
            a.getTextWordsAdded(),
            a.getTextWordsRemoved(),
            a.getTextWordsModified(),
            a.getTextLengthDelta(),
            a.getCodeLevenshteinRatio(),
            a.getCodeLinesAdded(),
            a.getCodeLinesRemoved(),
            a.getCodeLinesModified(),
            a.getCodeLinesReordered(),
            a.getCodeLocDelta(),
            a.getCodeComplexityDelta(),
            a.getCodeTokenChangeRatio(),
            a.getAstNodesAdded(),
            a.getAstNodesRemoved(),
            a.getAstNodesModified(),
            a.getAstStructuralScore(),
            toJsonb(a.getAstChangeTypes()),
            toJsonb(a.getAstDistractorChanges()),
            a.getOverallChangeScore(),
            a.getChangeCategory(),
            a.getTeacherRating()
        );
    }

    @Override
    public Optional<TaskChangeAnalysisDTO> findByTaskId(UUID taskId) {
        String sql = "SELECT * FROM task_change_analyses WHERE task_id = ?";
        try {
            TaskChangeAnalysisDTO result = jdbc.queryForObject(sql, ROW_MAPPER, taskId);
            return Optional.ofNullable(result);
        }
        catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public List<TaskChangeAnalysisDTO> findAll() {
        String sql = "SELECT * FROM task_change_analyses ORDER BY analyzed_at DESC";
        return jdbc.query(sql, ROW_MAPPER);
    }

    @Override
    public boolean existsByTaskId(UUID taskId) {
        String sql = "SELECT COUNT(*) FROM task_change_analyses WHERE task_id = ?";
        Integer count = jdbc.queryForObject(sql, Integer.class, taskId);
        return count != null && count > 0;
    }

    @Override
    public void deleteByTaskId(UUID taskId) {
        jdbc.update("DELETE FROM task_change_analyses WHERE task_id = ?", taskId);
    }

    // ── Helper ──

    private static PGobject toJsonb(String value) {
        if (value == null) {
            return null;
        }
        try {
            PGobject jsonb = new PGobject();
            jsonb.setType("jsonb");
            jsonb.setValue(value);
            return jsonb;
        }
        catch (SQLException e) {
            throw new IllegalStateException("Failed to create JSONB PGobject", e);
        }
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
