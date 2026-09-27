package com.zhul.erp.framework.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * 供模块内对外发起 HTTP 调用复用的单例客户端（当前用于 AI 编排服务契约，见
 * com.zhul.erp.modules.aitask.service.impl.AiOrchestratorClientImpl）。
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }
}
