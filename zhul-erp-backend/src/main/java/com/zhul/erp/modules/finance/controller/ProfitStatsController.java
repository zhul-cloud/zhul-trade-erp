package com.zhul.erp.modules.finance.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.finance.dto.ProfitStatsQuery;
import com.zhul.erp.modules.finance.dto.ProfitStatsVO;
import com.zhul.erp.modules.finance.service.ProfitStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 利润统计（spec finance/profit-stats），只读 */
@RestController
@RequiredArgsConstructor
public class ProfitStatsController {

    private final ProfitStatsService service;

    @GetMapping("/api/v1/finance/profit-stats")
    @PreAuthorize("@perm.canAccessMenu('/finance/profit-stats')")
    public Result<ProfitStatsVO> stats(ProfitStatsQuery q) {
        return Result.ok(service.stats(q));
    }
}
