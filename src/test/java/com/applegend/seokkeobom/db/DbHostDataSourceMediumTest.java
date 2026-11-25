package com.applegend.seokkeobom.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Medium: test 프로필 없이 배포 서버와 같은 방식(DB_HOST 환경변수)으로 앱을 띄우고,
 * 실제로 그 DB(메모리 H2, MySQL 모드)에 연결됐는지 확인한다.
 */
@Tag("medium")
@DisplayName("[Medium] DB_HOST 로 앱 연결")
@SpringBootTest(properties = {
    "DB_HOST=jdbc:h2:mem:dbhost;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "DB_USERNAME=sa",
    "DB_PASSWORD=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    // 잘못된 DB 로 붙으려 할 때 오래 기다리지 않게 한다
    "spring.datasource.hikari.connection-timeout=3000",
})
class DbHostDataSourceMediumTest {

    @Autowired
    DataSource dataSource;

    @Test
    @DisplayName("DB_HOST 로 준 주소의 DB 에 실제로 연결된다")
    void connectsToDbHost() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:dbhost");
            assertThat(connection.isValid(2)).isTrue();
        }
    }
}
