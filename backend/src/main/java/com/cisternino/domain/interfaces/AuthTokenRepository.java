package com.cisternino.domain.interfaces;

import com.cisternino.domain.entities.AuthTokenDTO;
import java.util.Optional;
import java.util.UUID;

public interface AuthTokenRepository {
    AuthTokenDTO save(AuthTokenDTO token);
    Optional<AuthTokenDTO> findByTokenValue(String tokenValue);
    /** Atomically marks token as used. Returns true if the update succeeded (token was not already used). */
    boolean markUsed(UUID tokenId);
    void deleteExpiredTokens();
    void deleteByUserIdAndType(UUID userId, AuthTokenDTO.TokenType tokenType);
}
