package com.patientmonitor.alarm;

import java.util.function.Function;
import java.util.function.ToDoubleFunction;

import com.patientmonitor.vital.VitalSign;

public enum VitalType {
    HEART_RATE("심박수", "bpm", VitalSign::heartRate, ThresholdProperties::heartRate),
    SYSTOLIC_BP("수축기 혈압", "mmHg", VitalSign::systolicBp, ThresholdProperties::systolicBp),
    DIASTOLIC_BP("이완기 혈압", "mmHg", VitalSign::diastolicBp, ThresholdProperties::diastolicBp),
    SPO2("산소포화도", "%", VitalSign::spo2, ThresholdProperties::spo2),
    TEMPERATURE("체온", "°C", VitalSign::temperature, ThresholdProperties::temperature),
    RESPIRATORY_RATE("호흡수", "회/분", VitalSign::respiratoryRate, ThresholdProperties::respiratoryRate);

    private final String label;
    private final String unit;
    private final ToDoubleFunction<VitalSign> extractor;
    private final Function<ThresholdProperties, ThresholdProperties.Range> rangeSelector;

    VitalType(String label, String unit, ToDoubleFunction<VitalSign> extractor,
              Function<ThresholdProperties, ThresholdProperties.Range> rangeSelector) {
        this.label = label;
        this.unit = unit;
        this.extractor = extractor;
        this.rangeSelector = rangeSelector;
    }

    public String label() {
        return label;
    }

    public String unit() {
        return unit;
    }

    public double valueOf(VitalSign vital) {
        return extractor.applyAsDouble(vital);
    }

    public ThresholdProperties.Range rangeOf(ThresholdProperties thresholds) {
        return rangeSelector.apply(thresholds);
    }
}
