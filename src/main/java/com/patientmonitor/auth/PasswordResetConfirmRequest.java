package com.patientmonitor.auth;

import jakarta.validation.constraints.NotBlank;

public record PasswordResetConfirmRequest(
        @NotBlank(message = "아이디를 입력하세요") String username,
        @NotBlank(message = "인증 코드를 입력하세요") String code,
        @NotBlank(message = "새 비밀번호를 입력하세요") String newPassword
) {
}
