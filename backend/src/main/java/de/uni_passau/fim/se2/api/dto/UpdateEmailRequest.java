package de.uni_passau.fim.se2.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateEmailRequest {
    @NotBlank @Email(message = "Ungültige Email-Adresse.")
    private String newEmail;
}
