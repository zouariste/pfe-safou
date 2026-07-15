package de.uni_passau.fim.se2.domain.entities;

import lombok.*;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Represents a learning analytics event (AP10).
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LearningEventDTO {

    private UUID id;
    private UUID userId;        // nullable — null for anonymous or anonymized
    private UUID taskId;
    private UUID sequenceId;    // nullable
    private Integer tierLevel;  // nullable
    private String eventType;
    private String eventData;   // stored as JSONB string
    private Timestamp createdAt;
}
