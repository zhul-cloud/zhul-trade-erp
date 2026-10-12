package com.zhul.erp.framework.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

/** Container readiness: never expose connection details or credentials. */
@Profile("docker")
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final DataSource dataSource;
    private final RedisConnectionFactory redisConnectionFactory;
    private final ApplicationAvailability availability;

    @GetMapping("/api/v1/public/health")
    public ResponseEntity<Map<String, String>> health() {
        if (availability.getReadinessState() != ReadinessState.ACCEPTING_TRAFFIC) {
            return down();
        }
        try (Connection database = dataSource.getConnection();
             RedisConnection redis = redisConnectionFactory.getConnection()) {
            if (database.isValid(2) && "PONG".equals(redis.ping())) {
                return ResponseEntity.ok(Map.of("status", "UP"));
            }
        } catch (Exception ignored) {
            // Detailed datasource/Redis failures belong in application logs, not this public response.
        }
        return down();
    }

    private ResponseEntity<Map<String, String>> down() {
        return ResponseEntity.status(503).body(Map.of("status", "DOWN"));
    }
}
