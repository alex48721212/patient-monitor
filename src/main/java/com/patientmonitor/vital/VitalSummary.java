package com.patientmonitor.vital;

public record VitalSummary(
        long patientId,
        int minutes,
        int count,
        double avgHeartRate,
        int minHeartRate,
        int maxHeartRate,
        double avgSystolicBp,
        double avgDiastolicBp,
        double avgSpo2,
        int minSpo2,
        double avgTemperature,
        double maxTemperature,
        double avgRespiratoryRate
) {
}
