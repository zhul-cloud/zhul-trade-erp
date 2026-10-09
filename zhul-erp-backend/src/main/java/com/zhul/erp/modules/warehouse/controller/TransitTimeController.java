package com.zhul.erp.modules.warehouse.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.warehouse.dto.SaveTransitTimeRequest;
import com.zhul.erp.modules.warehouse.dto.TransitTimeVO;
import com.zhul.erp.modules.warehouse.service.TransitTimeService;
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

/** 业务设置 → 快递时效 */
@RestController
@RequestMapping("/api/v1/system/transit-times")
@RequiredArgsConstructor
public class TransitTimeController {

    private static final String MENU = "@perm.canAccessMenu('/system/transit-times')";
    private static final String EDIT = MENU + " and @perm.has('system:transit-time:edit')";

    private final TransitTimeService service;

    @GetMapping
    @PreAuthorize(MENU)
    public Result<List<TransitTimeVO>> list(@RequestParam(required = false) String carrier, @RequestParam(required = false) String province) {
        return Result.ok(service.list(carrier, province));
    }

    @PostMapping
    @PreAuthorize(EDIT)
    public Result<TransitTimeVO> create(@Valid @RequestBody SaveTransitTimeRequest req) {
        return Result.ok(service.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize(EDIT)
    public Result<TransitTimeVO> update(@PathVariable Long id, @Valid @RequestBody SaveTransitTimeRequest req) {
        return Result.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(EDIT)
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @GetMapping("/default-days")
    @PreAuthorize(MENU)
    public Result<Integer> defaultDays() {
        return Result.ok(service.defaultDays());
    }

    @PutMapping("/default-days")
    @PreAuthorize(EDIT)
    public Result<Integer> setDefaultDays(@RequestBody Map<String, Integer> body) {
        Integer days = body.get("days");
        return Result.ok(service.setDefaultDays(days == null ? 0 : days));
    }
}
