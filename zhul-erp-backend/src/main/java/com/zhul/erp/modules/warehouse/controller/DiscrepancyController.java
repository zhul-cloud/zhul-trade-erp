package com.zhul.erp.modules.warehouse.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.warehouse.dto.CountsVO;
import com.zhul.erp.modules.warehouse.dto.DiscrepancyPageQuery;
import com.zhul.erp.modules.warehouse.dto.DiscrepancyVO;
import com.zhul.erp.modules.warehouse.dto.EvidenceVO;
import com.zhul.erp.modules.warehouse.dto.HandleDiscrepancyRequest;
import com.zhul.erp.modules.warehouse.service.DiscrepancyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 到货差异（「供应商发货」的页签，按数据权限） */
@RestController
@RequestMapping("/api/v1/purchase/discrepancies")
@RequiredArgsConstructor
public class DiscrepancyController {

    private static final String MENU = "@perm.canAccessMenu('/purchase/shipments')";
    private static final String HANDLE = MENU + " and @perm.has('purchase:discrepancy:handle')";

    private final DiscrepancyService service;

    @PostMapping("/page")
    @PreAuthorize(MENU)
    public Result<PageResult<DiscrepancyVO>> page(@RequestBody DiscrepancyPageQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/counts")
    @PreAuthorize(MENU)
    public Result<CountsVO> counts() {
        CountsVO vo = new CountsVO();
        vo.setPending(service.countPending());
        return Result.ok(vo);
    }

    /** 验收说明与照片（采购员处理差异时看） */
    @GetMapping("/{id}/evidence")
    @PreAuthorize(MENU)
    public Result<EvidenceVO> evidence(@PathVariable Long id) {
        return Result.ok(service.evidence(id));
    }

    @PostMapping("/{id}/handle")
    @PreAuthorize(HANDLE)
    public Result<DiscrepancyVO> handle(@PathVariable Long id, @Valid @RequestBody HandleDiscrepancyRequest req) {
        return Result.ok(service.handle(id, req));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize(HANDLE)
    public Result<DiscrepancyVO> reopen(@PathVariable Long id) {
        return Result.ok(service.reopen(id));
    }
}
