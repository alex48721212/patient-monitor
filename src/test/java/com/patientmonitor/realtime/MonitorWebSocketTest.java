package com.patientmonitor.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patientmonitor.LoginSession;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"monitor.simulator.enabled=false", "monitor.security.admin-password=test-password"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class MonitorWebSocketTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    MonitorWebSocketHandler handler;

    private final BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
    private LoginSession login;
    private WebSocketSession session;

    @BeforeEach
    void connect() throws Exception {
        login = new LoginSession(rest);
        login.login("admin", "test-password");

        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.set(HttpHeaders.COOKIE, login.cookieHeader());
        session = new StandardWebSocketClient().execute(new TextWebSocketHandler() {
            @Override
            protected void handleTextMessage(WebSocketSession s, TextMessage message) throws Exception {
                received.add(objectMapper.readTree(message.getPayload()));
            }
        }, headers, URI.create("ws://localhost:" + port + "/ws/monitor")).get(5, TimeUnit.SECONDS);

        // 서버 쪽에서 세션 등록이 끝날 때까지 대기
        long deadline = System.currentTimeMillis() + 5000;
        while (handler.connectedCount() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
    }

    @AfterEach
    void close() throws Exception {
        session.close();
    }

    @Test
    void normalVitalIsPushedWithoutAlarm() throws Exception {
        postVital(1, 80, 120, 80, 98, 36.5, 16);

        JsonNode msg = next();
        assertThat(msg.get("type").asText()).isEqualTo("vital");
        assertThat(msg.get("data").get("patientId").asLong()).isEqualTo(1);
        assertThat(msg.get("data").get("heartRate").asInt()).isEqualTo(80);
        assertThat(received.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void abnormalVitalPushesVitalThenAlarm() throws Exception {
        postVital(2, 80, 120, 80, 80, 36.5, 16);

        assertThat(next().get("type").asText()).isEqualTo("vital");
        JsonNode alarm = next();
        assertThat(alarm.get("type").asText()).isEqualTo("alarm");
        assertThat(alarm.get("data").get("vitalType").asText()).isEqualTo("SPO2");
        assertThat(alarm.get("data").get("severity").asText()).isEqualTo("CRITICAL");
        assertThat(alarm.get("data").get("patientName").asText()).isEqualTo("이서연");
    }

    @Test
    void acknowledgingAlarmsBroadcastsChange() throws Exception {
        postVital(3, 150, 120, 80, 98, 36.5, 16);
        next();
        next();

        login.postJson("/api/alarms/patients/3/ack", "");

        assertThat(next().get("type").asText()).isEqualTo("alarms-changed");
    }

    @Test
    void registeringPatientBroadcastsChange() throws Exception {
        ResponseEntity<String> res = login.postJson("/api/patients", """
                {"name":"정우주","birthDate":"1966-09-09","gender":"M","roomNumber":"ICU-05"}
                """);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(next().get("type").asText()).isEqualTo("patients-changed");
    }

    @Test
    void failedRequestBroadcastsNothing() throws Exception {
        ResponseEntity<String> res = login.postJson("/api/patients/9999/vitals", vitalJson(80, 120, 80, 98, 36.5, 16));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        assertThat(received.poll(300, TimeUnit.MILLISECONDS)).isNull();
    }

    private void postVital(long patientId, int hr, int sys, int dia, int spo2, double temp, int rr) {
        ResponseEntity<String> res = login.postJson("/api/patients/" + patientId + "/vitals",
                vitalJson(hr, sys, dia, spo2, temp, rr));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private JsonNode next() throws InterruptedException {
        JsonNode msg = received.poll(5, TimeUnit.SECONDS);
        assertThat(msg).as("WebSocket 메시지 수신").isNotNull();
        return msg;
    }

    private static String vitalJson(int hr, int sys, int dia, int spo2, double temp, int rr) {
        return """
                {"heartRate":%d,"systolicBp":%d,"diastolicBp":%d,"spo2":%d,"temperature":%s,"respiratoryRate":%d}
                """.formatted(hr, sys, dia, spo2, temp, rr);
    }
}
