package com.zhul.erp.modules.aitask.support;

import com.zhul.erp.modules.aitask.constants.AiConfigConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

/**
 * 连接方式失败后的暂停状态（openspec ai-connection-failover「失败后暂停与恢复」）：暂停 10 分钟，到期由 Redis TTL 自动恢复。
 * 本公司 API Key 的暂停记在本租户；平台订阅是同一个登录，暂停记在平台（租户 0），对所有使用它的租户生效。
 * 暂停只是提示性的短期状态：Redis 不可用时视为没有暂停，最多是新任务多试一次已坏的方式，再由执行中的切换兜住。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiConnectionPauses {

    public static final Duration PAUSE = Duration.ofMinutes(10);
    /** 原因分类码，与编排服务一致；其他值不记 */
    public static final Set<String> REASONS = Set.of("auth", "quota", "rate_limit", "provider_error", "timeout",
            "not_logged_in", "usage_limit");
    private static final String PREFIX = "zhul:erp:ai:pause:";

    private final StringRedisTemplate redis;

    public record Pause(String reason, LocalDateTime until) {
    }

    /** 记一次暂停：at 为失败发生的时间，暂停到 at + 10 分钟；已过期的（如很晚才到的回调）不记 */
    public void pause(int tenantId, String mode, String provider, String reason, Instant at) {
        if (!REASONS.contains(reason)) {
            log.warn("忽略不认识的切换原因，mode={}, provider={}, reason={}", mode, provider, reason);
            return;
        }
        Instant until = (at == null ? Instant.now() : at).plus(PAUSE);
        Duration left = Duration.between(Instant.now(), until);
        if (left.isNegative() || left.isZero()) {
            return;
        }
        try {
            redis.opsForValue().set(key(tenantId, mode, provider), reason + "|" + until.getEpochSecond(), left);
            log.info("AI 连接方式暂停，tenantId={}, mode={}, provider={}, reason={}, until={}",
                    owner(tenantId, mode), mode, provider, reason, toLocal(until));
        } catch (DataAccessException e) {
            log.warn("记录 AI 连接方式暂停失败：{}", e.getMessage());
        }
    }

    /** 当前暂停；没有暂停或 Redis 不可用时为 null */
    public Pause get(int tenantId, String mode, String provider) {
        String v;
        try {
            v = redis.opsForValue().get(key(tenantId, mode, provider));
        } catch (DataAccessException e) {
            log.warn("读取 AI 连接方式暂停失败：{}", e.getMessage());
            return null;
        }
        if (v == null) {
            return null;
        }
        int i = v.lastIndexOf('|');
        try {
            long until = Long.parseLong(v.substring(i + 1));
            return new Pause(v.substring(0, i), toLocal(Instant.ofEpochSecond(until)));
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            log.warn("AI 连接方式暂停记录格式不对，忽略：{}", v);
            return null;
        }
    }

    /** 解除暂停：保存这个方式的设置（如更换 Key）或测试连接成功时 */
    public void clear(int tenantId, String mode, String provider) {
        try {
            redis.delete(key(tenantId, mode, provider));
        } catch (DataAccessException e) {
            log.warn("解除 AI 连接方式暂停失败：{}", e.getMessage());
        }
    }

    /** 北京时间（容器时区为 Asia/Shanghai） */
    private static LocalDateTime toLocal(Instant t) {
        return LocalDateTime.ofInstant(t, ZoneId.systemDefault());
    }

    /** 暂停记在谁名下：平台订阅记在平台 */
    static int owner(int tenantId, String mode) {
        return AiConfigConstants.MODE_SUBSCRIPTION.equals(mode) ? AiConfigConstants.PLATFORM_TENANT_ID : tenantId;
    }

    static String key(int tenantId, String mode, String provider) {
        return PREFIX + owner(tenantId, mode) + ":" + mode + ":" + provider;
    }
}
