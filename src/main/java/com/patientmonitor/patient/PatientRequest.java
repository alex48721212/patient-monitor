package com.patientmonitor.patient;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PatientRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull @Past LocalDate birthDate,
        @NotNull @Pattern(regexp = "[MF]", message = "M 또는 F 여야 합니다") String gender,
        @NotBlank @Size(max = 20) String roomNumber,
        @Size(max = 200) String diagnosis
) {
}
