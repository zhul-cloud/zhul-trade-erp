package com.zhul.erp.modules.sales.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.sales.dto.SalesStatsQuery;
import com.zhul.erp.modules.sales.dto.SalesStatsVO;
import com.zhul.erp.modules.sales.service.SalesStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 销售统计（spec sales/sales-stats），只读，数据范围遵循销售订单的数据权限 */
@RestController
@RequiredArgsConstructor
public class SalesStatsController {

    private final SalesStatsService service;

    @GetMapping("/api/v1/sales/stats")
    @PreAuthorize("@perm.canAccessMenu('/sales/stats')")
    public Result<SalesStatsVO> stats(SalesStatsQuery q) {
        return Result.ok(service.stats(q));
    }
}
