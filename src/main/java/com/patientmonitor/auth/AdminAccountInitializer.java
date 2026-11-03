package com.patientmonitor.auth;

import java.security.SecureRandom;
import java.util.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 저장소에 비밀번호를 남기지 않도록, 계정이 하나도 없을 때만 관리자를 만든다.
 * 비밀번호는 ADMIN_PASSWORD 환경변수로 받고, 없으면 임의로 생성해 시작 로그에 한 번 출력한다.
 */
@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String adminEmail;

    public AdminAccountInitializer(UserRepository repository, PasswordEncoder passwordEncoder,
                                   @Value("${monitor.security.admin-username}") String adminUsername,
                                   @Value("${monitor.security.admin-password}") String adminPassword,
                                   @Value("${monitor.security.admin-email}") String adminEmail) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
        this.adminEmail = adminEmail;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }
        boolean generated = adminPassword.isBlank();
        String password = generated ? generatePassword() : adminPassword;
        repository.insert(adminUsername, passwordEncoder.encode(password), "관리자",
                adminEmail.isBlank() ? null : adminEmail, "ADMIN", true);

        if (generated) {
            log.warn("관리자 계정을 만들었습니다. 아이디: {} / 임시 비밀번호: {}", adminUsername, password);
            log.warn("비밀번호를 고정하려면 ADMIN_PASSWORD 환경변수를 설정하세요.");
        } else {
            log.info("관리자 계정을 만들었습니다. 아이디: {}", adminUsername);
        }
    }

    private static String generatePassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
