package com.patientmonitor.vital;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.patientmonitor.patient.Patient;
import com.patientmonitor.patient.PatientService;
import com.patientmonitor.patient.PatientStatus;

/**
 * 실제 장비 대신 입원 중인 환자의 바이탈을 주기적으로 생성한다.
 * 환자별로 직전 값을 기억해 랜덤 워크로 자연스럽게 변하게 하고,
 * 일정 확률로 한 항목을 비정상 범위로 튀게 해서 알람 흐름을 확인할 수 있게 한다.
 */
@Component
@ConditionalOnProperty(name = "monitor.simulator.enabled", havingValue = "true")
public class VitalSimulator {

    private static final Logger log = LoggerFactory.getLogger(VitalSimulator.class);

    private final PatientService patientService;
    private final VitalService vitalService;
    private final double spikeProbability;
    private final Map<Long, VitalRequest> lastValues = new ConcurrentHashMap<>();

    public VitalSimulator(PatientService patientService, VitalService vitalService,
                          @Value("${monitor.simulator.spike-probability:0.05}") double spikeProbability) {
        this.patientService = patientService;
        this.vitalService = vitalService;
        this.spikeProbability = spikeProbability;
    }

    @Scheduled(fixedRateString = "${monitor.simulator.interval-ms:2000}")
    public void tick() {
        for (Patient patient : patientService.findAll(PatientStatus.ADMITTED)) {
            VitalRequest next = next(lastValues.getOrDefault(patient.id(), baseline()));
            lastValues.put(patient.id(), next);
            try {
                vitalService.record(patient.id(), maybeSpike(next));
            } catch (RuntimeException e) {
                log.warn("바이탈 기록 실패 patientId={}: {}", patient.id(), e.getMessage());
            }
        }
    }

    private static VitalRequest baseline() {
        return new VitalRequest(75, 120, 80, 98, 36.6, 16);
    }

    private static VitalRequest next(VitalRequest prev) {
        return new VitalRequest(
                drift(prev.heartRate(), 3, 60, 100),
                drift(prev.systolicBp(), 3, 100, 140),
                drift(prev.diastolicBp(), 2, 60, 90),
                drift(prev.spo2(), 1, 95, 100),
                round1(clamp(prev.temperature() + rnd(-0.1, 0.1), 36.0, 37.5)),
                drift(prev.respiratoryRate(), 1, 12, 20));
    }

    private VitalRequest maybeSpike(VitalRequest v) {
        if (ThreadLocalRandom.current().nextDouble() >= spikeProbability) {
            return v;
        }
        return switch (ThreadLocalRandom.current().nextInt(6)) {
            case 0 -> new VitalRequest(randInt(125, 160), v.systolicBp(), v.diastolicBp(), v.spo2(), v.temperature(), v.respiratoryRate());
            case 1 -> new VitalRequest(v.heartRate(), randInt(165, 200), v.diastolicBp(), v.spo2(), v.temperature(), v.respiratoryRate());
            case 2 -> new VitalRequest(v.heartRate(), v.systolicBp(), randInt(40, 48), v.spo2(), v.temperature(), v.respiratoryRate());
            case 3 -> new VitalRequest(v.heartRate(), v.systolicBp(), v.diastolicBp(), randInt(80, 91), v.temperature(), v.respiratoryRate());
            case 4 -> new VitalRequest(v.heartRate(), v.systolicBp(), v.diastolicBp(), v.spo2(), round1(rnd(38.2, 40.0)), v.respiratoryRate());
            default -> new VitalRequest(v.heartRate(), v.systolicBp(), v.diastolicBp(), v.spo2(), v.temperature(), randInt(27, 35));
        };
    }

    private static int drift(int value, int step, int min, int max) {
        return (int) clamp(value + randInt(-step, step), min, max);
    }

    private static int randInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private static double rnd(double min, double max) {
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
