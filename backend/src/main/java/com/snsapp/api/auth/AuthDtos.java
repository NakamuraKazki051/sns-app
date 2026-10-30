package com.snsapp.api.auth;

import jakarta.validation.constraints.NotBlank;

public class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }
}
