package com.patientmonitor.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SignupRequest(
        @NotBlank(message = "이름을 입력하세요") @Size(max = 50, message = "이름은 50자 이하여야 합니다") String name,
        @NotBlank(message = "이메일을 입력하세요") @Email(message = "이메일 형식이 올바르지 않습니다")
        @Size(max = 100, message = "이메일은 100자 이하여야 합니다") String email,
        @NotBlank(message = "아이디를 입력하세요")
        @Pattern(regexp = "[A-Za-z0-9_.-]{4,50}", message = "아이디는 영문, 숫자, _ . - 로 4~50자여야 합니다") String username,
        @NotBlank(message = "비밀번호를 입력하세요") String password
) {
}
