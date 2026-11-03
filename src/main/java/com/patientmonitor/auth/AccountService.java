package com.patientmonitor.auth;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.patientmonitor.common.BadRequestException;
import com.patientmonitor.common.ConflictException;

@Service
public class AccountService {

    private static final int MIN_PASSWORD_LENGTH = 10;
    // BCrypt 는 72바이트까지만 처리한다
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 환자 정보를 다루므로 가입 직후에는 로그인할 수 없고, 관리자가 승인해야 활성화된다 */
    public void signup(SignupRequest req) {
        validatePassword(req.password());
        try {
            repository.insert(req.username(), passwordEncoder.encode(req.password()), req.name().trim(),
                    normalizeEmail(req.email()), "USER", false);
        } catch (DuplicateKeyException e) {
            throw new ConflictException("이미 사용 중인 아이디 또는 이메일입니다.");
        }
    }

    private static void validatePassword(String password) {
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new BadRequestException("비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BadRequestException("비밀번호가 너무 깁니다.");
        }
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
