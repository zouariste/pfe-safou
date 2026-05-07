package com.cisternino.infrastructure.persistence.mapper;

import java.sql.ResultSet;

import com.cisternino.domain.entities.*;
import org.springframework.jdbc.core.RowMapper;
import java.sql.SQLException;
import java.util.UUID;

public class TieredTaskSequenceTaskRowMapper implements RowMapper<TieredTaskSequenceTaskDTO> {
    @Override
    public TieredTaskSequenceTaskDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        TieredTaskSequenceTaskDTO dto = new TieredTaskSequenceTaskDTO();
        dto.setSequenceId(UUID.fromString(rs.getString("sequence_id")));
        dto.setTaskId(UUID.fromString(rs.getString("task_id")));
        return dto;
    }
}