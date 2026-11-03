package com.patientmonitor.auth;

public record AppUser(
        String username,
        String passwordHash,
        String role,
        boolean enabled
) {
}
