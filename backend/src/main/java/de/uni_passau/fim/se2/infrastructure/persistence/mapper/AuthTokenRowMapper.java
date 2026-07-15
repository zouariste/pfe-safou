package de.uni_passau.fim.se2.infrastructure.persistence.mapper;

import de.uni_passau.fim.se2.domain.entities.AuthTokenDTO;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.UUID;

@Component
public class AuthTokenRowMapper implements RowMapper<AuthTokenDTO> {

    @Override
    public AuthTokenDTO mapRow(ResultSet rs, int rowNum) throws SQLException {
        return AuthTokenDTO.builder()
                .tokenId(UUID.fromString(rs.getString("token_id")))
                .userId(UUID.fromString(rs.getString("user_id")))
                .tokenValue(rs.getString("token_value"))
                .tokenType(AuthTokenDTO.TokenType.valueOf(rs.getString("token_type")))
                .expiresAt(toLocalDateTime(rs.getTimestamp("expires_at")))
                .used(rs.getBoolean("used"))
                .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                .build();
    }

    private java.time.LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }
}
