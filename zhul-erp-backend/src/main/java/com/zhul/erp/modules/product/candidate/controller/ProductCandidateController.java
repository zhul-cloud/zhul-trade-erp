package com.zhul.erp.modules.product.candidate.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.product.candidate.dto.ApproveCandidateRequest;
import com.zhul.erp.modules.product.candidate.dto.BatchApproveRequest;
import com.zhul.erp.modules.product.candidate.dto.BatchApproveVO;
import com.zhul.erp.modules.product.candidate.dto.CandidatePageQuery;
import com.zhul.erp.modules.product.candidate.dto.CandidateVO;
import com.zhul.erp.modules.product.candidate.dto.MergeCandidateRequest;
import com.zhul.erp.modules.product.candidate.dto.RejectCandidateRequest;
import com.zhul.erp.modules.product.candidate.service.ProductCandidateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 商品候选：查看需要菜单权限，审核还需要「商品候选审核」按钮权限 */
@RestController
@RequestMapping("/api/v1/product/candidates")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/product/candidates')")
public class ProductCandidateController {

    private static final String REVIEW = "@perm.canAccessMenu('/product/candidates') and @perm.has('product:candidate:review')";

    private final ProductCandidateService service;

    @PostMapping("/page")
    public Result<PageResult<CandidateVO>> page(@RequestBody CandidatePageQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/counts")
    public Result<Map<String, Long>> counts() {
        return Result.ok(service.counts());
    }

    @GetMapping("/{id}")
    public Result<CandidateVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(REVIEW)
    public Result<CandidateVO> approve(@PathVariable Long id, @Valid @RequestBody ApproveCandidateRequest req) {
        return Result.ok(service.approve(id, req));
    }

    @PostMapping("/batch-approve")
    @PreAuthorize(REVIEW)
    public Result<BatchApproveVO> batchApprove(@Valid @RequestBody BatchApproveRequest req) {
        return Result.ok(service.batchApprove(req.getIds()));
    }

    @PostMapping("/{id}/merge")
    @PreAuthorize(REVIEW)
    public Result<CandidateVO> merge(@PathVariable Long id, @Valid @RequestBody MergeCandidateRequest req) {
        return Result.ok(service.merge(id, req.getProductId()));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(REVIEW)
    public Result<CandidateVO> reject(@PathVariable Long id, @Valid @RequestBody RejectCandidateRequest req) {
        return Result.ok(service.reject(id, req));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize(REVIEW)
    public Result<CandidateVO> reopen(@PathVariable Long id) {
        return Result.ok(service.reopen(id));
    }
}
