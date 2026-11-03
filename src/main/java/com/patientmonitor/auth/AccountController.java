package com.patientmonitor.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** 로그인 전에 쓰는 계정 API */
@RestController
@RequestMapping("/api/auth")
public class AccountController {

    private final AccountService service;
    private final PasswordResetService passwordResetService;

    public AccountController(AccountService service, PasswordResetService passwordResetService) {
        this.service = service;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public void signup(@Valid @RequestBody SignupRequest req) {
        service.signup(req);
    }

    @PostMapping("/find-id")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void findId(@Valid @RequestBody FindIdRequest req) {
        service.findUsername(req);
    }

    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void requestPasswordReset(@Valid @RequestBody PasswordResetRequest req) {
        passwordResetService.requestCode(req);
    }

    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest req) {
        passwordResetService.confirm(req);
    }
}
