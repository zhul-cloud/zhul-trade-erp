package com.zhul.erp.modules.logistics.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.logistics.dto.CountsVO;
import com.zhul.erp.modules.logistics.dto.ForwarderVO;
import com.zhul.erp.modules.logistics.dto.HandOverRequest;
import com.zhul.erp.modules.logistics.dto.NoticeFormVO;
import com.zhul.erp.modules.logistics.dto.OutboundPageQuery;
import com.zhul.erp.modules.logistics.dto.OutboundVO;
import com.zhul.erp.modules.logistics.dto.PackRequest;
import com.zhul.erp.modules.logistics.dto.SaveNoticeRequest;
import com.zhul.erp.modules.logistics.service.OutboundService;
import com.zhul.erp.modules.logistics.support.LogisticsSupport;
import com.zhul.erp.modules.warehouse.dto.ReasonRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 发货通知（业务员，订单数据权限）与出库打包（仓库，看全部） */
@RestController
@RequestMapping("/api/v1/logistics")
@RequiredArgsConstructor
public class OutboundController {

    private static final String SO_MENU = "@perm.canAccessMenu('/sales/orders')";
    private static final String NOTICE = SO_MENU + " and @perm.has('sales:order:ship-notice')";
    private static final String WH_MENU = "@perm.canAccessMenu('/warehouse/outbounds')";
    private static final String PACK = WH_MENU + " and @perm.has('warehouse:outbound:pack')";
    private static final String ANY = "@perm.canAccessMenu('/sales/orders') or @perm.canAccessMenu('/warehouse/outbounds')"
            + " or @perm.canAccessMenu('/logistics/shipments') or @perm.canAccessMenu('/purchase/shipments')"
            + " or @perm.canAccessMenu('/logistics/statements')";

    private final OutboundService service;
    private final LogisticsSupport support;

    /** 启用的货代（服务商） */
    @GetMapping("/forwarders")
    @PreAuthorize(ANY)
    public Result<List<ForwarderVO>> forwarders() {
        return Result.ok(support.forwarders());
    }

    @GetMapping("/outbounds/notice-form")
    @PreAuthorize(NOTICE)
    public Result<NoticeFormVO> noticeForm(@RequestParam(required = false) Long soId, @RequestParam(required = false) Long outboundId) {
        return Result.ok(service.noticeForm(soId, outboundId));
    }

    @PostMapping("/outbounds/notice")
    @PreAuthorize(NOTICE)
    public Result<OutboundVO> createNotice(@Valid @RequestBody SaveNoticeRequest req) {
        return Result.ok(service.createNotice(req));
    }

    @PutMapping("/outbounds/notice/{id}")
    @PreAuthorize(NOTICE)
    public Result<OutboundVO> updateNotice(@PathVariable Long id, @Valid @RequestBody SaveNoticeRequest req) {
        return Result.ok(service.updateNotice(id, req));
    }

    @PostMapping("/outbounds/{id}/withdraw")
    @PreAuthorize(NOTICE)
    public Result<OutboundVO> withdraw(@PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
        return Result.ok(service.withdraw(id, req.getReason()));
    }

    /** 订单上的出库单 */
    @GetMapping("/outbounds/by-order/{soId}")
    @PreAuthorize(SO_MENU)
    public Result<List<OutboundVO>> byOrder(@PathVariable Long soId) {
        return Result.ok(service.byOrder(soId));
    }

    @PostMapping("/outbounds/page")
    @PreAuthorize(WH_MENU)
    public Result<PageResult<OutboundVO>> page(@RequestBody OutboundPageQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/outbounds/counts")
    @PreAuthorize(WH_MENU)
    public Result<CountsVO> counts() {
        return Result.ok(service.counts());
    }

    @GetMapping("/outbounds/{id}")
    @PreAuthorize(WH_MENU)
    public Result<OutboundVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id, false));
    }

    @PutMapping("/outbounds/{id}/pack")
    @PreAuthorize(PACK)
    public Result<OutboundVO> pack(@PathVariable Long id, @Valid @RequestBody PackRequest req) {
        return Result.ok(service.pack(id, req));
    }

    @PostMapping("/outbounds/hand-over")
    @PreAuthorize(PACK)
    public Result<List<OutboundVO>> handOver(@Valid @RequestBody HandOverRequest req) {
        return Result.ok(service.handOver(req));
    }

    @PostMapping("/outbounds/{id}/undo-hand-over")
    @PreAuthorize(PACK)
    public Result<OutboundVO> undoHandOver(@PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
        return Result.ok(service.undoHandOver(id, req.getReason()));
    }
}
