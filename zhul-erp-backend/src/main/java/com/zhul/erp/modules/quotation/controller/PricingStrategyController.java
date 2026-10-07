package com.zhul.erp.modules.quotation.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.quotation.dto.PricingStrategyVO;
import com.zhul.erp.modules.quotation.dto.SavePricingStrategyRequest;
import com.zhul.erp.modules.quotation.service.PricingStrategyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 定价策略：能访问「定价策略」菜单可查看，修改需要「编辑定价策略」按钮权限 */
@RestController
@RequestMapping("/api/v1/quotation/pricing-strategy")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/quotation/pricing')")
public class PricingStrategyController {

    private final PricingStrategyService service;

    @GetMapping
    public Result<PricingStrategyVO> get() {
        return Result.ok(service.get());
    }

    @PutMapping
    @PreAuthorize("@perm.has('quotation:pricing:edit')")
    public Result<PricingStrategyVO> save(@Valid @RequestBody SavePricingStrategyRequest req) {
        return Result.ok(service.save(req));
    }
}
