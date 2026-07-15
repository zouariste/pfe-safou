package de.uni_passau.fim.se2.domain.entities;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Data
@NoArgsConstructor
public class ConceptDTO {
    private UUID id;
    private String name;
    private String description;
    private boolean generated;
}