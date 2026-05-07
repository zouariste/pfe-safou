package com.cisternino.infrastructure.persistence.mapper;

import java.sql.ResultSet;

import com.cisternino.domain.entities.*;
import org.springframework.jdbc.core.RowMapper;
import java.sql.SQLException;
import java.util.UUID;

public class TieredTaskSequenceConceptRowMapper implements RowMapper<TieredTaskSequenceConceptDTO> {
    @Override
    public TieredTaskSequenceConceptDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        TieredTaskSequenceConceptDTO dto = new TieredTaskSequenceConceptDTO();
        dto.setSequenceId(UUID.fromString(rs.getString("sequence_id")));
        dto.setConceptId(UUID.fromString(rs.getString("concept_id")));
        return dto;
    }
}