package com.zhul.erp.modules.masterdata.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.masterdata.dto.BatchPendingBrandRequest;
import com.zhul.erp.modules.masterdata.dto.ConfirmPendingBrandRequest;
import com.zhul.erp.modules.masterdata.dto.PendingBrandVO;
import com.zhul.erp.modules.masterdata.service.PendingBrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 待确认品牌：品牌管理页的「待确认品牌」页签。与商品主数据其他写操作一样，要求品牌编辑权限且必须是平台账号。
 * 见 openspec/changes/add-supplier-brand-category/。
 */
@RestController
@RequestMapping("/api/v1/masterdata/pending-brands")
@RequiredArgsConstructor
public class PendingBrandController {

    private final PendingBrandService pendingBrandService;

    @GetMapping
    @PreAuthorize("@perm.has('product:brand:edit')")
    public Result<List<PendingBrandVO>> list() {
        return Result.ok(pendingBrandService.list());
    }

    /** 设为已有品牌的别名 */
    @PostMapping("/alias")
    @PreAuthorize("@perm.has('product:brand:edit')")
    public Result<Void> linkAsAlias(@Valid @RequestBody ConfirmPendingBrandRequest req) {
        pendingBrandService.linkAsAlias(req.getPendingKey(), req.getBrandId());
        return Result.ok();
    }

    /** 批量设为同一品牌的别名，返回处理的写法数 */
    @PostMapping("/alias-batch")
    @PreAuthorize("@perm.has('product:brand:edit')")
    public Result<Integer> linkAsAliasBatch(@Valid @RequestBody BatchPendingBrandRequest req) {
        return Result.ok(pendingBrandService.linkAsAlias(req.getPendingKeys(), req.getBrandId()));
    }

    /** 标为不是品牌（可批量），返回处理的写法数 */
    @PostMapping("/ignore")
    @PreAuthorize("@perm.has('product:brand:edit')")
    public Result<Integer> ignore(@Valid @RequestBody BatchPendingBrandRequest req) {
        return Result.ok(pendingBrandService.ignore(req.getPendingKeys()));
    }

    /** 新建为品牌 */
    @PostMapping("/brand")
    @PreAuthorize("@perm.has('product:brand:add')")
    public Result<Long> createBrand(@Valid @RequestBody ConfirmPendingBrandRequest req) {
        return Result.ok(pendingBrandService.createBrand(req.getPendingKey(), req.getBrand()));
    }
}
