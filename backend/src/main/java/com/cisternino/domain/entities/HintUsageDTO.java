package com.cisternino.domain.entities;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter @Setter @ToString @EqualsAndHashCode(of = "id") @Builder @NoArgsConstructor @AllArgsConstructor
public class HintUsageDTO {
    private UUID id;
    private UUID userId;
    private UUID taskId;
    private int hintLevel;
    private String blockOrderAtRequest; // JSON string
    private String findings;            // JSON string
    private String hintText;
    private String llmExplanation;
    private String affectedBlockIds;    // JSON string
    private LocalDateTime createdAt;
}
