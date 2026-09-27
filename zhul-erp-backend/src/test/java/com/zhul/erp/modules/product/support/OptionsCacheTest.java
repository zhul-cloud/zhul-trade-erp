package com.zhul.erp.modules.product.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.product.dto.BrandOptionVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Redis 出问题时降级为直接读库，不影响主流程；写入后的缓存删除只在事务提交后执行。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OptionsCacheTest {

    @Mock
    private StringRedisTemplate redis;
    @Mock
    private ValueOperations<String, String> ops;

    private OptionsCache cache;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(ops);
        cache = new OptionsCache(redis, new ObjectMapper());
    }

    @Test
    void hitReturnsParsedList() {
        when(ops.get("k")).thenReturn("[{\"id\":1,\"brandName\":\"ABB\"}]");

        List<BrandOptionVO> result = cache.get("k", BrandOptionVO.class);

        assertEquals(1, result.size());
        assertEquals("ABB", result.get(0).getBrandName());
    }

    @Test
    void missReturnsNullAndEmptyListIsAValidHit() {
        when(ops.get("miss")).thenReturn(null);
        when(ops.get("empty")).thenReturn("[]");

        assertNull(cache.get("miss", BrandOptionVO.class));
        assertTrue(cache.get("empty", BrandOptionVO.class).isEmpty());
    }

    @Test
    void redisFailureOrCorruptedValueFallsBackToDatabase() {
        when(ops.get("down")).thenThrow(new RedisConnectionFailureException("down"));
        when(ops.get("bad")).thenReturn("not json");

        assertNull(cache.get("down", BrandOptionVO.class));
        assertNull(cache.get("bad", BrandOptionVO.class));
    }

    @Test
    void putWritesWithFiveMinuteTtlAndSwallowsFailures() {
        cache.put("k", List.of());
        verify(ops).set(eq("k"), eq("[]"), eq(Duration.ofSeconds(300)));

        org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down")).when(ops).set(anyString(), anyString(), any(Duration.class));
        cache.put("k2", List.of());   // 不抛异常
    }

    @Test
    void evictWithoutTransactionDeletesImmediatelyAndSwallowsFailures() {
        cache.evictAfterCommit("k");
        verify(redis).delete("k");

        when(redis.delete("boom")).thenThrow(new RedisConnectionFailureException("down"));
        cache.evictAfterCommit("boom");   // 不抛异常
    }

    @Test
    void evictInsideTransactionWaitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            cache.evictAfterCommit("k");
            verify(redis, never()).delete("k");

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(redis).delete("k");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
