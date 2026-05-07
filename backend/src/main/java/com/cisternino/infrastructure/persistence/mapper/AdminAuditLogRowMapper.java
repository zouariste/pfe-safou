package com.cisternino.infrastructure.persistence.mapper;

import com.cisternino.domain.entities.AdminAuditLogDTO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

@Component
public class AdminAuditLogRowMapper implements RowMapper<AdminAuditLogDTO> {
    @Override
    public AdminAuditLogDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp created = rs.getTimestamp("created_at");

        // Read nullable BIGINT columns before checking wasNull()
        long bytesSentRaw = rs.getLong("bytes_sent");
        boolean bytesSentNull = rs.wasNull();
        long rowsSentRaw = rs.getLong("rows_sent");
        boolean rowsSentNull = rs.wasNull();

        return AdminAuditLogDTO.builder()
                .id(rs.getLong("id"))
                .adminId(UUID.fromString(rs.getString("admin_id")))
                .action(rs.getString("action"))
                .ipAddress(rs.getString("ip_address"))
                .userAgent(rs.getString("user_agent"))
                .bytesSent(bytesSentNull ? null : bytesSentRaw)
                .rowsSent(rowsSentNull ? null : rowsSentRaw)
                .success(rs.getBoolean("success"))
                .errorMsg(rs.getString("error_msg"))
                .createdAt(created != null ? created.toLocalDateTime() : null)
                .build();
    }
}
