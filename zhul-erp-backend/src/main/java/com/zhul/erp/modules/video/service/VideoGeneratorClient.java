package com.zhul.erp.modules.video.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.List;

/**
 * 视频生成服务（scripts/video-generator）的客户端：一次提交，结果由生成服务回调（design 决策 1、2）。
 * 生成服务同一时间只接一个任务，忙时返回 409。
 */
@Slf4j
@Component
public class VideoGeneratorClient {

    public static final String TOKEN_HEADER = "X-Video-Token";

    public enum Outcome { ACCEPTED, BUSY, UNAVAILABLE }

    public record Submit(Outcome outcome, String message) {
    }

    /** 提交给生成服务的内容：图片由生成服务按 url 带令牌下载；镜头按顺序，image 为 images 的下标 */
    public record Request(Long taskId, int version, String title, List<Image> images, List<Shot> shots,
                          List<String> aspects, String voice, double voiceRate, String callbackUrl) {
    }

    public record Image(Long id, String url) {
    }

    public record Shot(int image, String caption, double seconds) {
    }

    /** 固定 HTTP/1.1：默认 HTTP/2 对明文地址会先发 h2c 升级，uvicorn 处理升级请求时读不到请求体 */
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String token;

    public VideoGeneratorClient(ObjectMapper objectMapper,
                                @Value("${zhul.video.generator-url:}") String baseUrl,
                                @Value("${zhul.video.callback-token}") String token) {
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        this.token = token;
    }

    /** 没配置生成服务地址时功能关闭，队列不派发 */
    public boolean enabled() {
        return StringUtils.hasText(baseUrl);
    }

    public Submit submit(Request req) {
        try {
            HttpRequest r = HttpRequest.newBuilder(URI.create(baseUrl + "/generate"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header(TOKEN_HEADER, token)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(req)))
                    .build();
            HttpResponse<String> resp = http.send(r, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 == 2) {
                return new Submit(Outcome.ACCEPTED, "");
            }
            if (resp.statusCode() == 409) {
                return new Submit(Outcome.BUSY, "生成服务正忙");
            }
            return new Submit(Outcome.UNAVAILABLE, "生成服务返回 " + resp.statusCode());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("视频生成请求序列化失败", e);
        } catch (IOException e) {
            log.warn("视频生成服务不可用：{}", e.toString());
            return new Submit(Outcome.UNAVAILABLE, "生成服务不可用");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Submit(Outcome.UNAVAILABLE, "提交被中断");
        }
    }

    public boolean tokenMatches(String provided) {
        // 令牌没配置（空值）时一律拒绝，不能让空令牌的请求通过
        return StringUtils.hasText(token) && token.equals(provided);
    }
}
