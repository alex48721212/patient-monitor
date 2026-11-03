package com.patientmonitor;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/** 브라우저처럼 쿠키를 들고 실제 로그인 절차를 밟는 테스트용 HTTP 클라이언트 */
public class LoginSession {

    private final TestRestTemplate rest;
    private final Map<String, String> cookies = new TreeMap<>();

    public LoginSession(TestRestTemplate rest) {
        this.rest = rest;
    }

    /** 로그인 페이지에서 CSRF 쿠키를 받은 뒤 폼을 제출한다. 응답은 302 (Location 으로 성공/실패 구분) */
    public ResponseEntity<String> login(String username, String password) {
        store(rest.getForEntity("/login.html", String.class));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", username);
        form.add("password", password);
        form.add("_csrf", cookies.get("XSRF-TOKEN"));

        HttpHeaders headers = headers();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        ResponseEntity<String> response = store(rest.postForEntity("/login", new HttpEntity<>(form, headers), String.class));

        // 로그인하면 CSRF 토큰이 교체된다. 브라우저가 리다이렉트된 페이지에서 새 쿠키를 받는 것과 같이 동작
        store(rest.exchange("/api/me", HttpMethod.GET, new HttpEntity<>(headers()), String.class));
        return response;
    }

    public ResponseEntity<String> logout() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("_csrf", cookies.get("XSRF-TOKEN"));

        HttpHeaders headers = headers();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return store(rest.postForEntity("/logout", new HttpEntity<>(form, headers), String.class));
    }

    public ResponseEntity<String> postJson(String path, String body) {
        HttpHeaders headers = headers();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-XSRF-TOKEN", cookies.get("XSRF-TOKEN"));
        return store(rest.postForEntity(path, new HttpEntity<>(body, headers), String.class));
    }

    public String cookieHeader() {
        return cookies.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .reduce((a, b) -> a + "; " + b)
                .orElse("");
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        if (!cookies.isEmpty()) {
            headers.set(HttpHeaders.COOKIE, cookieHeader());
        }
        return headers;
    }

    private ResponseEntity<String> store(ResponseEntity<String> response) {
        List<String> setCookies = response.getHeaders().getOrDefault(HttpHeaders.SET_COOKIE, List.of());
        for (String setCookie : setCookies) {
            String[] pair = setCookie.split(";", 2)[0].split("=", 2);
            if (pair.length < 2 || pair[1].isEmpty()) {
                cookies.remove(pair[0]);
            } else {
                cookies.put(pair[0], pair[1]);
            }
        }
        return response;
    }
}
