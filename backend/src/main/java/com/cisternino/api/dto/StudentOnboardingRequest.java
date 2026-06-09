package com.cisternino.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StudentOnboardingRequest {
    @NotBlank
    private String token;
    @NotBlank @Email
    private String email;
    @NotBlank @Size(min = 8, message = "Passwort muss mindestens 8 Zeichen lang sein.")
    private String password;
}
