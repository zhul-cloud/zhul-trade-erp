package com.zhul.erp.modules.finance.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.finance.dto.OperatingReportVO;
import com.zhul.erp.modules.finance.service.OperatingReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 经营统计（spec finance/operating-report），只读 */
@RestController
@RequiredArgsConstructor
public class OperatingReportController {

    private final OperatingReportService service;

    @GetMapping("/api/v1/finance/operating-report")
    @PreAuthorize("@perm.canAccessMenu('/finance/operating-report')")
    public Result<OperatingReportVO> year(@RequestParam(required = false) Integer year) {
        return Result.ok(service.year(year));
    }
}
