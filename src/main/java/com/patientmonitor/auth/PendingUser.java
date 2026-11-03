package com.patientmonitor.auth;

import java.time.LocalDateTime;

public record PendingUser(
        long id,
        String username,
        String name,
        String email,
        LocalDateTime createdAt
) {
}
