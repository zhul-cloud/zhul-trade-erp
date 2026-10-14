package com.zhul.erp.modules.purchase.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.purchase.dto.BargainStatsQuery;
import com.zhul.erp.modules.purchase.dto.BargainStatsVO;
import com.zhul.erp.modules.purchase.service.BargainStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 砍价统计（spec purchase/bargain-stats），只读 */
@RestController
@RequiredArgsConstructor
public class BargainStatsController {

    private final BargainStatsService service;

    @GetMapping("/api/v1/purchase/bargain-stats")
    @PreAuthorize("@perm.canAccessMenu('/purchase/bargain-stats')")
    public Result<BargainStatsVO> stats(BargainStatsQuery q) {
        return Result.ok(service.stats(q));
    }
}
