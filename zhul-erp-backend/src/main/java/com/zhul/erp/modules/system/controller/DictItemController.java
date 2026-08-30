package com.zhul.erp.modules.system.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.dto.DictItemVO;
import com.zhul.erp.modules.system.dto.SaveDictItemRequest;
import com.zhul.erp.modules.system.service.DictItemService;
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
@RequestMapping("/api/v1/system/dict-items")
@RequiredArgsConstructor
public class DictItemController {

    private final DictItemService dictItemService;

    @GetMapping
    public Result<List<DictItemVO>> listByDictTypeId(@RequestParam Integer dictTypeId) {
        return Result.ok(dictItemService.listByDictTypeId(dictTypeId));
    }

    @PreAuthorize("@perm.has('system:dict:add')")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SaveDictItemRequest req) {
        dictItemService.create(req);
        return Result.ok();
    }

    @PreAuthorize("@perm.has('system:dict:edit')")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Integer id, @Valid @RequestBody SaveDictItemRequest req) {
        dictItemService.update(id, req);
        return Result.ok();
    }

    @PreAuthorize("@perm.has('system:dict:delete')")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Integer id) {
        dictItemService.delete(id);
        return Result.ok();
    }
}
