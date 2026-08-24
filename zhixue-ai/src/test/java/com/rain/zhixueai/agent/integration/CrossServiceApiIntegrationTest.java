package com.rain.zhixueai.agent.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CrossServiceApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void userProfileEndpoint_isReachable() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer test-token");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/v1/users/profiles/1/agent-summary",
            HttpMethod.GET, entity, Map.class);

        assertNotNull(response);
    }

    @Test
    void problemRecommendEndpoint_isReachable() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer test-token");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(
            Map.of("userId", 1, "strategy", "weakness", "limit", 3), headers);
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/v1/problems/agent/recommend",
            HttpMethod.POST, entity, Map.class);

        assertNotNull(response);
    }

    @Test
    void userStatusEndpoint_isReachable() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer test-token");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/v1/problems/agent/user-status/1",
            HttpMethod.GET, entity, Map.class);

        assertNotNull(response);
    }

    @Test
    void wrongQuestionsEndpoint_isReachable() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer test-token");

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
            "/api/v1/problems/agent/wrong-questions/1",
            HttpMethod.GET, entity, Map.class);

        assertNotNull(response);
    }
}
