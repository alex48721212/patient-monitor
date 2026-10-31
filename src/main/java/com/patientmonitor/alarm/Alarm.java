package com.patientmonitor.alarm;

import java.time.LocalDateTime;

public record Alarm(
        Long id,
        long patientId,
        String patientName,
        String roomNumber,
        long vitalSignId,
        VitalType vitalType,
        double measuredValue,
        Severity severity,
        String message,
        boolean acknowledged,
        LocalDateTime createdAt
) {
}
