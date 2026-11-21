package com.zhul.erp.modules.inventory.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.aftersales.dto.ReasonBody;
import com.zhul.erp.modules.inventory.dto.LedgerQuery;
import com.zhul.erp.modules.inventory.dto.LedgerRowVO;
import com.zhul.erp.modules.inventory.dto.PeriodCogsVO;
import com.zhul.erp.modules.inventory.dto.PeriodVO;
import com.zhul.erp.modules.inventory.service.InventoryPeriodService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 库存月结（spec finance/period-close）：「财务管理 → 库存月结」 */
@RestController
@RequestMapping("/api/v1/finance/periods")
@RequiredArgsConstructor
public class InventoryPeriodController {

    private static final String VIEW = "@perm.canAccessMenu('/finance/periods')";
    private static final String CLOSE = VIEW + " and @perm.has('inventory:period:close')";
    private static final String REOPEN = VIEW + " and @perm.has('inventory:period:reopen')";

    private final InventoryPeriodService service;

    @GetMapping
    @PreAuthorize(VIEW)
    public Result<List<PeriodVO>> list() {
        return Result.ok(service.list());
    }

    @GetMapping("/{month}")
    @PreAuthorize(VIEW)
    public Result<PeriodVO> detail(@PathVariable String month) {
        return Result.ok(service.detail(month));
    }

    @PostMapping("/{month}/ledger")
    @PreAuthorize(VIEW)
    public Result<PageResult<LedgerRowVO>> ledger(@PathVariable String month, @RequestBody LedgerQuery q) {
        return Result.ok(service.ledger(month, q));
    }

    /** 月份的类目账（全部仓库，按一级品类汇总；已结账读快照） */
    @GetMapping("/{month}/category-ledger")
    @PreAuthorize(VIEW)
    public Result<com.zhul.erp.modules.inventory.dto.CategoryLedgerVO> categoryLedger(@PathVariable String month) {
        return Result.ok(service.categoryLedger(month, null));
    }

    @GetMapping("/{month}/cogs")
    @PreAuthorize(VIEW)
    public Result<PageResult<PeriodCogsVO>> cogs(@PathVariable String month, @RequestParam(required = false) String keyword,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(required = false) Integer pageSize) {
        return Result.ok(service.cogs(month, keyword, page, pageSize == null ? null : Math.min(pageSize, 100)));
    }

    /** 导出：库存台账与销售成本两张表 */
    @PostMapping("/{month}/export")
    @PreAuthorize(VIEW)
    public org.springframework.http.ResponseEntity<byte[]> export(@PathVariable String month) {
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, org.springframework.http.ContentDisposition.attachment()
                        .filename("库存月结-" + month + ".xlsx", java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .contentType(org.springframework.http.MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.export(month));
    }

    @PostMapping("/{month}/close")
    @PreAuthorize(CLOSE)
    public Result<PeriodVO> close(@PathVariable String month) {
        return Result.ok(service.close(month));
    }

    @PostMapping("/{month}/reopen")
    @PreAuthorize(REOPEN)
    public Result<PeriodVO> reopen(@PathVariable String month, @Valid @RequestBody ReasonBody req) {
        return Result.ok(service.reopen(month, req.getReason()));
    }
}
