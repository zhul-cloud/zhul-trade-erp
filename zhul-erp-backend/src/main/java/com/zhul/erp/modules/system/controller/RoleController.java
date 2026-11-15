package com.zhul.erp.modules.system.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.dto.RoleDeleteCheckVO;
import com.zhul.erp.modules.system.dto.RoleStatsVO;
import com.zhul.erp.modules.system.dto.RoleVO;
import com.zhul.erp.modules.system.dto.SaveRoleRequest;
import com.zhul.erp.modules.system.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/v1/system/roles")
@RequiredArgsConstructor
public class RoleController {

    /** 角色的读取只给角色管理；角色下拉另给用户管理用（audit SEC-018） */
    private static final String MENU = "@perm.canAccessMenu('/system/role')";
    private static final String OPTIONS = MENU + " or @perm.canAccessMenu('/system/user')";

    private final RoleService roleService;

    @Operation(summary = "角色列表")
    @PreAuthorize(MENU)
    @GetMapping
    public Result<PageResult<RoleVO>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Integer status) {
        return Result.ok(roleService.listRoles(page, pageSize, name, code, status));
    }

    @Operation(summary = "所有角色（下拉）")
    @PreAuthorize(OPTIONS)
    @GetMapping("/all")
    public Result<List<RoleVO>> allRoles() {
        return Result.ok(roleService.allRoles());
    }

    @Operation(summary = "角色统计卡片")
    @PreAuthorize(MENU)
    @GetMapping("/stats")
    public Result<RoleStatsVO> stats() {
        return Result.ok(roleService.getStats());
    }

    @Operation(summary = "新增角色")
    @PreAuthorize("@perm.has('system:role:add')")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SaveRoleRequest request) {
        roleService.createRole(request);
        return Result.ok();
    }

    @Operation(summary = "编辑角色")
    @PreAuthorize("@perm.has('system:role:edit')")
    @PutMapping("/{roleId}")
    public Result<Void> update(@PathVariable Integer roleId, @RequestBody SaveRoleRequest request) {
        roleService.updateRole(roleId, request);
        return Result.ok();
    }

    @Operation(summary = "删除前置校验")
    @PreAuthorize(MENU)
    @GetMapping("/{roleId}/delete-check")
    public Result<RoleDeleteCheckVO> deleteCheck(@PathVariable Integer roleId) {
        return Result.ok(roleService.checkDeletable(roleId));
    }

    @Operation(summary = "删除角色")
    @PreAuthorize("@perm.has('system:role:delete')")
    @DeleteMapping("/{roleId}")
    public Result<Void> delete(@PathVariable Integer roleId) {
        roleService.deleteRole(roleId);
        return Result.ok();
    }

    @Operation(summary = "获取角色已分配的部门ID（数据权限）")
    @PreAuthorize(MENU)
    @GetMapping("/{roleCode}/depts")
    public Result<List<Integer>> getRoleDeptIds(@PathVariable String roleCode) {
        return Result.ok(roleService.getRoleDeptIds(roleCode));
    }
}
