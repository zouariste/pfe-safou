package com.cisternino.infrastructure.persistence.mapper;

import java.sql.ResultSet;

import com.cisternino.domain.entities.*;
import org.springframework.jdbc.core.RowMapper;
import java.sql.SQLException;
import java.util.UUID;

public class TieredTaskSequenceVersionRowMapper implements RowMapper<TieredTaskSequenceVersionDTO> {
    @Override
    public TieredTaskSequenceVersionDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        TieredTaskSequenceVersionDTO dto = new TieredTaskSequenceVersionDTO();
        dto.setSequenceId(UUID.fromString(rs.getString("sequence_id")));
        dto.setVersionId(UUID.fromString(rs.getString("version_id")));
        return dto;
    }
}