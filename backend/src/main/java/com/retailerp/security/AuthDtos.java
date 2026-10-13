package com.retailerp.security;

import java.time.Instant;
import java.util.UUID;

import com.retailerp.user.Role;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request/response shapes of the auth API. Records = immutable DTOs with no boilerplate. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(max = 200) String companyName,
            @NotBlank @Size(max = 200) String fullName,
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 100) String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record MeResponse(UUID id, String email, String fullName, Role role, UUID tenantId, String tenantName) {
    }

    public record AuthResponse(String token, Instant expiresAt, MeResponse user) {
    }
}
