package com.patientmonitor.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.patientmonitor.LoginSession;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"monitor.simulator.enabled=false", "monitor.security.admin-password=test-password"})
class SignupFlowTest {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    private ResponseEntity<String> signup(String username, String email, String password) {
        return new LoginSession(rest).open().postJson("/api/auth/signup", """
                {"name":"김간호","email":"%s","username":"%s","password":"%s"}
                """.formatted(email, username, password));
    }

    @Test
    void signupCreatesDisabledUserWithHashedPassword() {
        assertThat(signup("nurse.kim", "Nurse.Kim@Example.com", "nurse-password").getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        var row = jdbc.queryForMap("SELECT * FROM users WHERE username = 'nurse.kim'");
        assertThat(row.get("enabled")).isEqualTo(false);
        assertThat(row.get("role")).isEqualTo("USER");
        assertThat(row.get("email")).isEqualTo("nurse.kim@example.com");
        assertThat((String) row.get("password_hash")).startsWith("$2").doesNotContain("nurse-password");
    }

    @Test
    void pendingUserCannotLogin() {
        signup("pending-user", "pending@example.com", "pending-password");

        assertThat(new LoginSession(rest).login("pending-user", "pending-password").getHeaders().getLocation().toString())
                .endsWith("/login.html?error");
    }

    @Test
    void duplicateUsernameOrEmailIsConflict() {
        signup("dup-user", "dup@example.com", "dup-password1");

        assertThat(signup("dup-user", "other@example.com", "dup-password1").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(signup("other-user", "DUP@example.com", "dup-password1").getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void invalidInputIsRejected() {
        assertThat(signup("short-pw", "short@example.com", "short").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(signup("bad id!", "badid@example.com", "valid-password").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(signup("bad-email", "not-an-email", "valid-password").getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(signup("long-pw", "long@example.com", "가".repeat(30)).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void signupWithoutCsrfTokenIsForbidden() {
        assertThat(new LoginSession(rest).postJson("/api/auth/signup", "{}").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
