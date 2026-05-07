package com.cisternino.domain.entities;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
public class CurriculumCodeExampleDTO {
    private UUID id;
    private UUID topicId;
    private String language;
    private String title;
    private String code;
    private String problemStatement;
    private String difficulty;
    private String notes;
    private LocalDateTime createdAt;
}
