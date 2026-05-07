package com.cisternino.infrastructure.persistence.mapper;

import com.cisternino.domain.entities.SequenceTagDTO;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class SequenceTagRowMapper implements RowMapper<SequenceTagDTO> {
    @Override
    public SequenceTagDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        SequenceTagDTO dto = new SequenceTagDTO();
        dto.setSequenceId(rs.getObject("sequence_id", UUID.class));
        dto.setTagName(rs.getString("tag_name"));
        dto.setSource(rs.getString("source"));
        float conf = rs.getFloat("confidence");
        if (!rs.wasNull()) dto.setConfidence(conf);
        var ts = rs.getTimestamp("created_at");
        if (ts != null) dto.setCreatedAt(ts.toLocalDateTime());
        return dto;
    }
}
