package com.patientmonitor.vital;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@RestController
@Validated
@RequestMapping("/api/patients/{patientId}/vitals")
public class VitalController {

    private final VitalService service;

    public VitalController(VitalService service) {
        this.service = service;
    }

    /** 장비 연동용 측정값 입력 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VitalSign record(@PathVariable long patientId, @Valid @RequestBody VitalRequest req) {
        return service.record(patientId, req);
    }

    /** from/to 를 주면 기간 조회, 없으면 최근 limit 건 */
    @GetMapping
    public List<VitalSign> list(
            @PathVariable long patientId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(1000) int limit,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        if (from != null) {
            return service.between(patientId, from, to != null ? to : LocalDateTime.now());
        }
        return service.recent(patientId, limit);
    }

    @GetMapping("/latest")
    public VitalSign latest(@PathVariable long patientId) {
        return service.latest(patientId);
    }

    @GetMapping("/summary")
    public VitalSummary summary(@PathVariable long patientId,
                                @RequestParam(defaultValue = "10") @Min(1) @Max(1440) int minutes) {
        return service.summary(patientId, minutes);
    }
}
