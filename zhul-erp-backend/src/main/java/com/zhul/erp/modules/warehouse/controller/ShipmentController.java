package com.zhul.erp.modules.warehouse.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.warehouse.dto.ArrivalEstimateVO;
import com.zhul.erp.modules.warehouse.dto.ReasonRequest;
import com.zhul.erp.modules.warehouse.dto.SaveShipmentRequest;
import com.zhul.erp.modules.warehouse.dto.ShipmentFormVO;
import com.zhul.erp.modules.warehouse.dto.ShipmentListVO;
import com.zhul.erp.modules.warehouse.dto.ShipmentPageQuery;
import com.zhul.erp.modules.warehouse.dto.ShipmentVO;
import com.zhul.erp.modules.warehouse.service.ShipmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** 供应商发货单（采购员侧，按数据权限） */
@RestController
@RequestMapping("/api/v1/purchase/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private static final String MENU = "@perm.canAccessMenu('/purchase/shipments')";
    private static final String EDIT = MENU + " and @perm.has('purchase:shipment:create')";

    private final ShipmentService service;

    @PostMapping("/page")
    @PreAuthorize(MENU)
    public Result<PageResult<ShipmentListVO>> page(@RequestBody ShipmentPageQuery query) {
        return Result.ok(service.page(query, true, false));
    }

    @GetMapping("/{id}")
    @PreAuthorize(MENU)
    public Result<ShipmentVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id, true));
    }

    /** 登记（传 poId）或修改（传 shipmentId）发货单的表单 */
    @GetMapping("/form")
    @PreAuthorize(EDIT)
    public Result<ShipmentFormVO> form(@RequestParam(required = false) Long poId, @RequestParam(required = false) Long shipmentId) {
        return Result.ok(service.form(poId, shipmentId));
    }

    /** 按快递时效估算预计到货日期 */
    @GetMapping("/estimate")
    @PreAuthorize(EDIT)
    public Result<ArrivalEstimateVO> estimate(@RequestParam Long poId, @RequestParam(required = false) String carrier,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate shipDate) {
        return Result.ok(service.estimate(poId, carrier, shipDate));
    }

    @PostMapping
    @PreAuthorize(EDIT)
    public Result<ShipmentVO> create(@Valid @RequestBody SaveShipmentRequest req) {
        return Result.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(EDIT)
    public Result<ShipmentVO> update(@PathVariable Long id, @Valid @RequestBody SaveShipmentRequest req) {
        return Result.ok(service.update(id, req));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize(EDIT)
    public Result<ShipmentVO> voidShipment(@PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
        return Result.ok(service.voidShipment(id, req.getReason()));
    }
}
