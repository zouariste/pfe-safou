package de.uni_passau.fim.se2.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateClassRequest {
    @NotBlank(message = "Klassenname ist erforderlich.")
    private String name;
    private String description;
}
