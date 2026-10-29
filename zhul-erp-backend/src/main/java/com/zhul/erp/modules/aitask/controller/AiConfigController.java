package com.zhul.erp.modules.aitask.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.aitask.dto.AiConfigVO;
import com.zhul.erp.modules.aitask.dto.AiConnectionTestVO;
import com.zhul.erp.modules.aitask.dto.AiModelListVO;
import com.zhul.erp.modules.aitask.dto.AiSubscriptionStatusVO;
import com.zhul.erp.modules.aitask.dto.SaveAiConfigRequest;
import com.zhul.erp.modules.aitask.service.AiConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 业务设置 → AI 设置：当前租户的 AI 编排配置 */
@RestController
@RequestMapping("/api/v1/system/ai-config")
@RequiredArgsConstructor
public class AiConfigController {

    private static final String MENU = "@perm.canAccessMenu('/system/ai-settings')";
    private static final String EDIT = MENU + " and @perm.has('system:ai-config:edit')";

    private final AiConfigService aiConfigService;

    @GetMapping
    @PreAuthorize(MENU)
    public Result<AiConfigVO> get() {
        return Result.ok(aiConfigService.get());
    }

    @PreAuthorize(EDIT)
    @PutMapping
    public Result<Void> save(@Valid @RequestBody SaveAiConfigRequest req) {
        aiConfigService.save(req);
        return Result.ok();
    }

    @PreAuthorize(EDIT)
    @PostMapping("/test")
    public Result<AiConnectionTestVO> test(@Valid @RequestBody SaveAiConfigRequest req) {
        return Result.ok(aiConfigService.test(req));
    }

    @PreAuthorize(EDIT)
    @PostMapping("/models")
    public Result<AiModelListVO> models(@Valid @RequestBody SaveAiConfigRequest req) {
        return Result.ok(aiConfigService.models(req));
    }

    @PreAuthorize(MENU)
    @GetMapping("/subscription-status")
    public Result<AiSubscriptionStatusVO> subscriptionStatus() {
        return Result.ok(aiConfigService.subscriptionStatus());
    }
}
