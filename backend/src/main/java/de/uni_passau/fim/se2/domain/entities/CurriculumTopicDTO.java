package de.uni_passau.fim.se2.domain.entities;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
public class CurriculumTopicDTO {
    private UUID id;
    private String name;
    private String description;
    // AP19: Curriculum integration fields
    private Integer gradeLevel;
    private String track;
    private String learningArea;
    private UUID parentId;
    private int sortOrder;
}