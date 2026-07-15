package de.uni_passau.fim.se2.domain.entities;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "tokenId")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokenDTO {

    private UUID tokenId;
    private UUID userId;
    private String tokenValue;
    private TokenType tokenType;
    private LocalDateTime expiresAt;
    private boolean used;
    private LocalDateTime createdAt;

    public enum TokenType {
        EMAIL_VERIFICATION, PASSWORD_RESET, STUDENT_INVITE
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}
