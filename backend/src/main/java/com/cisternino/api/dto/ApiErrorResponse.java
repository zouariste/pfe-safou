package com.cisternino.api.dto;

import lombok.*;

@Getter
@Setter
public class ApiErrorResponse {
    private String error;
    private String message;

    public ApiErrorResponse(String error, String message) {
        this.error = error;
        this.message = message;
    }
}