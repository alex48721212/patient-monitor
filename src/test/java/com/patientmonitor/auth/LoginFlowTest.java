package com.patientmonitor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.patientmonitor.LoginSession;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"monitor.simulator.enabled=false", "monitor.security.admin-password=test-password"})
class LoginFlowTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void apiWithoutLoginIs401() {
        assertThat(rest.getForEntity("/api/patients", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void dashboardWithoutLoginRedirectsToLoginPage() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(java.util.List.of(MediaType.TEXT_HTML));
        ResponseEntity<String> res = rest.exchange("/", HttpMethod.GET, new HttpEntity<>(headers), String.class);

        // 테스트 클라이언트가 GET 리다이렉트를 따라가므로 최종 화면으로 확인한다
        assertThat(res.getBody()).contains("login-form").doesNotContain("patient-grid");
    }

    @Test
    void loginPageIsPublic() {
        ResponseEntity<String> res = rest.getForEntity("/login.html", String.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).contains("로그인");
    }

    @Test
    void wrongPasswordIsRejected() {
        LoginSession session = new LoginSession(rest);

        assertThat(session.login("admin", "wrong").getHeaders().getLocation().toString()).endsWith("/login.html?error");
    }

    @Test
    void unknownUserIsRejected() {
        LoginSession session = new LoginSession(rest);

        assertThat(session.login("nobody", "test-password").getHeaders().getLocation().toString())
                .endsWith("/login.html?error");
    }

    @Test
    void passwordIsStoredHashed() {
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE username = 'admin'", String.class);

        assertThat(hash).startsWith("$2").doesNotContain("test-password");
    }

    @Test
    void loginGivesAccessAndLogoutRevokesIt() {
        LoginSession session = new LoginSession(rest);

        ResponseEntity<String> login = session.login("admin", "test-password");
        assertThat(login.getHeaders().getLocation().getPath()).isEqualTo("/");

        ResponseEntity<String> me = get("/api/me", session);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody()).contains("\"username\":\"admin\"").contains("ROLE_ADMIN");

        String oldCookies = session.cookieHeader();
        assertThat(session.logout().getHeaders().getLocation().toString()).endsWith("/login.html?logout");

        // 로그아웃 전 쿠키를 다시 써도 세션은 이미 무효
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, oldCookies);
        assertThat(rest.exchange("/api/me", HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void writeWithoutCsrfTokenIsForbidden() {
        LoginSession session = new LoginSession(rest);
        session.login("admin", "test-password");

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, session.cookieHeader());
        headers.setContentType(MediaType.APPLICATION_JSON);

        assertThat(rest.postForEntity("/api/alarms/1/ack", new HttpEntity<>("", headers), String.class).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void webSocketWithoutLoginIsRefused() {
        assertThatThrownBy(() -> new StandardWebSocketClient()
                .execute(new TextWebSocketHandler(), "ws://localhost:" + port + "/ws/monitor")
                .get(5, TimeUnit.SECONDS))
                .isInstanceOf(Exception.class);
    }

    @Test
    void logoutClosesOpenWebSocket() throws Exception {
        LoginSession session = new LoginSession(rest);
        session.login("admin", "test-password");

        CountDownLatch closed = new CountDownLatch(1);
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.set(HttpHeaders.COOKIE, session.cookieHeader());
        WebSocketSession socket = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
                closed.countDown();
            }
        }, headers, URI.create("ws://localhost:" + port + "/ws/monitor")).get(5, TimeUnit.SECONDS);
        assertThat(socket.isOpen()).isTrue();

        session.logout();

        assertThat(closed.await(5, TimeUnit.SECONDS)).as("로그아웃 후 소켓 종료").isTrue();
    }

    private ResponseEntity<String> get(String path, LoginSession session) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, session.cookieHeader());
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
