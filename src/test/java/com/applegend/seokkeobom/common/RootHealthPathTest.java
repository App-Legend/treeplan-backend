package com.applegend.seokkeobom.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** 실제 Tomcat에서: probe가 부르는 /common/health 와 기존 /api 주소가 함께 동작하는지 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class RootHealthPathTest {
    @Autowired TestRestTemplate http;
    @LocalServerPort int port;

    /** TestRestTemplate은 상대 주소에 /api 를 붙이므로, 서버 루트 기준 전체 주소로 부른다 */
    private ResponseEntity<String> get(String path) {
        return http.getForEntity("http://localhost:" + port + path, String.class);
    }

    @Test
    void healthAnswersWithAndWithoutApiPrefix() {
        for (String path : new String[] {"/common/health", "/api/common/health"}) {
            ResponseEntity<String> res = get(path);
            assertThat(res.getStatusCode().value()).as(path).isEqualTo(200);
            assertThat(res.getBody()).as(path).contains("\"database\":\"up\"");
        }
    }

    @Test
    void deployDemoAnswersWithAndWithoutApiPrefix() {
        for (String path : new String[] {"/common/deploy-demo", "/api/common/deploy-demo"}) {
            ResponseEntity<String> res = get(path);
            assertThat(res.getStatusCode().value()).as(path).isEqualTo(200);
            assertThat(res.getBody()).as(path).contains("team-v1");
        }
    }

    @Test
    void otherRootPathsAreNotForwarded() {
        // /api 없이 다른 주소를 부르면 예전처럼 404, /api 주소는 그대로 인증 요구(401)
        assertThat(get("/categories").getStatusCode().value()).isEqualTo(404);
        assertThat(get("/common/healthz").getStatusCode().value()).isEqualTo(404);
        assertThat(get("/api/sessions").getStatusCode().value()).isEqualTo(401);
        assertThat(get("/api/categories").getStatusCode().value()).isEqualTo(200);
    }
}
