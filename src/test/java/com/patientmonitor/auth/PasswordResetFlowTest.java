package com.patientmonitor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

import com.patientmonitor.LoginSession;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"monitor.simulator.enabled=false", "monitor.security.admin-password=test-password"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PasswordResetFlowTest {

    private static final String NEW_PASSWORD = "brand-new-password";

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @MockBean
    AccountNotifier notifier;

    @BeforeEach
    void createApprovedUser() {
        new LoginSession(rest).open().postJson("/api/auth/signup", """
                {"name":"김간호","email":"nurse@example.com","username":"nurse01","password":"old-password-1"}
                """);
        jdbc.update("UPDATE users SET enabled = TRUE WHERE username = 'nurse01'");
    }

    private ResponseEntity<String> request(String username, String email) {
        return new LoginSession(rest).open().postJson("/api/auth/password-reset/request", """
                {"username":"%s","email":"%s"}
                """.formatted(username, email));
    }

    private ResponseEntity<String> confirm(String code, String newPassword) {
        return new LoginSession(rest).open().postJson("/api/auth/password-reset/confirm", """
                {"username":"nurse01","code":"%s","newPassword":"%s"}
                """.formatted(code, newPassword));
    }

    private String sentCode() {
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendPasswordResetCode(any(), code.capture());
        return code.getValue();
    }

    private String wrongCode(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }

    private String loginResult(String password) {
        return new LoginSession(rest).login("nurse01", password).getHeaders().getLocation().toString();
    }

    @Test
    void correctCodeChangesPassword() {
        request("nurse01", "nurse@example.com");

        assertThat(confirm(sentCode(), NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(loginResult("old-password-1")).endsWith("/login.html?error");
        assertThat(loginResult(NEW_PASSWORD)).doesNotContain("error");
    }

    @Test
    void codeIsStoredHashedAndCannotBeReused() {
        request("nurse01", "nurse@example.com");
        String code = sentCode();

        String stored = jdbc.queryForObject("SELECT code_hash FROM password_reset_code", String.class);
        assertThat(stored).hasSize(64).isNotEqualTo(code);

        confirm(code, NEW_PASSWORD);
        assertThat(confirm(code, "another-password").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(loginResult(NEW_PASSWORD)).doesNotContain("error");
    }

    @Test
    void fiveWrongAttemptsLockTheCode() {
        request("nurse01", "nurse@example.com");
        String code = sentCode();

        for (int i = 0; i < 5; i++) {
            assertThat(confirm(wrongCode(code), NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        assertThat(confirm(code, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(loginResult("old-password-1")).doesNotContain("error");
    }

    @Test
    void expiredCodeIsRejected() {
        request("nurse01", "nurse@example.com");
        String code = sentCode();
        jdbc.update("UPDATE password_reset_code SET expires_at = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusSeconds(1)));

        assertThat(confirm(code, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void resendWithinCooldownDoesNotIssueNewCodeOrResetAttempts() {
        request("nurse01", "nurse@example.com");
        String code = sentCode();
        confirm(wrongCode(code), NEW_PASSWORD);

        request("nurse01", "nurse@example.com");

        verify(notifier, times(1)).sendPasswordResetCode(any(), any());
        assertThat(jdbc.queryForObject("SELECT attempts FROM password_reset_code", Integer.class)).isEqualTo(1);
    }

    @Test
    void responseDoesNotRevealWhetherAccountExists() {
        ResponseEntity<String> unknownUser = request("nobody", "nurse@example.com");
        ResponseEntity<String> wrongEmail = request("nurse01", "other@example.com");
        verify(notifier, never()).sendPasswordResetCode(any(), any());

        ResponseEntity<String> match = request("nurse01", "nurse@example.com");

        assertThat(unknownUser.getStatusCode()).isEqualTo(match.getStatusCode());
        assertThat(wrongEmail.getStatusCode()).isEqualTo(match.getStatusCode());
        assertThat(unknownUser.getBody()).isEqualTo(match.getBody());
    }

    @Test
    void pendingUserGetsNoCode() {
        jdbc.update("UPDATE users SET enabled = FALSE WHERE username = 'nurse01'");

        request("nurse01", "nurse@example.com");

        verify(notifier, never()).sendPasswordResetCode(any(), any());
    }

    @Test
    void weakNewPasswordIsRejectedWithoutSpendingAttempt() {
        request("nurse01", "nurse@example.com");
        String code = sentCode();

        assertThat(confirm(code, "short").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(confirm(code, NEW_PASSWORD).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }
}
