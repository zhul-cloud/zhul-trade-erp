package com.zhul.erp.modules.inquiry.sourcing.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.inquiry.sourcing.dto.SourcingStatsVO;
import com.zhul.erp.modules.inquiry.sourcing.service.impl.SourcingStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 询价统计（spec inquiry/sourcing-stats），只读 */
@RestController
@RequiredArgsConstructor
public class SourcingStatsController {

    private final SourcingStatsService service;

    @GetMapping("/api/v1/inquiry/sourcing-stats")
    @PreAuthorize("@perm.canAccessMenu('/inquiry/sourcing-stats')")
    public Result<SourcingStatsVO> stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return Result.ok(service.stats(dateFrom, dateTo));
    }
}
