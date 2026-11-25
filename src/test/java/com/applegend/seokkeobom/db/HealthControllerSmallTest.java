package com.applegend.seokkeobom.db;

import com.applegend.seokkeobom.common.health.HealthController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/** Small: 스프링·DB 없이 가짜 DataSource 로 헬스체크가 DB 연결 상태를 어떻게 답하는지 확인한다. */
@Tag("small")
@DisplayName("[Small] 헬스체크의 DB 연결 판단")
class HealthControllerSmallTest {

    private static DataSource dataSourceAnswering(boolean valid) throws SQLException {
        Connection connection = mock(Connection.class);
        given(connection.isValid(anyInt())).willReturn(valid);
        DataSource dataSource = mock(DataSource.class);
        given(dataSource.getConnection()).willReturn(connection);
        return dataSource;
    }

    @Test
    @DisplayName("DB 연결이 살아 있으면 200, status=ok, database=up")
    void upWhenConnectionIsValid() throws SQLException {
        ResponseEntity<Map<String, String>> res = new HealthController(dataSourceAnswering(true)).health();

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody()).containsEntry("status", "ok").containsEntry("database", "up");
    }

    @Test
    @DisplayName("연결은 됐지만 유효하지 않으면 503, database=down")
    void downWhenConnectionIsInvalid() throws SQLException {
        ResponseEntity<Map<String, String>> res = new HealthController(dataSourceAnswering(false)).health();

        assertThat(res.getStatusCode().value()).isEqualTo(503);
        assertThat(res.getBody()).containsEntry("status", "error").containsEntry("database", "down");
    }

    @Test
    @DisplayName("DB 에 아예 못 붙으면 예외를 밖으로 던지지 않고 503, database=down")
    void downWhenConnectionFails() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        given(dataSource.getConnection()).willThrow(new SQLException("connection refused"));

        ResponseEntity<Map<String, String>> res = new HealthController(dataSource).health();

        assertThat(res.getStatusCode().value()).isEqualTo(503);
        assertThat(res.getBody()).containsEntry("database", "down");
    }
}
