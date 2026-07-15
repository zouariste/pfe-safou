package de.uni_passau.fim.se2.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdatePasswordRequest {
    @NotBlank(message = "Aktuelles Passwort ist erforderlich.")
    private String currentPassword;
    @NotBlank @Size(min = 8, message = "Neues Passwort muss mindestens 8 Zeichen lang sein.")
    private String newPassword;
}
