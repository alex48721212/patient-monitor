package com.patientmonitor.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record FindIdRequest(
        @NotBlank(message = "이름을 입력하세요") String name,
        @NotBlank(message = "이메일을 입력하세요") @Email(message = "이메일 형식이 올바르지 않습니다") String email
) {
}
