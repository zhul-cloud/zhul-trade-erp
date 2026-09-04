package com.zhul.erp.modules.masterdata.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.masterdata.dto.CreateSupplierFromChannelRequest;
import com.zhul.erp.modules.masterdata.dto.SaveSupplierRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierCreateResultVO;
import com.zhul.erp.modules.masterdata.dto.SupplierVO;
import com.zhul.erp.modules.masterdata.service.SupplierService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 供应商主数据最小可用管理：创建（含内联快速创建、以及从电商询价渠道转正式供应商）、
 * 关键词搜索、软删除。独立的列表/详情管理页本轮不做。
 */
@RestController
@RequestMapping("/api/v1/masterdata/suppliers")
@RequiredArgsConstructor
public class SupplierController {

    private final SupplierService supplierService;

    @PostMapping
    public Result<SupplierCreateResultVO> create(@Valid @RequestBody SaveSupplierRequest req) {
        return Result.ok(supplierService.create(req));
    }

    @PostMapping("/from-channel")
    public Result<SupplierCreateResultVO> createFromChannel(@Valid @RequestBody CreateSupplierFromChannelRequest req) {
        return Result.ok(supplierService.createFromChannel(req));
    }

    @GetMapping
    public Result<List<SupplierVO>> search(@RequestParam(required = false) String keyword) {
        return Result.ok(supplierService.search(keyword));
    }

    @GetMapping("/{id}")
    public Result<SupplierVO> getById(@PathVariable Long id) {
        return Result.ok(supplierService.getById(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        supplierService.delete(id);
        return Result.ok();
    }
}
