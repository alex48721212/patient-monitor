package com.patientmonitor.alarm;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.patientmonitor.alarm.ThresholdProperties.Range;
import com.patientmonitor.vital.VitalSign;

class AlarmEvaluatorTest {

    private final AlarmEvaluator evaluator = new AlarmEvaluator(new ThresholdProperties(
            new Range(50, 120), new Range(90, 160), new Range(50, 100),
            new Range(92, 100), new Range(35.5, 38.0), new Range(10, 25)));

    private static VitalSign vital(int hr, int sys, int dia, int spo2, double temp, int rr) {
        return new VitalSign(1L, 1L, hr, sys, dia, spo2, temp, rr, LocalDateTime.now());
    }

    @Test
    void normalValuesProduceNoAlarm() {
        assertThat(evaluator.evaluate(vital(75, 120, 80, 98, 36.6, 16))).isEmpty();
    }

    @Test
    void boundaryValuesAreNormal() {
        assertThat(evaluator.evaluate(vital(120, 160, 100, 92, 38.0, 25))).isEmpty();
        assertThat(evaluator.evaluate(vital(50, 90, 50, 100, 35.5, 10))).isEmpty();
    }

    @Test
    void slightlyHighHeartRateIsWarning() {
        List<AlarmEvaluator.AlarmCandidate> result = evaluator.evaluate(vital(125, 120, 80, 98, 36.6, 16));

        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.type()).isEqualTo(VitalType.HEART_RATE);
            assertThat(c.severity()).isEqualTo(Severity.WARNING);
            assertThat(c.message()).contains("심박수 높음").contains("125");
        });
    }

    @Test
    void farOutOfRangeIsCritical() {
        // SpO2 기준 92 의 90% = 82.8 미만이면 CRITICAL
        List<AlarmEvaluator.AlarmCandidate> result = evaluator.evaluate(vital(75, 120, 80, 80, 36.6, 16));

        assertThat(result).singleElement().satisfies(c -> {
            assertThat(c.type()).isEqualTo(VitalType.SPO2);
            assertThat(c.severity()).isEqualTo(Severity.CRITICAL);
            assertThat(c.message()).contains("산소포화도 낮음");
        });
    }

    @Test
    void multipleAbnormalValuesProduceMultipleAlarms() {
        List<AlarmEvaluator.AlarmCandidate> result = evaluator.evaluate(vital(140, 170, 80, 98, 39.2, 30));

        assertThat(result).extracting(AlarmEvaluator.AlarmCandidate::type)
                .containsExactly(VitalType.HEART_RATE, VitalType.SYSTOLIC_BP,
                        VitalType.TEMPERATURE, VitalType.RESPIRATORY_RATE);
    }
}
