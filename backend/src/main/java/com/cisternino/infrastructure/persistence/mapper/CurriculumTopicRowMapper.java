package com.cisternino.infrastructure.persistence.mapper;

import java.sql.ResultSet;

import com.cisternino.domain.entities.*;
import org.springframework.jdbc.core.RowMapper;
import java.sql.SQLException;
import java.util.UUID;

public class CurriculumTopicRowMapper implements RowMapper<CurriculumTopicDTO> {
    @Override
    public CurriculumTopicDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        CurriculumTopicDTO dto = new CurriculumTopicDTO();
        dto.setId(UUID.fromString(rs.getString("id")));
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        // AP19: extended fields
        int grade = rs.getInt("grade_level");
        dto.setGradeLevel(rs.wasNull() ? null : grade);
        dto.setTrack(rs.getString("track"));
        dto.setLearningArea(rs.getString("learning_area"));
        String parentStr = rs.getString("parent_id");
        dto.setParentId(parentStr != null ? UUID.fromString(parentStr) : null);
        dto.setSortOrder(rs.getInt("sort_order"));
        return dto;
    }
}