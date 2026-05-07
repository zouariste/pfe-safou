package com.cisternino.infrastructure.persistence.mapper;

import com.cisternino.domain.entities.TaskDTO;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class TaskRowMapper implements RowMapper<TaskDTO> {

    private static int intOrZero(ResultSet rs, String col) throws SQLException {
        Integer v = rs.getObject(col, Integer.class); // erlaubt NULL
        return v == null ? 0 : v;
    }

    @Override
    public TaskDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        TaskDTO dto = new TaskDTO();

        // Pflichtfelder
        dto.setId(rs.getObject("id", UUID.class));
        dto.setStatement(rs.getString("statement"));
        dto.setSolution(rs.getString("solution"));
        dto.setTierLevel(rs.getInt("tier_level"));
        dto.setCyclomaticComplexity(intOrZero(rs, "cyclomatic_complexity"));
        dto.setLinesOfCode(intOrZero(rs, "lines_of_code"));
        dto.setDistinctIdentifierCount(intOrZero(rs, "distinct_identifier_count"));
        dto.setMaximalNestingDepth(intOrZero(rs, "maximal_nesting_depth"));
        dto.setMethodCallCount(intOrZero(rs, "method_call_count"));
        dto.setSequenceId(rs.getObject("sequence_id", UUID.class));
        dto.setDistractors(rs.getString("distractors"));
        dto.setDistractorPlan(rs.getString("distractor_plan"));
        dto.setDistractorsEnabled(rs.getBoolean("distractors_enabled"));
        dto.setContextBefore(rs.getString("context_before"));
        dto.setContextAfter(rs.getString("context_after"));

        return dto;
    }
}