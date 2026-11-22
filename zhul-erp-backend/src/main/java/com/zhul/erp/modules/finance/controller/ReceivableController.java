package com.zhul.erp.modules.finance.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.finance.dto.ReceivableVO;
import com.zhul.erp.modules.finance.service.ReceivableService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 应收管理（spec finance/accounts-receivable），只读 */
@RestController
@RequiredArgsConstructor
public class ReceivableController {

    private final ReceivableService service;

    @GetMapping("/api/v1/finance/receivables")
    @PreAuthorize("@perm.canAccessMenu('/finance/receivables')")
    public Result<ReceivableVO> overview() {
        return Result.ok(service.overview());
    }
}
