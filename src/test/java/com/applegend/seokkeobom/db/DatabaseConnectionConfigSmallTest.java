package com.applegend.seokkeobom.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Small: 스프링·DB 없이 application.yml 만 읽어서, 배포 서버가 넣어 주는 환경변수로
 * 어느 DB 주소에 붙는지 확인한다.
 * 배포 서버(교수님 예시 매니페스트)는 DB_HOST=jdbc:mysql://..., DB_USERNAME, DB_PASSWORD 를 넣는다.
 */
@Tag("small")
@DisplayName("[Small] DB 연결 설정")
class DatabaseConnectionConfigSmallTest {
    private static final String MYSQL_URL = "jdbc:mysql://mysql.team2.svc.cluster.local:3306/idea_support";
    private static final String POSTGRES_URL = "jdbc:postgresql://db.example:5432/seokkeobom";

    /** 실제 OS 환경변수는 빼고, 주어진 값만 환경변수처럼 넣은 환경 */
    private static StandardEnvironment envWith(Map<String, Object> vars) throws IOException {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        env.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        env.getPropertySources().addFirst(new MapPropertySource("env", vars));
        for (PropertySource<?> yaml : new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"))) {
            env.getPropertySources().addLast(yaml);
        }
        return env;
    }

    @Test
    @DisplayName("배포 서버처럼 DB_HOST 만 주면 그 주소로 붙는다")
    void usesDbHostWhenOnlyDbHostIsGiven() throws IOException {
        StandardEnvironment env = envWith(Map.of("DB_HOST", MYSQL_URL));

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo(MYSQL_URL);
    }

    @Test
    @DisplayName("DB_URL 과 DB_HOST 가 둘 다 있으면 DB_URL 이 먼저다")
    void dbUrlWinsOverDbHost() throws IOException {
        StandardEnvironment env = envWith(Map.of("DB_URL", POSTGRES_URL, "DB_HOST", MYSQL_URL));

        assertThat(env.getProperty("spring.datasource.url")).isEqualTo(POSTGRES_URL);
    }

    @Test
    @DisplayName("아무것도 안 주면 로컬 PostgreSQL 기본값으로 붙는다")
    void fallsBackToLocalPostgres() throws IOException {
        StandardEnvironment env = envWith(Map.of());

        assertThat(env.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:postgresql://localhost:5433/seokkeobom");
    }

    @Test
    @DisplayName("DB_USERNAME·DB_PASSWORD 를 그대로 계정으로 쓴다")
    void usesGivenCredentials() throws IOException {
        StandardEnvironment env = envWith(Map.of("DB_USERNAME", "team2", "DB_PASSWORD", "secret"));

        assertThat(env.getProperty("spring.datasource.username")).isEqualTo("team2");
        assertThat(env.getProperty("spring.datasource.password")).isEqualTo("secret");
    }

    @Test
    @DisplayName("운영 DB(MySQL 8) 드라이버가 들어 있다")
    void mysqlDriverIsOnClasspath() {
        assertThatCode(() -> Class.forName("com.mysql.cj.jdbc.Driver")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("로컬·CI DB(PostgreSQL) 드라이버도 계속 들어 있다")
    void postgresDriverIsOnClasspath() {
        assertThatCode(() -> Class.forName("org.postgresql.Driver")).doesNotThrowAnyException();
    }
}
