package com.zhul.erp.modules.system.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.dto.DeptDeleteCheckVO;
import com.zhul.erp.modules.system.dto.DeptVO;
import com.zhul.erp.modules.system.dto.SaveDeptRequest;
import com.zhul.erp.modules.system.service.DeptService;
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

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system/depts")
@RequiredArgsConstructor
public class DeptController {

    private final DeptService deptService;

    @GetMapping
    public Result<List<DeptVO>> listAll() {
        return Result.ok(deptService.listAll());
    }

    @GetMapping("/tree")
    public Result<List<DeptVO>> tree() {
        return Result.ok(deptService.getDeptTree());
    }

    @PreAuthorize("@perm.has('system:dept:add')")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SaveDeptRequest req) {
        deptService.createDept(req);
        return Result.ok();
    }

    @PreAuthorize("@perm.has('system:dept:edit')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Integer id, @Valid @RequestBody SaveDeptRequest req) {
        deptService.updateDept(id, req);
        return Result.ok();
    }

    @PreAuthorize("@perm.has('system:dept:edit')")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Integer id, @RequestBody Map<String, Integer> body) {
        deptService.updateStatus(id, body.get("status"));
        return Result.ok();
    }

    @GetMapping("/{id}/delete-check")
    public Result<DeptDeleteCheckVO> deleteCheck(@PathVariable Integer id) {
        return Result.ok(deptService.checkDeletable(id));
    }

    @PreAuthorize("@perm.has('system:dept:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        deptService.deleteDept(id);
        return Result.ok();
    }
}
