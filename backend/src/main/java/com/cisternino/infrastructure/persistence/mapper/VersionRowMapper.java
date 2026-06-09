package com.cisternino.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import java.sql.ResultSet;

import com.cisternino.domain.entities.*;
import org.springframework.jdbc.core.RowMapper;
import java.sql.SQLException;
import java.util.UUID;

public class VersionRowMapper implements RowMapper<VersionDTO> {
    @Override
    public VersionDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        VersionDTO dto = new VersionDTO();
        dto.setId(UUID.fromString(rs.getString("id")));
        dto.setVersionName(rs.getString("version_name"));
        dto.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        dto.setDescription(rs.getString("description"));
        return dto;
    }
}