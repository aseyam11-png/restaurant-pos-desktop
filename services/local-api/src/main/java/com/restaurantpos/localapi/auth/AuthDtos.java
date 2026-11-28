package com.restaurantpos.localapi.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record SetupStatus(boolean initialized) {}

    public record BootstrapRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,40}") String username,
        @NotBlank @Size(min = 2, max = 80) String displayName,
        @NotBlank @Size(min = 12, max = 128) String password
    ) {}

    public record LoginRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,40}") String username,
        @NotBlank @Size(max = 128) String password
    ) {}

    public record ChangePasswordRequest(
        @NotBlank @Size(max = 128) String currentPassword,
        @NotBlank @Size(min = 12, max = 128) String newPassword
    ) {}

    public record AuthSession(
        String accessToken,
        String tokenType,
        OffsetDateTime expiresAt,
        UUID userId,
        String username,
        String displayName,
        String role,
        boolean mustChangePassword
    ) {}

    public record CurrentUser(
        UUID userId,
        String username,
        String displayName,
        String role,
        OffsetDateTime sessionExpiresAt,
        boolean mustChangePassword
    ) {}

    public record CreateStaffRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,40}") String username,
        @NotBlank @Size(min = 2, max = 80) String displayName,
        @NotBlank @Size(min = 12, max = 128) String temporaryPassword,
        @NotNull StaffRole role
    ) {}

    public enum StaffRole { MANAGER, CASHIER }

    public record StaffView(
        UUID userId,
        String username,
        String displayName,
        String role,
        boolean active,
        boolean mustChangePassword,
        OffsetDateTime createdAt
    ) {}
}
