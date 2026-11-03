package com.patientmonitor.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 개발용: 메일 서버가 없으므로 메일로 보낼 내용을 서버 로그에 출력한다. 운영에서는 메일 구현으로 교체해야 한다 */
@Component
public class LoggingAccountNotifier implements AccountNotifier {

    private static final Logger log = LoggerFactory.getLogger(LoggingAccountNotifier.class);

    @Override
    public void sendUsername(String email, String username) {
        log.warn("[메일 대신 로그] 받는 사람: {} / 아이디 찾기 결과: {}", email, username);
    }

    @Override
    public void sendPasswordResetCode(String email, String code) {
        log.warn("[메일 대신 로그] 받는 사람: {} / 비밀번호 재설정 코드: {}", email, code);
    }
}
