package com.zhul.erp.modules.inventory.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.inventory.dto.InvWarehouseVO;
import com.zhul.erp.modules.inventory.dto.InventoryModeRequest;
import com.zhul.erp.modules.inventory.dto.RenameWarehouseRequest;
import com.zhul.erp.modules.inventory.service.WarehouseArchiveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 仓库档案与库存模式：「仓库管理 → 仓库档案」 */
@RestController
@RequestMapping("/api/v1/warehouse/warehouses")
@RequiredArgsConstructor
public class WarehouseArchiveController {

    private static final String VIEW = "@perm.canAccessMenu('/warehouse/warehouses')";
    private static final String EDIT = VIEW + " and @perm.has('inventory:warehouse:edit')";
    /** 库存查询、流水、台账、其他出库按仓库筛选 */
    private static final String PICK = "@perm.canAccessMenu('/warehouse/warehouses') or @perm.canAccessMenu('/warehouse/stocks')"
            + " or @perm.canAccessMenu('/warehouse/moves') or @perm.canAccessMenu('/warehouse/ledger')"
            + " or @perm.canAccessMenu('/warehouse/other-outbounds')";

    private final WarehouseArchiveService service;

    @GetMapping
    @PreAuthorize(PICK)
    public Result<List<InvWarehouseVO>> list() {
        return Result.ok(service.list());
    }

    @PutMapping("/{id}")
    @PreAuthorize(EDIT)
    public Result<Void> rename(@PathVariable Long id, @Valid @RequestBody RenameWarehouseRequest req) {
        service.rename(id, req.getName());
        return Result.ok();
    }

    @GetMapping("/mode")
    @PreAuthorize(VIEW)
    public Result<Map<String, String>> mode() {
        return Result.ok(service.mode());
    }

    @PutMapping("/mode")
    @PreAuthorize(EDIT)
    public Result<Map<String, String>> setMode(@Valid @RequestBody InventoryModeRequest req) {
        return Result.ok(service.setMode(req.getMode()));
    }
}
