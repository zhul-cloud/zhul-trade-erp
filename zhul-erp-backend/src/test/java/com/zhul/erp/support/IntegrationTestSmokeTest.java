package com.zhul.erp.support;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegrationTestSmokeTest extends IntegrationTestBase {

    @Autowired
    private StringRedisTemplate redis;

    @Test
    void connectsToTestDatabaseWithSeededSchema() {
        assertEquals(TEST_DB, jdbc.queryForObject("select database()", String.class));
        Integer tables = jdbc.queryForObject(
                "select count(*) from information_schema.tables where table_schema = ?", Integer.class, TEST_DB);
        assertNotNull(tables);
        assertTrue(tables >= 29, "测试库应包含 v1.0 + v1.1 的全部表，实际 " + tables);
    }

    @Test
    void connectsToIsolatedRedisDatabase() {
        LettuceConnectionFactory factory = (LettuceConnectionFactory) redis.getConnectionFactory();
        assertNotNull(factory);
        assertEquals(1, factory.getDatabase(), "测试应使用 Redis db 1");
        assertEquals("PONG", factory.getConnection().ping());
    }
}
