package com.patientmonitor.auth;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.patientmonitor.common.NotFoundException;

/** 가입 승인. /api/admin/** 는 SecurityConfig 에서 ADMIN 만 접근하도록 막는다 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserRepository repository;

    public AdminUserController(UserRepository repository) {
        this.repository = repository;
    }

    @GetMapping("/pending")
    public List<PendingUser> pending() {
        return repository.findPending();
    }

    @PostMapping("/{id}/approve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(@PathVariable long id) {
        if (repository.approvePending(id) == 0) {
            throw new NotFoundException("승인 대기 중인 계정이 아닙니다: id=" + id);
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@PathVariable long id) {
        if (repository.deletePending(id) == 0) {
            throw new NotFoundException("승인 대기 중인 계정이 아닙니다: id=" + id);
        }
    }
}
