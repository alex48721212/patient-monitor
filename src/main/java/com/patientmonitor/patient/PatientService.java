package com.patientmonitor.patient;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.patientmonitor.common.NotFoundException;
import com.patientmonitor.realtime.MonitorEvent;

@Service
public class PatientService {

    private final PatientRepository repository;
    private final ApplicationEventPublisher events;

    public PatientService(PatientRepository repository, ApplicationEventPublisher events) {
        this.repository = repository;
        this.events = events;
    }

    public List<Patient> findAll(PatientStatus status) {
        return status == null ? repository.findAll() : repository.findByStatus(status);
    }

    public Patient get(long id) {
        return repository.findById(id).orElseThrow(() -> notFound(id));
    }

    @Transactional
    public Patient register(PatientRequest req) {
        Patient created = get(repository.insert(req));
        events.publishEvent(new MonitorEvent.PatientsChanged());
        return created;
    }

    @Transactional
    public Patient update(long id, PatientRequest req) {
        if (repository.update(id, req) == 0) {
            throw notFound(id);
        }
        events.publishEvent(new MonitorEvent.PatientsChanged());
        return get(id);
    }

    @Transactional
    public Patient discharge(long id) {
        if (repository.updateStatus(id, PatientStatus.DISCHARGED) == 0) {
            throw notFound(id);
        }
        events.publishEvent(new MonitorEvent.PatientsChanged());
        return get(id);
    }

    @Transactional
    public void delete(long id) {
        if (repository.delete(id) == 0) {
            throw notFound(id);
        }
        events.publishEvent(new MonitorEvent.PatientsChanged());
    }

    private static NotFoundException notFound(long id) {
        return new NotFoundException("환자를 찾을 수 없습니다: id=" + id);
    }
}
