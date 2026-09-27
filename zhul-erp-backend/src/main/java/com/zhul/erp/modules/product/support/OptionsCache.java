package com.zhul.erp.modules.product.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.product.constants.ProductConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;

/**
 * 品牌、品类选项列表缓存（Cache-Aside）：先读缓存，未命中读库再写缓存；
 * 写库时**事务提交后**才删缓存（design.md 决策 9），事务回滚则缓存保持不变。
 * Redis 不可用时降级为直接读库，不影响主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OptionsCache {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    /** 命中返回列表（可能为空列表），未命中或 Redis 异常返回 null */
    public <T> List<T> get(String key, Class<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null) {
                return null;
            }
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (Exception e) {
            log.warn("读取选项缓存失败，降级读库，key={}", key, e);
            return null;
        }
    }

    public void put(String key, List<?> value) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value),
                    Duration.ofSeconds(ProductConstants.OPTIONS_CACHE_TTL_SECONDS));
        } catch (Exception e) {
            log.warn("写入选项缓存失败，key={}", key, e);
        }
    }

    /** 事务提交后删除缓存；当前没有事务则立即删除 */
    public void evictAfterCommit(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict(key);
                }
            });
        } else {
            evict(key);
        }
    }

    private void evict(String key) {
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("删除选项缓存失败，最长 {}s 后自然过期，key={}", ProductConstants.OPTIONS_CACHE_TTL_SECONDS, key, e);
        }
    }
}
