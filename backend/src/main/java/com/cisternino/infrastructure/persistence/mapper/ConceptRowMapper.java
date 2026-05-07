package com.cisternino.infrastructure.persistence.mapper;

import java.sql.ResultSet;

import com.cisternino.domain.entities.*;
import org.springframework.jdbc.core.RowMapper;
import java.sql.SQLException;
import java.util.UUID;

public class ConceptRowMapper implements RowMapper<ConceptDTO> {
    @Override
    public ConceptDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        ConceptDTO dto = new ConceptDTO();
        dto.setId(UUID.fromString(rs.getString("id")));
        dto.setName(rs.getString("name"));
        dto.setDescription(rs.getString("description"));
        dto.setGenerated(rs.getBoolean("generated"));
        return dto;
    }
}