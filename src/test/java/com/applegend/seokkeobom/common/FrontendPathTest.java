package com.applegend.seokkeobom.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Tomcat에서: 백엔드 이미지에 넣은 프론트 빌드 결과(static/)가 서버 루트에서 열리는지.
 * dev 서버는 https://daelim.minq.live/team2/ 를 컨테이너의 / 로 넘긴다.
 * 테스트용 화면 파일은 src/test/resources/static/ 에 있다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class FrontendPathTest {
    @Autowired TestRestTemplate http;
    @LocalServerPort int port;

    /** TestRestTemplate은 상대 주소에 /api 를 붙이므로, 서버 루트 기준 전체 주소로 부른다 */
    private ResponseEntity<String> get(String path) {
        return http.getForEntity("http://localhost:" + port + path, String.class);
    }

    @Test
    void rootServesFrontendIndexWithoutLogin() {
        for (String path : new String[] {"/", "/index.html"}) {
            ResponseEntity<String> res = get(path);
            assertThat(res.getStatusCode().value()).as(path).isEqualTo(200);
            assertThat(res.getHeaders().getContentType()).as(path).isNotNull();
            assertThat(res.getHeaders().getContentType().isCompatibleWith(MediaType.TEXT_HTML)).as(path).isTrue();
            assertThat(res.getBody()).as(path).contains("treeplan-test-index");
        }
    }

    @Test
    void frontendAssetsAndFontsAreServedWithoutLogin() {
        ResponseEntity<String> script = get("/assets/app.js");
        assertThat(script.getStatusCode().value()).isEqualTo(200);
        assertThat(script.getBody()).contains("treeplan-test-asset");

        ResponseEntity<String> font = get("/fonts/test.woff2");
        assertThat(font.getStatusCode().value()).isEqualTo(200);
        assertThat(font.getBody()).contains("treeplan-test-font");
    }

    @Test
    void apiPathsKeepWorkingNextToTheFrontend() {
        assertThat(get("/api/categories").getStatusCode().value()).isEqualTo(200);
        assertThat(get("/api/sessions").getStatusCode().value()).isEqualTo(401);
        // 화면 파일이 아닌 루트 주소는 예전처럼 404
        assertThat(get("/categories").getStatusCode().value()).isEqualTo(404);
        assertThat(get("/assets/missing.js").getStatusCode().value()).isEqualTo(404);
    }
}
