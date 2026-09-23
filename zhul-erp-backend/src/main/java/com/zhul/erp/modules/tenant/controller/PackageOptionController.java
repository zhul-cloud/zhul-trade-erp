package com.zhul.erp.modules.tenant.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.tenant.dto.PackageOptionVO;
import com.zhul.erp.modules.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 套餐下拉选项，供新增/编辑租户时选择套餐用。套餐管理本身（增删改）不在本次范围内，
 * 见 docs/03-产品原型/00-用户域/02-租户管理/01-租户套餐管理/。
 */
@RestController
@RequestMapping("/api/v1/tenant/packages")
@RequiredArgsConstructor
public class PackageOptionController {

    private final TenantService tenantService;

    @GetMapping("/options")
    public Result<List<PackageOptionVO>> options() {
        return Result.ok(tenantService.packageOptions());
    }
}
