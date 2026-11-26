package com.zhul.erp.modules.product.candidate.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.product.candidate.dto.ApproveCandidateRequest;
import com.zhul.erp.modules.product.candidate.dto.BatchApproveRequest;
import com.zhul.erp.modules.product.candidate.dto.CandidateAiFillRequest;
import com.zhul.erp.modules.product.candidate.dto.CandidateAiFillVO;
import com.zhul.erp.modules.product.candidate.dto.BatchApproveVO;
import com.zhul.erp.modules.product.candidate.dto.BatchEditRequest;
import com.zhul.erp.modules.product.candidate.dto.BatchEditVO;
import com.zhul.erp.modules.product.candidate.dto.CandidateImportPreviewVO;
import com.zhul.erp.modules.product.candidate.dto.CandidateImportRequest;
import com.zhul.erp.modules.product.candidate.dto.CandidateImportResultVO;
import com.zhul.erp.modules.product.candidate.dto.CandidatePageQuery;
import com.zhul.erp.modules.product.candidate.dto.CandidateVO;
import com.zhul.erp.modules.product.candidate.dto.MergeCandidateRequest;
import com.zhul.erp.modules.product.candidate.dto.RejectCandidateRequest;
import com.zhul.erp.modules.product.candidate.service.CandidateAiFill;
import com.zhul.erp.modules.product.candidate.service.CandidateImportService;
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

import java.util.List;
import java.util.Map;

/** 商品候选：查看需要菜单权限，审核还需要「商品候选审核」按钮权限 */
@RestController
@RequestMapping("/api/v1/product/candidates")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/product/candidates')")
public class ProductCandidateController {

    private static final String REVIEW = "@perm.canAccessMenu('/product/candidates') and @perm.has('product:candidate:review')";

    private final ProductCandidateService service;
    private final CandidateImportService importService;
    private final CandidateAiFill aiFill;
    private final com.zhul.erp.modules.product.candidate.service.DocProductLinkService linkService;
    private final com.zhul.erp.modules.product.candidate.service.UnlinkedMarkService markService;

    /** 上传商品文件导入候选：有商品候选菜单即可，建档仍需审核权限 */
    @PostMapping("/import/preview")
    public Result<List<CandidateImportPreviewVO>> importPreview(@Valid @RequestBody CandidateImportRequest req) {
        return Result.ok(importService.preview(req));
    }

    @PostMapping("/import/confirm")
    public Result<CandidateImportResultVO> importConfirm(@Valid @RequestBody CandidateImportRequest req) {
        return Result.ok(importService.confirm(req));
    }

    @PostMapping("/page")
    public Result<PageResult<CandidateVO>> page(@RequestBody CandidatePageQuery query) {
        return Result.ok(service.page(query));
    }

    @PostMapping("/counts")
    public Result<Map<String, Long>> counts(@RequestBody CandidatePageQuery query) {
        return Result.ok(service.counts(query));
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

    /** 批量修改品牌、品类：只改仍待审核的候选 */
    @PostMapping("/batch-edit")
    @PreAuthorize(REVIEW)
    public Result<BatchEditVO> batchEdit(@Valid @RequestBody BatchEditRequest req) {
        return Result.ok(service.batchEdit(req));
    }

    /** 批量 AI 解析品类或品牌：写入候选，审核时可改 */
    @PostMapping("/ai-fill")
    @PreAuthorize(REVIEW)
    public Result<CandidateAiFillVO> aiFill(@Valid @RequestBody CandidateAiFillRequest req) {
        return Result.ok(aiFill.submit(req));
    }

    @GetMapping("/ai-fill/{taskId}")
    @PreAuthorize(REVIEW)
    public Result<CandidateAiFillVO> aiFillStatus(@PathVariable Long taskId) {
        return Result.ok(aiFill.status(taskId));
    }

    /** 待关联清单：还没关联商品的单据明细，按型号归并（spec product/document-product-link） */
    @GetMapping("/unlinked")
    @PreAuthorize(REVIEW)
    public Result<com.zhul.erp.common.result.PageResult<com.zhul.erp.modules.product.candidate.dto.UnlinkedGroupVO>> unlinked(
            @org.springframework.web.bind.annotation.RequestParam(required = false) String keyword,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String table,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "1") int page,
            @org.springframework.web.bind.annotation.RequestParam(name = "page_size", defaultValue = "20") int pageSize,
            @org.springframework.web.bind.annotation.RequestParam(defaultValue = "false") boolean marked) {
        return Result.ok(linkService.unlinked(keyword, table, page, pageSize, marked));
    }

    /** 待关联清单：选择商品或填写真实型号后批量关联，返回关联的条数 */
    @PostMapping("/unlinked/link")
    @PreAuthorize(REVIEW)
    public Result<Integer> linkUnlinked(@Valid @RequestBody com.zhul.erp.modules.product.candidate.dto.LinkUnlinkedRequest req) {
        return Result.ok(req.getProductId() != null ? linkService.link(req.getKey(), req.getProductId())
                : linkService.fillModel(req.getKey(), req.getBrand(), req.getModel()));
    }

    /** 待关联清单：标为不是商品（运费等），从清单移出；返回标记的组数（openspec unlinked-docs-enhance） */
    @PostMapping("/unlinked/ignore")
    @PreAuthorize(REVIEW)
    public Result<Integer> ignoreUnlinked(@Valid @RequestBody com.zhul.erp.modules.product.candidate.dto.UnlinkedKeysRequest req) {
        return Result.ok(markService.ignore(req.getKeys()));
    }

    /** 待关联清单：恢复已标为不是商品的组 */
    @PostMapping("/unlinked/restore")
    @PreAuthorize(REVIEW)
    public Result<Integer> restoreUnlinked(@Valid @RequestBody com.zhul.erp.modules.product.candidate.dto.UnlinkedKeysRequest req) {
        return Result.ok(markService.restore(req.getKeys()));
    }

    /** 待关联清单：批量 AI 识别型号（只给建议） */
    @PostMapping("/unlinked/ai")
    @PreAuthorize(REVIEW)
    public Result<com.zhul.erp.modules.product.candidate.dto.UnlinkedAiVO> aiUnlinked(@Valid @RequestBody com.zhul.erp.modules.product.candidate.dto.UnlinkedKeysRequest req) {
        return Result.ok(markService.submit(req.getKeys()));
    }

    @GetMapping("/unlinked/ai/{taskId}")
    @PreAuthorize(REVIEW)
    public Result<com.zhul.erp.modules.product.candidate.dto.UnlinkedAiVO> aiUnlinkedStatus(@PathVariable Long taskId) {
        return Result.ok(markService.status(taskId));
    }

    /** 待关联清单：批量采纳 AI 建议的型号，逐组关联，失败的组列出原因 */
    @PostMapping("/unlinked/adopt")
    @PreAuthorize(REVIEW)
    public Result<com.zhul.erp.modules.product.candidate.dto.UnlinkedAdoptVO> adoptUnlinked(@Valid @RequestBody com.zhul.erp.modules.product.candidate.dto.UnlinkedKeysRequest req) {
        return Result.ok(markService.adopt(req.getKeys()));
    }

    /** 候选商品被各类单据引用的条数，并入、驳回前展示 */
    @GetMapping("/{id}/references")
    @PreAuthorize(REVIEW)
    public Result<java.util.Map<String, Long>> references(@PathVariable Long id) {
        return Result.ok(service.references(id));
    }

    @PostMapping("/{id}/merge")
    @PreAuthorize(REVIEW)
    public Result<CandidateVO> merge(@PathVariable Long id, @Valid @RequestBody MergeCandidateRequest req) {
        return Result.ok(service.merge(id, req.getProductId(), req.getReason()));
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
