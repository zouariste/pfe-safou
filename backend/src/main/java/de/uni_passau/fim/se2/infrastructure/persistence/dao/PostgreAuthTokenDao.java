package de.uni_passau.fim.se2.infrastructure.persistence.dao;

import de.uni_passau.fim.se2.domain.entities.AuthTokenDTO;
import de.uni_passau.fim.se2.domain.interfaces.AuthTokenRepository;
import de.uni_passau.fim.se2.infrastructure.persistence.mapper.AuthTokenRowMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostgreAuthTokenDao implements AuthTokenRepository {

    private final JdbcTemplate jdbc;
    private final AuthTokenRowMapper rowMapper;

    @Override
    public AuthTokenDTO save(AuthTokenDTO token) {
        if (token.getTokenId() == null) {
            token.setTokenId(UUID.randomUUID());
        }
        if (token.getCreatedAt() == null) {
            token.setCreatedAt(LocalDateTime.now());
        }

        jdbc.update("""
            INSERT INTO auth_tokens (token_id, user_id, token_value, token_type, expires_at, used, created_at)
            VALUES (?, ?, ?, ?::token_type, ?, ?, ?)
            """,
                token.getTokenId(),
                token.getUserId(),
                token.getTokenValue(),
                token.getTokenType().name(),
                Timestamp.valueOf(token.getExpiresAt()),
                token.isUsed(),
                Timestamp.valueOf(token.getCreatedAt())
        );
        return token;
    }

    @Override
    public Optional<AuthTokenDTO> findByTokenValue(String tokenValue) {
        List<AuthTokenDTO> results = jdbc.query(
                "SELECT * FROM auth_tokens WHERE LOWER(token_value) = LOWER(?)",
                rowMapper, tokenValue
        );
        return results.stream().findFirst();
    }

    @Override
    public boolean markUsed(UUID tokenId) {
        int rows = jdbc.update("UPDATE auth_tokens SET used = TRUE WHERE token_id = ? AND used = FALSE", tokenId);
        return rows > 0;
    }

    @Override
    public void deleteExpiredTokens() {
        jdbc.update("DELETE FROM auth_tokens WHERE expires_at < NOW()");
    }

    @Override
    public void deleteByUserIdAndType(UUID userId, AuthTokenDTO.TokenType tokenType) {
        jdbc.update(
                "DELETE FROM auth_tokens WHERE user_id = ? AND token_type = ?::token_type",
                userId, tokenType.name()
        );
    }
}
