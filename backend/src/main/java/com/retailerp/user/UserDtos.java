package com.retailerp.user;

import java.time.Instant;
import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UserDtos {

    private UserDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 200) String fullName,
            @NotNull Role role,
            @NotBlank @Size(min = 8, max = 100) String password) {
    }

    public record UpdateUserRequest(@NotBlank @Size(max = 200) String fullName, @NotNull Role role, boolean enabled) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(min = 8, max = 100) String password) {
    }

    public record UserResponse(UUID id, String email, String fullName, Role role, boolean enabled, Instant createdAt) {

        static UserResponse from(User user) {
            return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.isEnabled(), user.getCreatedAt());
        }
    }
}
