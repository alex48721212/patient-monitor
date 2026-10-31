package com.patientmonitor.alarm;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.patientmonitor.common.NotFoundException;
import com.patientmonitor.realtime.MonitorEvent;
import com.patientmonitor.vital.VitalSign;

@Service
public class AlarmService {

    private static final Logger log = LoggerFactory.getLogger(AlarmService.class);

    private final AlarmEvaluator evaluator;
    private final AlarmRepository repository;
    private final ApplicationEventPublisher events;

    public AlarmService(AlarmEvaluator evaluator, AlarmRepository repository, ApplicationEventPublisher events) {
        this.evaluator = evaluator;
        this.repository = repository;
        this.events = events;
    }

    @Transactional
    public List<AlarmEvaluator.AlarmCandidate> evaluate(VitalSign vital) {
        List<AlarmEvaluator.AlarmCandidate> candidates = evaluator.evaluate(vital);
        if (!candidates.isEmpty()) {
            repository.insertAll(vital.patientId(), vital.id(), candidates);
            candidates.forEach(c -> log.warn("[ALARM][{}] patientId={} {}", c.severity(), vital.patientId(), c.message()));
        }
        return candidates;
    }

    public List<Alarm> search(Long patientId, Boolean acknowledged, Severity severity, int limit) {
        return repository.search(patientId, acknowledged, severity, limit);
    }

    @Transactional
    public void acknowledge(long id) {
        if (repository.acknowledge(id) == 0) {
            throw new NotFoundException("알람을 찾을 수 없습니다: id=" + id);
        }
        events.publishEvent(new MonitorEvent.AlarmsChanged());
    }

    @Transactional
    public int acknowledgeAllForPatient(long patientId) {
        int count = repository.acknowledgeAllForPatient(patientId);
        if (count > 0) {
            events.publishEvent(new MonitorEvent.AlarmsChanged());
        }
        return count;
    }

    public List<AlarmStats> stats() {
        return repository.unacknowledgedStats();
    }
}
