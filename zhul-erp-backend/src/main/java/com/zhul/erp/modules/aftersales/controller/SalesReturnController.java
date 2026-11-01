package com.zhul.erp.modules.aftersales.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.aftersales.dto.DisposeReturnRequest;
import com.zhul.erp.modules.aftersales.dto.ReasonBody;
import com.zhul.erp.modules.aftersales.dto.ReceiveReturnRequest;
import com.zhul.erp.modules.aftersales.dto.ReturnQuery;
import com.zhul.erp.modules.aftersales.dto.ReturnVO;
import com.zhul.erp.modules.aftersales.dto.SaveReturnRequest;
import com.zhul.erp.modules.aftersales.service.SalesReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 退货单（spec warehouse/sales-return）：售后单发起，仓库在「退货入库」验收，采购员处理去向 */
@RestController
@RequestMapping("/api/v1/warehouse/returns")
@RequiredArgsConstructor
public class SalesReturnController {

    private static final String VIEW = "@perm.canAccessMenu('/warehouse/returns') or @perm.canAccessMenu('/sales/after-sales')";
    private static final String AFTER_SALES = "@perm.canAccessMenu('/sales/after-sales')";

    private final SalesReturnService service;

    @PostMapping("/page")
    @PreAuthorize("@perm.canAccessMenu('/warehouse/returns')")
    public Result<PageResult<ReturnVO>> page(@RequestBody ReturnQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/counts")
    @PreAuthorize("@perm.canAccessMenu('/warehouse/returns')")
    public Result<Map<String, Long>> counts() {
        return Result.ok(service.counts());
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public Result<ReturnVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @PreAuthorize(AFTER_SALES)
    public Result<ReturnVO> create(@Valid @RequestBody SaveReturnRequest req) {
        return Result.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(AFTER_SALES)
    public Result<ReturnVO> update(@PathVariable Long id, @Valid @RequestBody SaveReturnRequest req) {
        return Result.ok(service.update(id, req));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(AFTER_SALES)
    public Result<ReturnVO> voidReturn(@PathVariable Long id, @Valid @RequestBody ReasonBody req) {
        return Result.ok(service.voidReturn(id, req.getReason()));
    }

    @PostMapping("/{id}/receive")
    @PreAuthorize("@perm.canAccessMenu('/warehouse/returns') and @perm.has('warehouse:return:receive')")
    public Result<ReturnVO> receive(@PathVariable Long id, @Valid @RequestBody ReceiveReturnRequest req) {
        return Result.ok(service.receive(id, req));
    }

    @PostMapping("/{id}/unreceive")
    @PreAuthorize("@perm.canAccessMenu('/warehouse/returns') and @perm.has('warehouse:return:receive')")
    public Result<ReturnVO> unreceive(@PathVariable Long id, @Valid @RequestBody ReasonBody req) {
        return Result.ok(service.unreceive(id, req.getReason()));
    }

    @PostMapping("/{id}/dispose")
    @PreAuthorize("@perm.canAccessMenu('/warehouse/returns') and @perm.has('purchase:return:dispose')")
    public Result<ReturnVO> dispose(@PathVariable Long id, @Valid @RequestBody DisposeReturnRequest req) {
        return Result.ok(service.dispose(id, req));
    }
}
