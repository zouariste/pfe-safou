package com.cisternino.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordResetConfirmRequest {
    @NotBlank
    private String token;
    @NotBlank @Size(min = 8, message = "Passwort muss mindestens 8 Zeichen lang sein.")
    private String newPassword;
}
