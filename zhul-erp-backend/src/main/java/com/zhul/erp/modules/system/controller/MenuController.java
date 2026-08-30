package com.zhul.erp.modules.system.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.dto.DeleteCheckVO;
import com.zhul.erp.modules.system.dto.MenuVO;
import com.zhul.erp.modules.system.dto.SaveMenuRequest;
import com.zhul.erp.modules.system.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "菜单管理")
@RestController
@RequestMapping("/api/v1/system/menus")
@RequiredArgsConstructor
public class MenuController {

    private final MenuService menuService;

    @Operation(summary = "获取菜单树")
    @GetMapping
    public Result<List<MenuVO>> tree() {
        return Result.ok(menuService.getMenuTree());
    }

    @Operation(summary = "新增菜单")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SaveMenuRequest request) {
        menuService.createMenu(request);
        return Result.ok();
    }

    @Operation(summary = "编辑菜单")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Integer id, @RequestBody SaveMenuRequest request) {
        menuService.updateMenu(id, request);
        return Result.ok();
    }

    @Operation(summary = "删除菜单")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        menuService.deleteMenu(id);
        return Result.ok();
    }

    @Operation(summary = "删除前置校验")
    @GetMapping("/{id}/delete-check")
    public Result<DeleteCheckVO> deleteCheck(@PathVariable Integer id) {
        return Result.ok(menuService.checkDeletable(id));
    }

    @Operation(summary = "启用/禁用菜单")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Integer id, @RequestBody Map<String, Integer> body) {
        menuService.updateStatus(id, body.get("status"));
        return Result.ok();
    }

    @Operation(summary = "更新排序")
    @PutMapping("/{id}/sort")
    public Result<Void> updateSort(@PathVariable Integer id, @RequestBody Map<String, Integer> body) {
        menuService.updateSort(id, body.get("sort"));
        return Result.ok();
    }

    @Operation(summary = "获取角色已分配的菜单ID")
    @GetMapping("/role/{roleCode}")
    public Result<List<Integer>> getRoleMenuIds(@PathVariable String roleCode) {
        return Result.ok(menuService.getRoleMenuIds(roleCode));
    }

    @Operation(summary = "给角色分配菜单")
    @PreAuthorize("@perm.has('system:role:assign')")
    @PutMapping("/role/{roleCode}")
    public Result<Void> assignRoleMenus(@PathVariable String roleCode,
                                         @RequestBody List<Integer> menuIds) {
        menuService.assignRoleMenus(roleCode, menuIds);
        return Result.ok();
    }
}
