package com.zhul.erp.modules.product.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.product.dto.BrandOptionVO;
import com.zhul.erp.modules.product.dto.BrandQuery;
import com.zhul.erp.modules.product.dto.BrandVO;
import com.zhul.erp.modules.product.dto.SaveBrandRequest;
import com.zhul.erp.modules.product.dto.StatusRequest;
import com.zhul.erp.modules.product.service.BrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 品牌主数据：读取对所有登录用户开放；写入需要权限码，且由 Service 再要求平台账号（design.md 决策 2）。
 */
@RestController
@RequestMapping("/api/v1/product/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    public Result<PageResult<BrandVO>> page(BrandQuery query) {
        return Result.ok(brandService.page(query));
    }

    /** 全部启用品牌（走缓存） */
    @GetMapping("/options")
    public Result<List<BrandOptionVO>> options() {
        return Result.ok(brandService.options());
    }

    @PostMapping
    @PreAuthorize("@perm.has('product:brand:add')")
    public Result<BrandVO> create(@RequestBody SaveBrandRequest req) {
        return Result.ok(brandService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('product:brand:edit')")
    public Result<BrandVO> update(@PathVariable Long id, @RequestBody SaveBrandRequest req) {
        return Result.ok(brandService.update(id, req));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@perm.has('product:brand:edit')")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        brandService.updateStatus(id, req.getStatus());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('product:brand:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        brandService.delete(id);
        return Result.ok();
    }
}
