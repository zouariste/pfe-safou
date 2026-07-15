package de.uni_passau.fim.se2.domain.entities;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAuditLogDTO {
    private Long id;
    private UUID adminId;
    private String action;        // "FULL_BACKUP" | "RESEARCH_EXPORT"
    private String ipAddress;
    private String userAgent;
    private Long bytesSent;       // nullable
    private Long rowsSent;        // nullable
    private boolean success;
    private String errorMsg;      // nullable
    private LocalDateTime createdAt;

    /** Action constants — use these instead of magic strings. */
    public static final class Action {
        public static final String FULL_BACKUP     = "FULL_BACKUP";
        public static final String RESEARCH_EXPORT = "RESEARCH_EXPORT";
        private Action() {}
    }
}
