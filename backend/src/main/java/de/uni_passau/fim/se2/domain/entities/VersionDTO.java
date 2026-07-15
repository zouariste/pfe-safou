package de.uni_passau.fim.se2.domain.entities;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
public class VersionDTO {
    private UUID id;
    private String versionName;
    private LocalDateTime createdAt;
    private String description;
}