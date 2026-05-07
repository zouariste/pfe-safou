package com.cisternino.api.dto;

import lombok.*;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AdminAuditLogResponseDTO {
    private Long id;
    private String adminId;     // UUID as string
    private String action;
    private String ipAddress;
    private Long bytesSent;
    private Long rowsSent;
    private boolean success;
    private String errorMsg;
    private String createdAt;   // ISO-formatted LocalDateTime string
}
