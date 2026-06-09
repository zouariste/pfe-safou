package com.cisternino.domain.entities;

import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Stores the original version of a task before teacher edits (AP9).
 * One original per task — created on the first edit only.
 */
@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskOriginalDTO {

    private UUID id;
    private UUID taskId;
    private String originalProblemStatement;
    private String originalCode;
    private Integer adjustmentRating;
    private LocalDateTime savedAt;
}
