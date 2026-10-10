package com.zhul.erp.modules.warehouse.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.warehouse.dto.AddMediaRequest;
import com.zhul.erp.modules.warehouse.dto.CountsVO;
import com.zhul.erp.modules.warehouse.dto.ReasonRequest;
import com.zhul.erp.modules.warehouse.dto.ShootListVO;
import com.zhul.erp.modules.warehouse.dto.ShootPageQuery;
import com.zhul.erp.modules.warehouse.dto.ShootVO;
import com.zhul.erp.modules.warehouse.service.ShootService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 拍摄任务（素材先走 POST /api/v1/attachments?ownerType=SHOOT 上传，再挂到任务上） */
@RestController
@RequestMapping("/api/v1/warehouse/shoots")
@RequiredArgsConstructor
public class ShootController {

    private static final String MENU = "@perm.canAccessMenu('/warehouse/shoots')";
    private static final String EDIT = MENU + " and @perm.has('warehouse:shoot:edit')";

    private final ShootService service;

    @PostMapping("/page")
    @PreAuthorize(MENU)
    public Result<PageResult<ShootListVO>> page(@RequestBody ShootPageQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/counts")
    @PreAuthorize(MENU)
    public Result<CountsVO> counts() {
        CountsVO vo = new CountsVO();
        vo.setPending(service.countPending());
        return Result.ok(vo);
    }

    @GetMapping("/{id}")
    @PreAuthorize(MENU)
    public Result<ShootVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping("/{id}/media")
    @PreAuthorize(EDIT)
    public Result<ShootVO> addMedia(@PathVariable Long id, @Valid @RequestBody AddMediaRequest req) {
        return Result.ok(service.addMedia(id, req));
    }

    @DeleteMapping("/{id}/media/{mediaId}")
    @PreAuthorize(EDIT)
    public Result<ShootVO> removeMedia(@PathVariable Long id, @PathVariable Long mediaId) {
        return Result.ok(service.removeMedia(id, mediaId));
    }

    @PostMapping("/{id}/reuse")
    @PreAuthorize(EDIT)
    public Result<ShootVO> reuse(@PathVariable Long id) {
        return Result.ok(service.reuse(id));
    }

    @PostMapping("/{id}/skip")
    @PreAuthorize(EDIT)
    public Result<ShootVO> skip(@PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
        return Result.ok(service.skip(id, req.getReason()));
    }
}
