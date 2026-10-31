package com.patientmonitor.vital;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.patientmonitor.alarm.AlarmService;
import com.patientmonitor.common.NotFoundException;
import com.patientmonitor.patient.PatientService;
import com.patientmonitor.realtime.MonitorEvent;

@Service
public class VitalService {

    private final VitalSignRepository repository;
    private final PatientService patientService;
    private final AlarmService alarmService;
    private final ApplicationEventPublisher events;

    public VitalService(VitalSignRepository repository, PatientService patientService, AlarmService alarmService,
                        ApplicationEventPublisher events) {
        this.repository = repository;
        this.patientService = patientService;
        this.alarmService = alarmService;
        this.events = events;
    }

    /** 측정값 저장 후 이상치를 검사해 알람을 생성한다. 저장과 알람은 한 트랜잭션으로 묶는다. */
    @Transactional
    public VitalSign record(long patientId, VitalRequest req) {
        patientService.get(patientId);
        VitalSign saved = repository.insert(patientId, req);
        boolean hasAlarms = !alarmService.evaluate(saved).isEmpty();
        events.publishEvent(new MonitorEvent.VitalRecorded(saved, hasAlarms));
        return saved;
    }

    public List<VitalSign> recent(long patientId, int limit) {
        patientService.get(patientId);
        return repository.findRecent(patientId, limit);
    }

    public List<VitalSign> between(long patientId, LocalDateTime from, LocalDateTime to) {
        patientService.get(patientId);
        return repository.findBetween(patientId, from, to);
    }

    public VitalSign latest(long patientId) {
        patientService.get(patientId);
        return repository.findLatest(patientId)
                .orElseThrow(() -> new NotFoundException("측정 기록이 없습니다: patientId=" + patientId));
    }

    public VitalSummary summary(long patientId, int minutes) {
        patientService.get(patientId);
        return repository.summarize(patientId, minutes);
    }
}
