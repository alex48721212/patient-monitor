package com.patientmonitor;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "monitor.simulator.enabled=false")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PatientMonitorIntegrationTest {

    private static final String NEW_PATIENT = """
            {"name":"최하늘","birthDate":"1985-02-14","gender":"F","roomNumber":"W-405","diagnosis":"당뇨"}
            """;

    @Autowired
    WebApplicationContext context;

    @Autowired
    ObjectMapper objectMapper;

    MockMvc mvc;

    /** 기능 테스트이므로 로그인된 사용자와 CSRF 토큰을 모든 요청에 기본으로 넣는다 */
    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .defaultRequest(get("/").with(user("tester")).with(csrf()))
                .build();
    }

    @Test
    void sampleDataIsLoaded() throws Exception {
        mvc.perform(get("/api/patients"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name", is("김민준")));
    }

    @Test
    void patientCrudFlow() throws Exception {
        long id = createPatient();

        mvc.perform(get("/api/patients/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomNumber", is("W-405")))
                .andExpect(jsonPath("$.status", is("ADMITTED")));

        mvc.perform(put("/api/patients/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(NEW_PATIENT.replace("W-405", "ICU-03")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomNumber", is("ICU-03")));

        mvc.perform(post("/api/patients/{id}/discharge", id))
                .andExpect(jsonPath("$.status", is("DISCHARGED")));
        mvc.perform(get("/api/patients").param("status", "ADMITTED"))
                .andExpect(jsonPath("$", hasSize(3)));

        mvc.perform(delete("/api/patients/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/patients/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void invalidPatientIsRejected() throws Exception {
        mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"","birthDate":"2999-01-01","gender":"X","roomNumber":"A"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void abnormalVitalCreatesAlarmAndCanBeAcknowledged() throws Exception {
        long id = createPatient();

        postVital(id, 78, 118, 76, 98, 36.7, 15);   // 정상
        postVital(id, 142, 118, 76, 85, 36.8, 16);  // 심박수 CRITICAL, SpO2 WARNING

        mvc.perform(get("/api/patients/{id}/vitals", id))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].heartRate", is(142)));
        mvc.perform(get("/api/patients/{id}/vitals/latest", id))
                .andExpect(jsonPath("$.spo2", is(85)));
        mvc.perform(get("/api/patients/{id}/vitals/summary", id))
                .andExpect(jsonPath("$.count", is(2)))
                .andExpect(jsonPath("$.maxHeartRate", is(142)));

        mvc.perform(get("/api/alarms").param("patientId", String.valueOf(id)).param("acknowledged", "false"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].patientName", is("최하늘")));
        mvc.perform(get("/api/alarms").param("severity", "CRITICAL"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].vitalType", is("HEART_RATE")));
        mvc.perform(get("/api/alarms/stats"))
                .andExpect(jsonPath("$[0].unacknowledged", is(2)))
                .andExpect(jsonPath("$[0].critical", is(1)));

        mvc.perform(post("/api/alarms/patients/{id}/ack", id))
                .andExpect(jsonPath("$.acknowledged", is(2)));
        mvc.perform(get("/api/alarms").param("acknowledged", "false"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void vitalForUnknownPatientIs404() throws Exception {
        mvc.perform(post("/api/patients/9999/vitals").contentType(MediaType.APPLICATION_JSON)
                        .content(vitalJson(80, 120, 80, 98, 36.5, 16)))
                .andExpect(status().isNotFound());
    }

    private long createPatient() throws Exception {
        String body = mvc.perform(post("/api/patients").contentType(MediaType.APPLICATION_JSON).content(NEW_PATIENT))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(body);
        return node.get("id").asLong();
    }

    private void postVital(long patientId, int hr, int sys, int dia, int spo2, double temp, int rr) throws Exception {
        mvc.perform(post("/api/patients/{id}/vitals", patientId).contentType(MediaType.APPLICATION_JSON)
                        .content(vitalJson(hr, sys, dia, spo2, temp, rr)))
                .andExpect(status().isCreated());
    }

    private static String vitalJson(int hr, int sys, int dia, int spo2, double temp, int rr) {
        return """
                {"heartRate":%d,"systolicBp":%d,"diastolicBp":%d,"spo2":%d,"temperature":%s,"respiratoryRate":%d}
                """.formatted(hr, sys, dia, spo2, temp, rr);
    }
}
