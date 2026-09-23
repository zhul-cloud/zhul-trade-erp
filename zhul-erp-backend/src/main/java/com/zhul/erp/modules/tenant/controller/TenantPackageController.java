package com.zhul.erp.modules.tenant.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.tenant.dto.PackageDeleteCheckVO;
import com.zhul.erp.modules.tenant.dto.SaveTenantPackageRequest;
import com.zhul.erp.modules.tenant.dto.TenantPackageQuery;
import com.zhul.erp.modules.tenant.dto.TenantPackageVO;
import com.zhul.erp.modules.tenant.service.TenantPackageService;
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

import java.util.Map;

@RestController
@RequestMapping("/api/v1/tenant/packages")
@RequiredArgsConstructor
public class TenantPackageController {

    private final TenantPackageService packageService;

    @GetMapping
    public Result<PageResult<TenantPackageVO>> page(TenantPackageQuery query) {
        return Result.ok(packageService.page(query));
    }

    @PreAuthorize("@perm.has('tenant:package:add')")
    @PostMapping
    public Result<TenantPackageVO> create(@Valid @RequestBody SaveTenantPackageRequest req) {
        return Result.ok(packageService.create(req));
    }

    @PreAuthorize("@perm.has('tenant:package:edit')")
    @PutMapping("/{id}")
    public Result<TenantPackageVO> update(@PathVariable Integer id, @Valid @RequestBody SaveTenantPackageRequest req) {
        return Result.ok(packageService.update(id, req));
    }

    @PreAuthorize("@perm.has('tenant:package:status')")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Integer id, @RequestBody Map<String, Integer> body) {
        packageService.updateStatus(id, body.get("status"));
        return Result.ok();
    }

    @PreAuthorize("@perm.has('tenant:package:delete')")
    @GetMapping("/{id}/delete-check")
    public Result<PackageDeleteCheckVO> deleteCheck(@PathVariable Integer id) {
        return Result.ok(packageService.checkDeletable(id));
    }

    @PreAuthorize("@perm.has('tenant:package:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        packageService.delete(id);
        return Result.ok();
    }
}
