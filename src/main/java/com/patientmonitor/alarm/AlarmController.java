package com.patientmonitor.alarm;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@Validated
@RequestMapping("/api/alarms")
public class AlarmController {

    private final AlarmService service;

    public AlarmController(AlarmService service) {
        this.service = service;
    }

    /** 예) /api/alarms?acknowledged=false&severity=CRITICAL */
    @GetMapping
    public List<Alarm> search(@RequestParam(required = false) Long patientId,
                              @RequestParam(required = false) Boolean acknowledged,
                              @RequestParam(required = false) Severity severity,
                              @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        return service.search(patientId, acknowledged, severity, limit);
    }

    @GetMapping("/stats")
    public List<AlarmStats> stats() {
        return service.stats();
    }

    @PostMapping("/{id}/ack")
    public ResponseEntity<Void> acknowledge(@PathVariable long id) {
        service.acknowledge(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/patients/{patientId}/ack")
    public Map<String, Integer> acknowledgeAll(@PathVariable long patientId) {
        return Map.of("acknowledged", service.acknowledgeAllForPatient(patientId));
    }
}
