package com.cisternino.infrastructure.persistence.mapper;

import com.cisternino.domain.entities.UserDTO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

@Component
public class UserRowMapper implements RowMapper<UserDTO> {

    @Override
    public UserDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        return UserDTO.builder()
                .userId(UUID.fromString(rs.getString("user_id")))
                .email(rs.getString("email"))
                .firstName(rs.getString("first_name"))
                .lastName(rs.getString("last_name"))
                .passwordHash(rs.getString("password_hash"))
                .role(UserDTO.UserRole.valueOf(rs.getString("role")))
                .verified(rs.getBoolean("is_verified"))
                .active(rs.getBoolean("is_active"))
                .mustChangePassword(rs.getBoolean("must_change_password"))
                .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                .updatedAt(toLocalDateTime(rs.getTimestamp("updated_at")))
                .build();
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
