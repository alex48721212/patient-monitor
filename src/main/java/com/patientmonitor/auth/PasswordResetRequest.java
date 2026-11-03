package com.patientmonitor.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(
        @NotBlank(message = "아이디를 입력하세요") String username,
        @NotBlank(message = "이메일을 입력하세요") @Email(message = "이메일 형식이 올바르지 않습니다") String email
) {
}
