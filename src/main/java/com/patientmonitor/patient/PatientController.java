package com.patientmonitor.patient;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private final PatientService service;

    public PatientController(PatientService service) {
        this.service = service;
    }

    @GetMapping
    public List<Patient> list(@RequestParam(required = false) PatientStatus status) {
        return service.findAll(status);
    }

    @GetMapping("/{id}")
    public Patient get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<Patient> register(@Valid @RequestBody PatientRequest req) {
        Patient created = service.register(req);
        return ResponseEntity.created(URI.create("/api/patients/" + created.id())).body(created);
    }

    @PutMapping("/{id}")
    public Patient update(@PathVariable long id, @Valid @RequestBody PatientRequest req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/discharge")
    public Patient discharge(@PathVariable long id) {
        return service.discharge(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
