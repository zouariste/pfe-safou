package de.uni_passau.fim.se2.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @NotBlank(message = "Vorname darf nicht leer sein.")
    private String firstName;
    @NotBlank(message = "Nachname darf nicht leer sein.")
    private String lastName;
}
