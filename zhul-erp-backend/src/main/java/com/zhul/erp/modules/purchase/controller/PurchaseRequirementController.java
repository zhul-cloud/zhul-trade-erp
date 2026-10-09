package com.zhul.erp.modules.purchase.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.purchase.dto.AssignRequirementsRequest;
import com.zhul.erp.modules.purchase.dto.GeneratePreviewVO;
import com.zhul.erp.modules.purchase.dto.GeneratePurchaseRequest;
import com.zhul.erp.modules.purchase.dto.RequirementPageQuery;
import com.zhul.erp.modules.purchase.dto.RequirementStatsVO;
import com.zhul.erp.modules.purchase.dto.RequirementVO;
import com.zhul.erp.modules.purchase.dto.SplitRequirementRequest;
import com.zhul.erp.modules.purchase.service.PurchaseRequirementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 采购需求：需要「采购需求」菜单；拆分、指派、生成采购单另需按钮权限 */
@RestController
@RequestMapping("/api/v1/purchase/requirements")
@RequiredArgsConstructor
public class PurchaseRequirementController {

    private final PurchaseRequirementService service;

    @PostMapping("/page")
    @PreAuthorize("@perm.canAccessMenu('/purchase/requirements')")
    public Result<PageResult<RequirementVO>> page(@RequestBody RequirementPageQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/stats")
    @PreAuthorize("@perm.canAccessMenu('/purchase/requirements')")
    public Result<RequirementStatsVO> stats() {
        return Result.ok(service.stats());
    }

    @PostMapping("/{id}/split")
    @PreAuthorize("@perm.canAccessMenu('/purchase/requirements') and @perm.has('purchase:requirement:split')")
    public Result<Void> split(@PathVariable Long id, @Valid @RequestBody SplitRequirementRequest req) {
        service.split(id, req);
        return Result.ok();
    }

    @PostMapping("/assign")
    @PreAuthorize("@perm.canAccessMenu('/purchase/requirements') and @perm.has('purchase:requirement:assign')")
    public Result<Void> assign(@Valid @RequestBody AssignRequirementsRequest req) {
        service.assign(req);
        return Result.ok();
    }

    @GetMapping("/generate-preview")
    @PreAuthorize("@perm.canAccessMenu('/purchase/requirements') and @perm.has('purchase:order:create')")
    public Result<GeneratePreviewVO> preview(@RequestParam List<Long> ids) {
        return Result.ok(service.preview(ids));
    }

    @PostMapping("/generate")
    @PreAuthorize("@perm.canAccessMenu('/purchase/requirements') and @perm.has('purchase:order:create')")
    public Result<List<Long>> generate(@Valid @RequestBody GeneratePurchaseRequest req) {
        return Result.ok(service.generate(req));
    }
}
