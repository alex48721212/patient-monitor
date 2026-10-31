package com.patientmonitor.patient;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record Patient(
        Long id,
        String name,
        LocalDate birthDate,
        String gender,
        String roomNumber,
        String diagnosis,
        PatientStatus status,
        LocalDateTime admittedAt
) {
}
