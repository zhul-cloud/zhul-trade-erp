package com.zhul.erp.modules.product.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.product.dto.CategoryOptionVO;
import com.zhul.erp.modules.product.dto.CategoryQuery;
import com.zhul.erp.modules.product.dto.CategoryVO;
import com.zhul.erp.modules.product.dto.SaveCategoryRequest;
import com.zhul.erp.modules.product.dto.StatusRequest;
import com.zhul.erp.modules.product.service.CategoryService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 品类主数据：读取对所有登录用户开放；写入需要权限码，且由 Service 再要求平台账号（design.md 决策 2）。
 */
@RestController
@RequestMapping("/api/v1/product/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public Result<PageResult<CategoryVO>> page(CategoryQuery query) {
        return Result.ok(categoryService.page(query));
    }

    /** 启用品类（走缓存）；level 为空或 1 只返回一级品类，2 只返回细分品类，0 返回全部 */
    @GetMapping("/options")
    public Result<List<CategoryOptionVO>> options(@RequestParam(required = false) Integer level) {
        return Result.ok(categoryService.options(level));
    }

    /** 两级品类树（含停用的，品类管理页用） */
    @GetMapping("/tree")
    public Result<List<CategoryVO>> tree() {
        return Result.ok(categoryService.tree());
    }

    @PostMapping
    @PreAuthorize("@perm.has('product:category:add')")
    public Result<CategoryVO> create(@RequestBody SaveCategoryRequest req) {
        return Result.ok(categoryService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('product:category:edit')")
    public Result<CategoryVO> update(@PathVariable Long id, @RequestBody SaveCategoryRequest req) {
        return Result.ok(categoryService.update(id, req));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@perm.has('product:category:edit')")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        categoryService.updateStatus(id, req.getStatus());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('product:category:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.delete(id);
        return Result.ok();
    }
}
