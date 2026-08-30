package com.zhul.erp.modules.system.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.dto.DictTypeDeleteCheckVO;
import com.zhul.erp.modules.system.dto.DictTypeVO;
import com.zhul.erp.modules.system.dto.SaveDictTypeRequest;
import com.zhul.erp.modules.system.service.DictTypeService;
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

@RestController
@RequestMapping("/api/v1/system/dict-types")
@RequiredArgsConstructor
public class DictTypeController {

    private final DictTypeService dictTypeService;

    @GetMapping
    public Result<List<DictTypeVO>> listAll(@RequestParam(required = false) String name) {
        return Result.ok(dictTypeService.listAll(name));
    }

    @PreAuthorize("@perm.has('system:dict:add')")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SaveDictTypeRequest req) {
        dictTypeService.create(req);
        return Result.ok();
    }

    @PreAuthorize("@perm.has('system:dict:edit')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Integer id, @Valid @RequestBody SaveDictTypeRequest req) {
        dictTypeService.update(id, req);
        return Result.ok();
    }

    @GetMapping("/{id}/delete-check")
    public Result<DictTypeDeleteCheckVO> deleteCheck(@PathVariable Integer id) {
        return Result.ok(dictTypeService.checkDeletable(id));
    }

    @PreAuthorize("@perm.has('system:dict:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        dictTypeService.delete(id);
        return Result.ok();
    }
}
