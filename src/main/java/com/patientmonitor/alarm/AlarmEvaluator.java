package com.patientmonitor.alarm;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.patientmonitor.vital.VitalSign;

/** 측정값 하나를 정상 범위와 비교해 알람 후보를 만든다. DB와 무관한 순수 로직이라 단위 테스트가 쉽다. */
@Component
public class AlarmEvaluator {

    static final double CRITICAL_MARGIN = 0.10;

    private final ThresholdProperties thresholds;

    public AlarmEvaluator(ThresholdProperties thresholds) {
        this.thresholds = thresholds;
    }

    public List<AlarmCandidate> evaluate(VitalSign vital) {
        List<AlarmCandidate> result = new ArrayList<>();
        for (VitalType type : VitalType.values()) {
            ThresholdProperties.Range range = type.rangeOf(thresholds);
            double value = type.valueOf(vital);

            if (value < range.min()) {
                Severity severity = value < range.min() * (1 - CRITICAL_MARGIN) ? Severity.CRITICAL : Severity.WARNING;
                result.add(new AlarmCandidate(type, value, severity,
                        "%s 낮음: %s%s (기준 %s 이상)".formatted(type.label(), fmt(value), type.unit(), fmt(range.min()))));
            } else if (value > range.max()) {
                Severity severity = value > range.max() * (1 + CRITICAL_MARGIN) ? Severity.CRITICAL : Severity.WARNING;
                result.add(new AlarmCandidate(type, value, severity,
                        "%s 높음: %s%s (기준 %s 이하)".formatted(type.label(), fmt(value), type.unit(), fmt(range.max()))));
            }
        }
        return result;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }

    public record AlarmCandidate(VitalType type, double value, Severity severity, String message) {
    }
}
