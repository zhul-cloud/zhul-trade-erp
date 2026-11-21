package com.zhul.erp.modules.inventory.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.inventory.dto.InvMoveVO;
import com.zhul.erp.modules.inventory.dto.LedgerQuery;
import com.zhul.erp.modules.inventory.dto.LedgerRowVO;
import com.zhul.erp.modules.inventory.dto.MoveQuery;
import com.zhul.erp.modules.inventory.dto.StockBatchVO;
import com.zhul.erp.modules.inventory.dto.StockQuery;
import com.zhul.erp.modules.inventory.dto.StockRowVO;
import com.zhul.erp.modules.inventory.dto.StockSummaryVO;
import com.zhul.erp.modules.inventory.service.InventoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 库存查询、库存流水、库存台账（只读） */
@RestController
@RequestMapping("/api/v1/warehouse")
@RequiredArgsConstructor
public class InventoryQueryController {

    private final InventoryQueryService service;
    private final com.zhul.erp.modules.inventory.service.InventoryPeriods periods;

    @PostMapping("/stocks/page")
    @PreAuthorize("@perm.canAccessMenu('/warehouse/stocks')")
    public Result<PageResult<StockRowVO>> stocks(@RequestBody StockQuery q) {
        return Result.ok(service.stocks(q));
    }

    @PostMapping("/stocks/summary")
    @PreAuthorize("@perm.canAccessMenu('/warehouse/stocks')")
    public Result<StockSummaryVO> stockSummary(@RequestBody StockQuery q) {
        return Result.ok(service.stockSummary(q));
    }

    @GetMapping("/stocks/batches")
    @PreAuthorize("@perm.canAccessMenu('/warehouse/stocks')")
    public Result<List<StockBatchVO>> batches(@RequestParam Long warehouseId, @RequestParam Long productId) {
        return Result.ok(service.batches(warehouseId, productId));
    }

    @PostMapping("/moves/page")
    @PreAuthorize("@perm.canAccessMenu('/warehouse/moves')")
    public Result<PageResult<InvMoveVO>> moves(@RequestBody MoveQuery q) {
        return Result.ok(service.moves(q));
    }

    @PostMapping("/ledger/page")
    @PreAuthorize("@perm.canAccessMenu('/warehouse/ledger')")
    public Result<PageResult<LedgerRowVO>> ledger(@RequestBody LedgerQuery q) {
        return Result.ok(service.ledger(q));
    }

    /** 台账月份的结账状态（已结账时台账为结账快照，页面标「已结账」与结账人） */
    @GetMapping("/ledger/period")
    @PreAuthorize("@perm.canAccessMenu('/warehouse/ledger')")
    public Result<com.zhul.erp.modules.inventory.dto.PeriodVO> ledgerPeriod(@RequestParam(required = false) String month) {
        return Result.ok(periods.ledgerStatus(InventoryQueryService.month(month).toString()));
    }

    /** 台账导出：高资源操作，只导当前筛选（最多 5000 行） */
    @PostMapping("/ledger/export")
    @PreAuthorize("@perm.canAccessMenu('/warehouse/ledger')")
    public ResponseEntity<byte[]> exportLedger(@RequestBody LedgerQuery q) {
        String name = "库存台账-" + InventoryQueryService.month(q.getMonth()) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(service.exportLedger(q));
    }
}
