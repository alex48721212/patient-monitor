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
    void adminApprovalEnablesLogin() {
        signup("approved-user", "approved@example.com", "approved-password");
        long id = userId("approved-user");

        LoginSession admin = new LoginSession(rest);
        admin.login("admin", "test-password");
        assertThat(admin.get("/api/admin/users/pending").getBody()).contains("approved-user");
        assertThat(admin.postJson("/api/admin/users/" + id + "/approve", "").getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(admin.get("/api/admin/users/pending").getBody()).doesNotContain("approved-user");

        LoginSession user = new LoginSession(rest);
        assertThat(user.login("approved-user", "approved-password").getHeaders().getLocation().getPath()).isEqualTo("/");
        assertThat(user.get("/api/patients").getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void regularUserCannotUseAdminApi() {
        signup("regular-user", "regular@example.com", "regular-password");
        long id = userId("regular-user");
        LoginSession admin = new LoginSession(rest);
        admin.login("admin", "test-password");
        admin.postJson("/api/admin/users/" + id + "/approve", "");

        signup("victim-user", "victim@example.com", "victim-password");

        LoginSession user = new LoginSession(rest);
        user.login("regular-user", "regular-password");
        assertThat(user.get("/api/admin/users/pending").getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(user.postJson("/api/admin/users/" + userId("victim-user") + "/approve", "").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rejectDeletesOnlyPendingUsers() {
        signup("rejected-user", "rejected@example.com", "rejected-password");
        long id = userId("rejected-user");
        LoginSession admin = new LoginSession(rest);
        admin.login("admin", "test-password");

        assertThat(admin.delete("/api/admin/users/" + id).getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE username = 'rejected-user'", Long.class)).isZero();

        // 활성 계정(관리자 본인)은 이 경로로 지워지지 않는다
        assertThat(admin.delete("/api/admin/users/" + userId("admin")).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private long userId(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    @Test
    void signupWithoutCsrfTokenIsForbidden() {
        assertThat(new LoginSession(rest).postJson("/api/auth/signup", "{}").getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
