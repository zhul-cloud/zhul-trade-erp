package com.zhul.erp.modules.system.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.dto.ExchangeRateLogVO;
import com.zhul.erp.modules.system.dto.ExchangeRateVO;
import com.zhul.erp.modules.system.dto.SaveExchangeRateRequest;
import com.zhul.erp.modules.system.service.ExchangeRateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 系统汇率：能访问「汇率设置」菜单可查看，修改需要「修改汇率」按钮权限 */
@RestController
@RequestMapping("/api/v1/system/exchange-rates")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/system/exchange-rate')")
public class ExchangeRateController {

    private final ExchangeRateService service;

    @GetMapping
    public Result<List<ExchangeRateVO>> list() {
        return Result.ok(service.list());
    }

    @PutMapping("/{currencyCode}")
    @PreAuthorize("@perm.has('system:exchange-rate:edit')")
    public Result<ExchangeRateVO> save(@PathVariable String currencyCode, @Valid @RequestBody SaveExchangeRateRequest req) {
        return Result.ok(service.save(currencyCode, req.getRate()));
    }

    @GetMapping("/{currencyCode}/logs")
    public Result<List<ExchangeRateLogVO>> logs(@PathVariable String currencyCode) {
        return Result.ok(service.logs(currencyCode));
    }
}
