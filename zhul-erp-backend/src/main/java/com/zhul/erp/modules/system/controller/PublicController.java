package com.zhul.erp.modules.system.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.dto.AppearanceVO;
import com.zhul.erp.modules.system.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 免鉴权的公开接口，供登录页等未登录场景使用。
 * 仅暴露明确可公开的字段，禁止复用 /api/v1/system/configs 等需要鉴权的通用接口。
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final ConfigService configService;

    @GetMapping("/appearance")
    public Result<AppearanceVO> appearance() {
        return Result.ok(configService.getPublicAppearance());
    }
}
