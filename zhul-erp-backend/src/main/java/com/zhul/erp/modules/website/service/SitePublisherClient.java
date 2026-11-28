package com.zhul.erp.modules.website.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 独立站发布服务（scripts/site-publisher）的客户端（design 第 1 条）。请求体里带解密后的凭据，只在内存中，
 * 经 Compose 内网传给发布服务，不记录请求体；日志只记录接口和状态码。
 */
@Slf4j
@Component
public class SitePublisherClient {

    public static final String TOKEN_HEADER = "X-Publisher-Token";

    /** 发布服务返回：status 为 HTTP 状态码，body 为解析后的 JSON（失败时为 null） */
    public record Reply(int status, JsonNode body) {
        public String detail() {
            return body == null ? "" : body.path("detail").asText("");
        }
    }

    /** 固定 HTTP/1.1，与视频生成服务客户端一致，避免明文地址的 h2c 升级 */
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String token;

    public SitePublisherClient(ObjectMapper objectMapper,
                               @Value("${zhul.website.publisher-url:}") String baseUrl,
                               @Value("${zhul.website.publisher-token:}") String token) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.token = token == null ? "" : token;
    }

    public boolean enabled() {
        return StringUtils.hasText(baseUrl) && StringUtils.hasText(token);
    }

    /** 检测连接：同步执行，发布服务逐项检查，最多几分钟（拉取仓库） */
    public Reply check(Map<String, Object> body) {
        return post("/check", body, Duration.ofMinutes(4));
    }

    /** 提交导入、比对、发布任务：发布服务立即返回 202，同一站点有任务时返回 409 */
    public Reply submitJob(Map<String, Object> body) {
        return post("/jobs", body, Duration.ofSeconds(30));
    }

    public boolean tokenMatches(String provided) {
        // 令牌没配置（空值）时一律拒绝
        return StringUtils.hasText(token) && token.equals(provided);
    }

    private Reply post(String path, Map<String, Object> body, Duration timeout) {
        if (!enabled()) {
            throw new BizException("还没有配置独立站发布服务，请联系管理员");
        }
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header(TOKEN_HEADER, token)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("发布服务 {} 返回 {}", path, resp.statusCode());
            }
            return new Reply(resp.statusCode(), parse(resp.body()));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("发布服务请求序列化失败", e);
        } catch (IOException e) {
            log.warn("发布服务不可用：{} {}", path, e.getClass().getSimpleName());
            throw new BizException("发布服务暂时连不上，请稍后再试", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("请求被中断，请重试", e);
        }
    }

    private JsonNode parse(String text) {
        try {
            return StringUtils.hasText(text) ? objectMapper.readTree(text) : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
