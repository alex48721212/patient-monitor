package com.patientmonitor.alarm;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 의 monitor.thresholds 정상 범위 설정 */
@ConfigurationProperties(prefix = "monitor.thresholds")
public record ThresholdProperties(
        Range heartRate,
        Range systolicBp,
        Range diastolicBp,
        Range spo2,
        Range temperature,
        Range respiratoryRate
) {

    public record Range(double min, double max) {
    }
}
