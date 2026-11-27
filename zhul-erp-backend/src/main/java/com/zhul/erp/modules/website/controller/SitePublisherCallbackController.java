package com.zhul.erp.modules.website.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.common.result.ResultCode;
import com.zhul.erp.modules.website.service.SitePublisherClient;
import com.zhul.erp.modules.website.service.SiteReleaseCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 独立站发布服务的回调：不走用户态 JWT（已加入 SecurityConfig 白名单），用共享令牌 X-Publisher-Token 校验，
 * 不一致直接 401。回调里不记录请求体（可能含页面输出）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/website-publisher")
@RequiredArgsConstructor
public class SitePublisherCallbackController {

    private final SitePublisherClient client;
    private final SiteReleaseCallback releases;

    @PostMapping("/callback")
    public ResponseEntity<Result<Void>> callback(@RequestHeader(value = SitePublisherClient.TOKEN_HEADER, required = false) String token,
                                                 @RequestBody JsonNode body) {
        if (!client.tokenMatches(token)) {
            log.warn("独立站发布服务回调鉴权失败");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.fail(ResultCode.UNAUTHORIZED));
        }
        if (!releases.handle(body)) {
            log.warn("没有找到回调对应的任务，jobId={}", body.path("jobId").asText(""));
        }
        return ResponseEntity.ok(Result.ok());
    }
}
