package com.patientmonitor.vital;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** 장비(또는 시뮬레이터)가 보내는 측정값 한 건. 범위 검증은 물리적으로 불가능한 값만 걸러낸다. */
public record VitalRequest(
        @Min(0) @Max(300) int heartRate,
        @Min(0) @Max(300) int systolicBp,
        @Min(0) @Max(200) int diastolicBp,
        @Min(0) @Max(100) int spo2,
        @DecimalMin("25.0") @DecimalMax("45.0") double temperature,
        @Min(0) @Max(80) int respiratoryRate
) {
}
