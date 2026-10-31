package com.patientmonitor.alarm;

/** 환자별 미확인 알람 집계 */
public record AlarmStats(
        long patientId,
        String patientName,
        String roomNumber,
        int unacknowledged,
        int critical
) {
}
