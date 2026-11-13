package com.zhul.erp.modules.warehouse.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.warehouse.dto.AcceptRequest;
import com.zhul.erp.modules.warehouse.dto.CountsVO;
import com.zhul.erp.modules.warehouse.dto.DirectReceiveRequest;
import com.zhul.erp.modules.warehouse.dto.ReasonRequest;
import com.zhul.erp.modules.warehouse.dto.ReceiptListVO;
import com.zhul.erp.modules.warehouse.dto.ReceiptPageQuery;
import com.zhul.erp.modules.warehouse.dto.ReceiptVO;
import com.zhul.erp.modules.warehouse.dto.ReceivableOrderVO;
import com.zhul.erp.modules.warehouse.dto.ShipmentListVO;
import com.zhul.erp.modules.warehouse.dto.ShipmentPageQuery;
import com.zhul.erp.modules.warehouse.dto.ShipmentVO;
import com.zhul.erp.modules.warehouse.service.ReceiptService;
import com.zhul.erp.modules.warehouse.service.ShipmentService;
import jakarta.validation.Valid;
import com.zhul.erp.modules.system.doclog.DocAttachRequest;
import com.zhul.erp.modules.system.doclog.DocRemarkRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 采购入库（仓库侧，只按租户，不按采购员过滤） */
@RestController
@RequestMapping("/api/v1/warehouse/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private static final String MENU = "@perm.canAccessMenu('/warehouse/receipts')";
    private static final String EDIT = MENU + " and @perm.has('warehouse:receipt:create')";

    private final ReceiptService service;
    private final ShipmentService shipments;

    /** 待收货：在途的发货单，按发货日期从早到晚 */
    @PostMapping("/pending")
    @PreAuthorize(MENU)
    public Result<PageResult<ShipmentListVO>> pending(@RequestBody ShipmentPageQuery query) {
        return Result.ok(shipments.page(query, false, true));
    }

    @GetMapping("/counts")
    @PreAuthorize(MENU)
    public Result<CountsVO> counts() {
        CountsVO vo = new CountsVO();
        vo.setPending(shipments.countInTransit());
        return Result.ok(vo);
    }

    @GetMapping("/shipments/{shipmentId}")
    @PreAuthorize(MENU)
    public Result<ShipmentVO> shipment(@PathVariable Long shipmentId) {
        return Result.ok(shipments.detail(shipmentId, false));
    }

    @PostMapping("/page")
    @PreAuthorize(MENU)
    public Result<PageResult<ReceiptListVO>> page(@RequestBody ReceiptPageQuery query) {
        return Result.ok(service.page(query));
    }

    /** 采购入库单详情页：仓库人员看全部；采购员（发货跟踪或采购订单菜单）按采购订单的采购员（change warehouse-logistics-docs） */
    @GetMapping("/{id}")
    @PreAuthorize(MENU + " or @perm.canAccessMenu('/purchase/shipments') or @perm.canAccessMenu('/purchase/orders')")
    public Result<ReceiptVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping("/{id}/remark")
    @PreAuthorize(MENU)
    public Result<ReceiptVO> remark(@PathVariable Long id, @Valid @RequestBody DocRemarkRequest req) {
        return Result.ok(service.remark(id, req.getRemark()));
    }

    /** defect=true 为不良照片，否则为验收照片 */
    @PostMapping("/{id}/attachments")
    @PreAuthorize(MENU)
    public Result<ReceiptVO> attach(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean defect,
                                    @Valid @RequestBody DocAttachRequest req) {
        return Result.ok(service.attach(id, req.getIds(), defect));
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @PreAuthorize(MENU)
    public Result<ReceiptVO> removeAttachment(@PathVariable Long id, @PathVariable Long attachmentId,
                                              @RequestParam(defaultValue = "false") boolean defect) {
        return Result.ok(service.removeAttachment(id, attachmentId, defect));
    }

    @PostMapping
    @PreAuthorize(EDIT)
    public Result<ReceiptVO> accept(@Valid @RequestBody AcceptRequest req) {
        return Result.ok(service.accept(req));
    }

    /** 直接收货可选的采购订单 */
    @GetMapping("/orders")
    @PreAuthorize(EDIT)
    public Result<List<ReceivableOrderVO>> orders(@RequestParam(required = false) String keyword) {
        return Result.ok(service.receivableOrders(keyword));
    }

    @PostMapping("/direct")
    @PreAuthorize(EDIT)
    public Result<ReceiptVO> direct(@Valid @RequestBody DirectReceiveRequest req) {
        return Result.ok(service.direct(req));
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize(EDIT)
    public Result<ReceiptVO> reverse(@PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
        return Result.ok(service.reverse(id, req.getReason()));
    }
}
