package com.cisternino.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateStudentRequest {
    @NotBlank(message = "Vorname ist erforderlich.")
    private String firstName;
    @NotBlank(message = "Nachname ist erforderlich.")
    private String lastName;
}
