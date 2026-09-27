package com.zhul.erp.modules.product.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.product.dto.ApplicationVO;
import com.zhul.erp.modules.product.dto.CompletenessSummaryVO;
import com.zhul.erp.modules.product.dto.CustomsVO;
import com.zhul.erp.modules.product.dto.DocumentVO;
import com.zhul.erp.modules.product.dto.FaqVO;
import com.zhul.erp.modules.product.dto.LogisticsVO;
import com.zhul.erp.modules.product.dto.MediaVO;
import com.zhul.erp.modules.product.dto.ProductMatchVO;
import com.zhul.erp.modules.product.dto.ProductOptionVO;
import com.zhul.erp.modules.product.dto.ProductQuery;
import com.zhul.erp.modules.product.dto.ProductVO;
import com.zhul.erp.modules.product.dto.ReferencePriceVO;
import com.zhul.erp.modules.product.dto.RegisterMediaRequest;
import com.zhul.erp.modules.product.dto.RelationshipVO;
import com.zhul.erp.modules.product.dto.SaveApplicationRequest;
import com.zhul.erp.modules.product.dto.SaveCustomsRequest;
import com.zhul.erp.modules.product.dto.SaveDocumentRequest;
import com.zhul.erp.modules.product.dto.SaveFaqRequest;
import com.zhul.erp.modules.product.dto.SaveLogisticsRequest;
import com.zhul.erp.modules.product.dto.SaveProductRequest;
import com.zhul.erp.modules.product.dto.SaveReferencePriceRequest;
import com.zhul.erp.modules.product.dto.SaveRelationshipRequest;
import com.zhul.erp.modules.product.dto.SaveSpecificationsRequest;
import com.zhul.erp.modules.product.dto.SpecificationVO;
import com.zhul.erp.modules.product.dto.StatusRequest;
import com.zhul.erp.modules.product.dto.UpdateMediaRequest;
import com.zhul.erp.modules.product.service.ProductApplicationService;
import com.zhul.erp.modules.product.service.ProductCustomsService;
import com.zhul.erp.modules.product.service.ProductDocumentService;
import com.zhul.erp.modules.product.service.ProductFaqService;
import com.zhul.erp.modules.product.service.ProductLogisticsService;
import com.zhul.erp.modules.product.service.ProductLookupService;
import com.zhul.erp.modules.product.service.ProductMediaService;
import com.zhul.erp.modules.product.service.ProductReferencePriceService;
import com.zhul.erp.modules.product.service.ProductRelationshipService;
import com.zhul.erp.modules.product.service.ProductService;
import com.zhul.erp.modules.product.service.ProductSpecificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 商品主数据：读取与查找对所有登录用户开放；写入需要权限码，且由 Service 再要求平台账号（design.md 决策 2）。
 */
@RestController
@RequestMapping("/api/v1/product/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductLookupService lookupService;
    private final ProductSpecificationService specificationService;
    private final ProductRelationshipService relationshipService;
    private final ProductDocumentService documentService;
    private final ProductApplicationService applicationService;
    private final ProductFaqService faqService;
    private final ProductMediaService mediaService;
    private final ProductLogisticsService logisticsService;
    private final ProductCustomsService customsService;
    private final ProductReferencePriceService referencePriceService;

    @GetMapping
    public Result<PageResult<ProductVO>> page(ProductQuery query) {
        return Result.ok(productService.page(query));
    }

    /** 各缺项对应的商品数量，仅平台账号 */
    @GetMapping("/completeness-summary")
    public Result<CompletenessSummaryVO> completenessSummary() {
        return Result.ok(productService.completenessSummary());
    }

    /** 选择器远程搜索；brandId 可选，用于向导里提示同品牌的相近型号 */
    @GetMapping("/search")
    public Result<List<ProductOptionVO>> search(@RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer limit,
                                                @RequestParam(required = false) Long brandId) {
        return Result.ok(lookupService.search(keyword, limit, brandId));
    }

    @GetMapping("/match")
    public Result<ProductMatchVO> match(@RequestParam(required = false) String brand,
                                        @RequestParam(required = false) String mpn) {
        return Result.ok(lookupService.match(brand, mpn));
    }

    @GetMapping("/{id}")
    public Result<ProductVO> getById(@PathVariable Long id) {
        return Result.ok(productService.getById(id));
    }

    @PostMapping
    @PreAuthorize("@perm.has('product:product:add')")
    public Result<ProductVO> create(@RequestBody SaveProductRequest req) {
        return Result.ok(productService.create(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<ProductVO> update(@PathVariable Long id, @RequestBody SaveProductRequest req) {
        return Result.ok(productService.update(id, req));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest req) {
        productService.updateStatus(id, req.getStatus());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('product:product:delete')")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<ProductVO> restore(@PathVariable Long id) {
        return Result.ok(productService.restore(id));
    }

    @GetMapping("/{id}/specifications")
    public Result<List<SpecificationVO>> specifications(@PathVariable Long id) {
        return Result.ok(specificationService.list(id));
    }

    @PutMapping("/{id}/specifications")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<List<SpecificationVO>> replaceSpecifications(@PathVariable Long id,
                                                               @RequestBody SaveSpecificationsRequest req) {
        return Result.ok(specificationService.replace(id, req));
    }

    @GetMapping("/{id}/relationships")
    public Result<List<RelationshipVO>> relationships(@PathVariable Long id) {
        return Result.ok(relationshipService.list(id));
    }

    @PostMapping("/{id}/relationships")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<RelationshipVO> createRelationship(@PathVariable Long id,
                                                     @RequestBody SaveRelationshipRequest req) {
        return Result.ok(relationshipService.create(id, req));
    }

    @PutMapping("/{id}/relationships/{relId}")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<RelationshipVO> updateRelationship(@PathVariable Long id, @PathVariable Long relId,
                                                     @RequestBody SaveRelationshipRequest req) {
        return Result.ok(relationshipService.update(id, relId, req));
    }

    @DeleteMapping("/{id}/relationships/{relId}")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<Void> deleteRelationship(@PathVariable Long id, @PathVariable Long relId) {
        relationshipService.delete(id, relId);
        return Result.ok();
    }

    // ---------- 技术资料 ----------

    @GetMapping("/{id}/documents")
    public Result<List<DocumentVO>> documents(@PathVariable Long id) {
        return Result.ok(documentService.list(id));
    }

    @PostMapping("/{id}/documents")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<DocumentVO> createDocument(@PathVariable Long id, @RequestBody SaveDocumentRequest req) {
        return Result.ok(documentService.create(id, req));
    }

    @PutMapping("/{id}/documents/{itemId}")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<DocumentVO> updateDocument(@PathVariable Long id, @PathVariable Long itemId,
                                             @RequestBody SaveDocumentRequest req) {
        return Result.ok(documentService.update(id, itemId, req));
    }

    @DeleteMapping("/{id}/documents/{itemId}")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<Void> deleteDocument(@PathVariable Long id, @PathVariable Long itemId) {
        documentService.delete(id, itemId);
        return Result.ok();
    }

    // ---------- 应用场景 ----------

    @GetMapping("/{id}/applications")
    public Result<List<ApplicationVO>> applications(@PathVariable Long id) {
        return Result.ok(applicationService.list(id));
    }

    @PostMapping("/{id}/applications")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<ApplicationVO> createApplication(@PathVariable Long id, @RequestBody SaveApplicationRequest req) {
        return Result.ok(applicationService.create(id, req));
    }

    @PutMapping("/{id}/applications/{itemId}")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<ApplicationVO> updateApplication(@PathVariable Long id, @PathVariable Long itemId,
                                                   @RequestBody SaveApplicationRequest req) {
        return Result.ok(applicationService.update(id, itemId, req));
    }

    @DeleteMapping("/{id}/applications/{itemId}")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<Void> deleteApplication(@PathVariable Long id, @PathVariable Long itemId) {
        applicationService.delete(id, itemId);
        return Result.ok();
    }

    // ---------- FAQ ----------

    /** 租户账号读不到待审核的 FAQ，过滤在服务端完成 */
    @GetMapping("/{id}/faqs")
    public Result<List<FaqVO>> faqs(@PathVariable Long id) {
        return Result.ok(faqService.list(id));
    }

    @PostMapping("/{id}/faqs")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<FaqVO> createFaq(@PathVariable Long id, @RequestBody SaveFaqRequest req) {
        return Result.ok(faqService.create(id, req));
    }

    @PutMapping("/{id}/faqs/{itemId}")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<FaqVO> updateFaq(@PathVariable Long id, @PathVariable Long itemId, @RequestBody SaveFaqRequest req) {
        return Result.ok(faqService.update(id, itemId, req));
    }

    @DeleteMapping("/{id}/faqs/{itemId}")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<Void> deleteFaq(@PathVariable Long id, @PathVariable Long itemId) {
        faqService.delete(id, itemId);
        return Result.ok();
    }

    /** 审核确认待审核 FAQ：来源变为人工撰写，记录审核人与审核时间 */
    @PatchMapping("/{id}/faqs/{itemId}/approve")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<FaqVO> approveFaq(@PathVariable Long id, @PathVariable Long itemId) {
        return Result.ok(faqService.approve(id, itemId));
    }

    // ---------- 图片与视频 ----------

    @GetMapping("/{id}/media")
    public Result<List<MediaVO>> media(@PathVariable Long id) {
        return Result.ok(mediaService.list(id));
    }

    /** 登记外部链接 */
    @PostMapping("/{id}/media")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<MediaVO> registerMedia(@PathVariable Long id, @RequestBody RegisterMediaRequest req) {
        return Result.ok(mediaService.register(id, req));
    }

    /** 上传一个文件（multipart：file、mediaType，可选 title、setMain）；服务端限流 */
    @PostMapping(value = "/{id}/media/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<MediaVO> uploadMedia(@PathVariable Long id,
                                       @RequestParam("file") MultipartFile file,
                                       @RequestParam Integer mediaType,
                                       @RequestParam(required = false) String title,
                                       @RequestParam(required = false) Boolean setMain) {
        return Result.ok(mediaService.upload(id, file, mediaType, title, setMain));
    }

    @PutMapping("/{id}/media/{itemId}")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<MediaVO> updateMedia(@PathVariable Long id, @PathVariable Long itemId,
                                       @RequestBody UpdateMediaRequest req) {
        return Result.ok(mediaService.update(id, itemId, req));
    }

    @DeleteMapping("/{id}/media/{itemId}")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<Void> deleteMedia(@PathVariable Long id, @PathVariable Long itemId) {
        mediaService.delete(id, itemId);
        return Result.ok();
    }

    /** 设为主图（仅图片），同一事务内取消原主图 */
    @PatchMapping("/{id}/media/{itemId}/main")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<MediaVO> setMainMedia(@PathVariable Long id, @PathVariable Long itemId) {
        return Result.ok(mediaService.setMain(id, itemId));
    }

    // ---------- 物流、海关、参考价（一对一） ----------

    @GetMapping("/{id}/logistics")
    public Result<LogisticsVO> logistics(@PathVariable Long id) {
        return Result.ok(logisticsService.get(id));
    }

    @PutMapping("/{id}/logistics")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<LogisticsVO> saveLogistics(@PathVariable Long id, @RequestBody SaveLogisticsRequest req) {
        return Result.ok(logisticsService.save(id, req));
    }

    @GetMapping("/{id}/customs")
    public Result<CustomsVO> customs(@PathVariable Long id) {
        return Result.ok(customsService.get(id));
    }

    @PutMapping("/{id}/customs")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<CustomsVO> saveCustoms(@PathVariable Long id, @RequestBody SaveCustomsRequest req) {
        return Result.ok(customsService.save(id, req));
    }

    /** 平台参考价：平台层面的参考信息，不是任何租户的报价或售价 */
    @GetMapping("/{id}/reference-price")
    public Result<ReferencePriceVO> referencePrice(@PathVariable Long id) {
        return Result.ok(referencePriceService.get(id));
    }

    @PutMapping("/{id}/reference-price")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<ReferencePriceVO> saveReferencePrice(@PathVariable Long id,
                                                       @RequestBody SaveReferencePriceRequest req) {
        return Result.ok(referencePriceService.save(id, req));
    }

    @DeleteMapping("/{id}/reference-price")
    @PreAuthorize("@perm.has('product:product:edit')")
    public Result<Void> clearReferencePrice(@PathVariable Long id) {
        referencePriceService.clear(id);
        return Result.ok();
    }
}
