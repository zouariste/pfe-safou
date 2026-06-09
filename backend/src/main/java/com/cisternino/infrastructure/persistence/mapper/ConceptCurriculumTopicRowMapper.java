package com.cisternino.infrastructure.persistence.mapper;

import java.sql.ResultSet;

import com.cisternino.domain.entities.*;
import org.springframework.jdbc.core.RowMapper;
import java.sql.SQLException;
import java.util.UUID;

public class ConceptCurriculumTopicRowMapper implements RowMapper<ConceptCurriculumTopicDTO> {
    @Override
    public ConceptCurriculumTopicDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        ConceptCurriculumTopicDTO dto = new ConceptCurriculumTopicDTO();
        dto.setConceptId(UUID.fromString(rs.getString("concept_id")));
        dto.setTopicId(UUID.fromString(rs.getString("topic_id")));
        return dto;
    }
}