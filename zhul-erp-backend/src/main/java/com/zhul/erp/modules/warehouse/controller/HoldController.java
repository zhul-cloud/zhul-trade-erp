package com.zhul.erp.modules.warehouse.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.warehouse.dto.CountsVO;
import com.zhul.erp.modules.warehouse.dto.HandleHoldRequest;
import com.zhul.erp.modules.warehouse.dto.HoldPageQuery;
import com.zhul.erp.modules.warehouse.dto.HoldVO;
import com.zhul.erp.modules.warehouse.service.HoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 暂存货 */
@RestController
@RequestMapping("/api/v1/warehouse/holds")
@RequiredArgsConstructor
public class HoldController {

    private static final String MENU = "@perm.canAccessMenu('/warehouse/holds')";

    private final HoldService service;

    @PostMapping("/page")
    @PreAuthorize(MENU)
    public Result<PageResult<HoldVO>> page(@RequestBody HoldPageQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/counts")
    @PreAuthorize(MENU)
    public Result<CountsVO> counts() {
        CountsVO vo = new CountsVO();
        vo.setPending(service.countActive());
        return Result.ok(vo);
    }

    @PostMapping("/{id}/handle")
    @PreAuthorize(MENU + " and @perm.has('warehouse:hold:handle')")
    public Result<HoldVO> handle(@PathVariable Long id, @Valid @RequestBody HandleHoldRequest req) {
        return Result.ok(service.handle(id, req));
    }
}
