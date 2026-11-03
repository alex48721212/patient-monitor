package com.patientmonitor.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import com.patientmonitor.LoginSession;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"monitor.simulator.enabled=false", "monitor.security.admin-password=test-password"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FindIdFlowTest {

    @Autowired
    TestRestTemplate rest;

    @MockBean
    AccountNotifier notifier;

    private void signup() {
        new LoginSession(rest).open().postJson("/api/auth/signup", """
                {"name":"김간호","email":"nurse@example.com","username":"nurse01","password":"nurse-password"}
                """);
    }

    private ResponseEntity<String> findId(String name, String email) {
        return new LoginSession(rest).open().postJson("/api/auth/find-id", """
                {"name":"%s","email":"%s"}
                """.formatted(name, email));
    }

    @Test
    void matchingInfoSendsUsernameToRegisteredEmail() {
        signup();

        assertThat(findId("김간호", "NURSE@example.com").getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);

        verify(notifier).sendUsername("nurse@example.com", "nurse01");
    }

    @Test
    void responseDoesNotRevealWhetherAccountExists() {
        signup();

        ResponseEntity<String> match = findId("김간호", "nurse@example.com");
        ResponseEntity<String> wrongName = findId("다른이름", "nurse@example.com");
        ResponseEntity<String> unknown = findId("김간호", "nobody@example.com");

        assertThat(wrongName.getStatusCode()).isEqualTo(match.getStatusCode());
        assertThat(unknown.getStatusCode()).isEqualTo(match.getStatusCode());
        assertThat(wrongName.getBody()).isEqualTo(match.getBody());
        assertThat(unknown.getBody()).isEqualTo(match.getBody());
    }

    @Test
    void wrongInfoSendsNothing() {
        signup();

        findId("다른이름", "nurse@example.com");
        findId("김간호", "nobody@example.com");

        verify(notifier, never()).sendUsername(any(), any());
    }
}
