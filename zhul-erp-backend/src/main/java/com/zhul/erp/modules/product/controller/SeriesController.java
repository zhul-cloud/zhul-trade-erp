package com.zhul.erp.modules.product.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.product.dto.CreateSeriesRequest;
import com.zhul.erp.modules.product.dto.SeriesOptionVO;
import com.zhul.erp.modules.product.dto.SeriesQuery;
import com.zhul.erp.modules.product.dto.SeriesVO;
import com.zhul.erp.modules.product.dto.StatusRequest;
import com.zhul.erp.modules.product.dto.UpdateSeriesRequest;
import com.zhul.erp.modules.product.service.SeriesService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 系列主数据：读取对所有登录用户开放；写入需要权限码，且由 Service 再要求平台账号（design.md 决策 2）。
 */
@RestController
@RequestMapping("/api/v1/product/series")
@RequiredArgsConstructor
public class SeriesController {

    private final SeriesService seriesService;

    @GetMapping
    public Result<PageResult<SeriesVO>> page(SeriesQuery query) {
        return Result.ok(seriesService.page(query));
    }

    /** 启用的系列；传 brandId 时只返回该品牌下的 */
    @GetMapping("/options")
    public Result<List<SeriesOptionVO>> options(@RequestParam(required = false) Long brandId) {
        return Result.ok(seriesService.options(brandId));
    }

    @PostMapping
    @PreAuthorize("@perm.has('product:series:add')")
    public Result<SeriesVO> create(@RequestBody CreateSeriesRequest req) {
        return Result.ok(seriesService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('product:series:edit')")
    public Result<SeriesVO> update(@PathVariable Long id, @RequestBody UpdateSeriesRequest req) {
        return Result.ok(seriesService.update(id, req));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@perm.has('product:series:edit')")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        seriesService.updateStatus(id, req.getStatus());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('product:series:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        seriesService.delete(id);
        return Result.ok();
    }
}
