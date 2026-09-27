package com.zhul.erp.modules.tenant.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.tenant.dto.RenewTenantRequest;
import com.zhul.erp.modules.tenant.dto.ResetPasswordResultVO;
import com.zhul.erp.modules.tenant.dto.SaveTenantRequest;
import com.zhul.erp.modules.tenant.dto.TenantCreateResultVO;
import com.zhul.erp.modules.tenant.dto.TenantQuery;
import com.zhul.erp.modules.tenant.dto.TenantVO;
import com.zhul.erp.modules.tenant.service.TenantService;
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

@RestController
@RequestMapping("/api/v1/tenant/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    public Result<PageResult<TenantVO>> page(TenantQuery query) {
        return Result.ok(tenantService.page(query));
    }

    @PreAuthorize("@perm.has('tenant:list:add')")
    @PostMapping
    public Result<TenantCreateResultVO> create(@Valid @RequestBody SaveTenantRequest req) {
        return Result.ok(tenantService.create(req));
    }

    @PreAuthorize("@perm.has('tenant:list:edit')")
    @PutMapping("/{id}")
    public Result<TenantVO> update(@PathVariable Integer id, @Valid @RequestBody SaveTenantRequest req) {
        return Result.ok(tenantService.update(id, req));
    }

    @PreAuthorize("@perm.has('tenant:list:status')")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Integer id, @RequestBody Map<String, Integer> body) {
        tenantService.updateStatus(id, body.get("status"));
        return Result.ok();
    }

    // 续期没有单独的按钮权限码（种子数据里没开），复用"编辑租户"的权限码
    @PreAuthorize("@perm.has('tenant:list:edit')")
    @PostMapping("/{id}/renew")
    public Result<TenantVO> renew(@PathVariable Integer id, @Valid @RequestBody RenewTenantRequest req) {
        return Result.ok(tenantService.renew(id, req));
    }

    @PreAuthorize("@perm.has('tenant:list:resetPwd')")
    @PostMapping("/{id}/reset-password")
    public Result<ResetPasswordResultVO> resetPassword(@PathVariable Integer id) {
        return Result.ok(tenantService.resetPassword(id));
    }
}
