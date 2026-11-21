package com.zhul.erp.modules.inventory.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.aftersales.dto.ReasonBody;
import com.zhul.erp.modules.inventory.dto.OtherOutboundQuery;
import com.zhul.erp.modules.inventory.dto.OtherOutboundRequest;
import com.zhul.erp.modules.inventory.dto.OtherOutboundVO;
import com.zhul.erp.modules.inventory.service.OtherOutboundService;
import com.zhul.erp.modules.system.doclog.DocAttachRequest;
import com.zhul.erp.modules.system.doclog.DocRemarkRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 其他出库单（spec warehouse/other-outbound）：「仓库管理 → 其他出库」 */
@RestController
@RequestMapping("/api/v1/warehouse/other-outbounds")
@RequiredArgsConstructor
public class OtherOutboundController {

    private static final String VIEW = "@perm.canAccessMenu('/warehouse/other-outbounds')";
    /** 新建也可以从库存查询的批次行发起 */
    private static final String CREATE = "(@perm.canAccessMenu('/warehouse/other-outbounds') or @perm.canAccessMenu('/warehouse/stocks'))"
            + " and @perm.has('inventory:other-outbound:edit')";
    private static final String EDIT = VIEW + " and @perm.has('inventory:other-outbound:edit')";
    private static final String VOID = VIEW + " and @perm.has('inventory:other-outbound:void')";

    private final OtherOutboundService service;

    @PostMapping("/page")
    @PreAuthorize(VIEW)
    public Result<PageResult<OtherOutboundVO>> page(@RequestBody OtherOutboundQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public Result<OtherOutboundVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping
    @PreAuthorize(CREATE)
    public Result<OtherOutboundVO> create(@Valid @RequestBody OtherOutboundRequest req) {
        return Result.ok(service.createManual(req));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(VOID)
    public Result<OtherOutboundVO> voidOutbound(@PathVariable Long id, @Valid @RequestBody ReasonBody req) {
        return Result.ok(service.voidOutbound(id, req.getReason()));
    }

    @PutMapping("/{id}/remark")
    @PreAuthorize(EDIT)
    public Result<OtherOutboundVO> remark(@PathVariable Long id, @Valid @RequestBody DocRemarkRequest req) {
        return Result.ok(service.remark(id, req.getRemark()));
    }

    @PostMapping("/{id}/attachments")
    @PreAuthorize(EDIT)
    public Result<OtherOutboundVO> attach(@PathVariable Long id, @Valid @RequestBody DocAttachRequest req) {
        return Result.ok(service.attach(id, req.getIds()));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(EDIT)
    public Result<OtherOutboundVO> removeAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        return Result.ok(service.removeAttachment(id, attachmentId));
    }
}
