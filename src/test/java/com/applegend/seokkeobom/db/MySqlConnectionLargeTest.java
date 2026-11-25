package com.applegend.seokkeobom.db;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Large: 운영과 같은 MySQL 8 을 Docker 로 띄우고, 실제 포트로 앱을 띄워서
 * 배포 서버와 같은 방식(DB_HOST·DB_USERNAME·DB_PASSWORD)으로 연결·저장까지 확인한다.
 * Docker 가 없는 PC 에서는 건너뛴다 (GitHub Actions 러너에는 Docker 가 있다).
 */
@Tag("large")
@DisplayName("[Large] 운영 MySQL 연결")
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class MySqlConnectionLargeTest {

    private static final String DATABASE = "idea_support";
    private static final String USER = "team2";
    private static final String PASSWORD = "team2-password";

    /**
     * MySQLContainer 는 준비 확인에 테스트 쪽 MySQL 드라이버를 써서, 앱에 드라이버가 없는지를 가려 버린다.
     * 그래서 일반 컨테이너로 띄우고 MySQL 의 "ready for connections" 로그로 준비를 확인한다.
     */
    @Container
    static final GenericContainer<?> MYSQL = new GenericContainer<>("mysql:8.0")
            .withEnv("MYSQL_DATABASE", DATABASE)
            .withEnv("MYSQL_USER", USER)
            .withEnv("MYSQL_PASSWORD", PASSWORD)
            .withEnv("MYSQL_ROOT_PASSWORD", PASSWORD)
            .withExposedPorts(3306)
            .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1)
                    .withStartupTimeout(Duration.ofMinutes(3)));

    /** 교수님 예시 매니페스트(team/spring)와 같은 이름·형식으로 넣는다 */
    @DynamicPropertySource
    static void productionLikeEnv(DynamicPropertyRegistry registry) {
        registry.add("DB_HOST", () -> "jdbc:mysql://" + MYSQL.getHost() + ":" + MYSQL.getMappedPort(3306)
                + "/" + DATABASE + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true");
        registry.add("DB_USERNAME", () -> USER);
        registry.add("DB_PASSWORD", () -> PASSWORD);
        registry.add("spring.datasource.hikari.connection-timeout", () -> "5000");
    }

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate http;

    /** 배포 서버가 부르는 그대로의 주소 (TestRestTemplate 이 컨텍스트 경로를 덧붙이지 않게 전체 주소로 쓴다) */
    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("앱이 붙은 DB 가 정말 MySQL 8 이다")
    void connectedDatabaseIsMySql8() {
        String version = jdbc.queryForObject("select version()", String.class);

        assertThat(version).startsWith("8.");
    }

    @Test
    @DisplayName("배포 서버처럼 실제 포트로 GET /api/common/health 를 부르면 200, database=up")
    void healthCheckOnRealPortReportsUp() {
        ResponseEntity<JsonNode> res = http.getForEntity(url("/api/common/health"), JsonNode.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().path("status").asText()).isEqualTo("ok");
        assertThat(res.getBody().path("database").asText()).isEqualTo("up");
    }

    @Test
    @DisplayName("POST /api/auth/guest 로 만든 사용자가 MySQL users 테이블에 저장된다")
    void guestSignupIsStoredInMySql() {
        Integer before = jdbc.queryForObject("select count(*) from users", Integer.class);

        ResponseEntity<JsonNode> res = http.postForEntity(url("/api/auth/guest"), null, JsonNode.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(res.getBody().path("accessToken").asText()).isNotBlank();
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isEqualTo(before + 1);
    }
}
