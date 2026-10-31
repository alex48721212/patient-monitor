package com.patientmonitor.vital;

import java.time.LocalDateTime;

public record VitalSign(
        Long id,
        long patientId,
        int heartRate,
        int systolicBp,
        int diastolicBp,
        int spo2,
        double temperature,
        int respiratoryRate,
        LocalDateTime measuredAt
) {
}
