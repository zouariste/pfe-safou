package com.cisternino.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthResponse {
    private String token;
    private String role;
    private String email;
    private String firstName;
    private String lastName;
    private String userId;

    /** Class IDs the student belongs to (only populated for STUDENT role) */
    private List<String> classIds;

    /** True when a teacher set a temporary password — frontend must redirect to change-password page. */
    private Boolean mustChangePassword;
}
