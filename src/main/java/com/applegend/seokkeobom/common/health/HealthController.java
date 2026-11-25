package com.applegend.seokkeobom.common.health;

import java.sql.Connection;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 배포 워크플로가 호출하는 헬스 체크. DB에 연결되면 200, 아니면 503. */
@RestController
public class HealthController {
    private final DataSource dataSource;

    public HealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/common/health")
    public ResponseEntity<Map<String, String>> health() {
        boolean dbUp;
        try (Connection conn = dataSource.getConnection()) {
            dbUp = conn.isValid(2);
        } catch (Exception e) {
            dbUp = false;
        }
        if (dbUp) {
            return ResponseEntity.ok(Map.of("status", "ok", "database", "up"));
        }
        return ResponseEntity.status(503).body(Map.of("status", "error", "database", "down"));
    }
}
