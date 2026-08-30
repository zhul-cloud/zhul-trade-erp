package com.zhul.erp.modules.system.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.dto.PositionDeleteCheckVO;
import com.zhul.erp.modules.system.dto.PositionVO;
import com.zhul.erp.modules.system.dto.SavePositionRequest;
import com.zhul.erp.modules.system.service.PositionService;
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
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    public Result<PageResult<PositionVO>> listAll(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer status) {
        return Result.ok(positionService.listAll(page, pageSize, name, status));
    }

    @GetMapping("/all")
    public Result<List<PositionVO>> allPositions() {
        return Result.ok(positionService.allPositions());
    }

    @PreAuthorize("@perm.has('system:position:add')")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SavePositionRequest req) {
        positionService.create(req);
        return Result.ok();
    }

    @PreAuthorize("@perm.has('system:position:edit')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Integer id, @Valid @RequestBody SavePositionRequest req) {
        positionService.update(id, req);
        return Result.ok();
    }

    @PreAuthorize("@perm.has('system:position:edit')")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Integer id, @RequestBody Map<String, Integer> body) {
        positionService.updateStatus(id, body.get("status"));
        return Result.ok();
    }

    @GetMapping("/{id}/delete-check")
    public Result<PositionDeleteCheckVO> deleteCheck(@PathVariable Integer id) {
        return Result.ok(positionService.checkDeletable(id));
    }

    @PreAuthorize("@perm.has('system:position:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        positionService.delete(id);
        return Result.ok();
    }
}
