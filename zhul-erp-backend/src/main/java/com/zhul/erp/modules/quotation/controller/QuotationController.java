package com.zhul.erp.modules.quotation.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.document.dto.TemplateFile;
import com.zhul.erp.modules.quotation.dto.AddQuotationItemsRequest;
import com.zhul.erp.modules.quotation.dto.CreateQuotationRequest;
import com.zhul.erp.modules.quotation.dto.MarkLostRequest;
import com.zhul.erp.modules.quotation.dto.MarkSentRequest;
import com.zhul.erp.modules.quotation.dto.PickCustomerVO;
import com.zhul.erp.modules.quotation.dto.PickInquiryQuery;
import com.zhul.erp.modules.quotation.dto.PickInquiryVO;
import com.zhul.erp.modules.quotation.dto.PreviewRequest;
import com.zhul.erp.modules.quotation.dto.PreviewVO;
import com.zhul.erp.modules.quotation.dto.QuotationListVO;
import com.zhul.erp.modules.quotation.dto.QuotationPageQuery;
import com.zhul.erp.modules.quotation.dto.QuotationStatsVO;
import com.zhul.erp.modules.quotation.dto.QuotationVO;
import com.zhul.erp.modules.quotation.dto.QuoteInquiryPageVO;
import com.zhul.erp.modules.quotation.dto.QuoteInquiryQuery;
import com.zhul.erp.modules.quotation.dto.QuoteTextVO;
import com.zhul.erp.modules.quotation.dto.SaveQuotationRequest;
import com.zhul.erp.modules.quotation.service.QuotationDocumentService;
import com.zhul.erp.modules.quotation.service.QuotationService;
import com.zhul.erp.modules.quotation.service.QuoteCandidateService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 报价单：要求能访问「报价单」菜单（兼职采购没有），数据范围按报价单创建人 */
@RestController
@RequestMapping("/api/v1/quotations")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/quotation/quotations')")
public class QuotationController {

    private final QuotationService service;
    private final QuoteCandidateService candidateService;
    private final QuotationDocumentService documentService;
    private final com.zhul.erp.modules.quotation.service.QuotationStrategyService strategyService;

    /** 报价策略「沿用历史价」：同一客户同型号最近的成交价（优先）或报价 */
    @GetMapping("/{id}/price-history")
    @PreAuthorize("@perm.canAccessMenu('/quotation/quotations')")
    public Result<List<com.zhul.erp.modules.quotation.dto.PriceHistoryVO>> priceHistory(@PathVariable Long id,
                                                                                      @RequestParam(required = false) Integer version) {
        return Result.ok(strategyService.priceHistory(id, version));
    }

    @GetMapping("/strategy-tiers")
    @PreAuthorize("@perm.canAccessMenu('/quotation/quotations')")
    public Result<List<com.zhul.erp.modules.quotation.dto.StrategyTierDTO>> strategyTiers() {
        return Result.ok(strategyService.tiers());
    }

    @PutMapping("/strategy-tiers")
    @PreAuthorize("@perm.canAccessMenu('/quotation/quotations') and @perm.has('quotation:pricing:edit')")
    public Result<List<com.zhul.erp.modules.quotation.dto.StrategyTierDTO>> saveStrategyTiers(
            @Valid @RequestBody com.zhul.erp.modules.quotation.dto.SaveStrategyTiersRequest req) {
        return Result.ok(strategyService.saveTiers(req.getTiers()));
    }

    // ---------------------------------------------------------------- 新建时的候选

    @PostMapping("/candidates/inquiries")
    public Result<QuoteInquiryPageVO> quoteInquiries(@RequestBody QuoteInquiryQuery q) {
        return Result.ok(candidateService.quoteInquiries(q));
    }

    @GetMapping("/candidates/customers")
    public Result<List<PickCustomerVO>> pickCustomers(@RequestParam(required = false) String keyword) {
        return Result.ok(candidateService.pickCustomers(keyword));
    }

    @PostMapping("/candidates/items")
    public Result<PageResult<PickInquiryVO>> pickInquiries(@Valid @RequestBody PickInquiryQuery q) {
        return Result.ok(candidateService.pickInquiries(q));
    }

    // ---------------------------------------------------------------- 报价单

    @PostMapping("/page")
    public Result<PageResult<QuotationListVO>> page(@RequestBody QuotationPageQuery q) {
        return Result.ok(service.page(q));
    }

    @GetMapping("/stats")
    public Result<QuotationStatsVO> stats() {
        return Result.ok(service.stats());
    }

    @GetMapping("/by-inquiry/{inquiryId}")
    public Result<List<QuotationListVO>> byInquiry(@PathVariable Long inquiryId) {
        return Result.ok(service.byInquiry(inquiryId));
    }

    @PostMapping
    public Result<QuotationVO> create(@Valid @RequestBody CreateQuotationRequest req) {
        return Result.ok(service.create(req));
    }

    @GetMapping("/{id}")
    public Result<QuotationVO> detail(@PathVariable Long id, @RequestParam(required = false) Integer version) {
        return Result.ok(service.detail(id, version));
    }

    @PostMapping("/{id}/revise")
    public Result<QuotationVO> revise(@PathVariable Long id) {
        return Result.ok(service.revise(id));
    }

    @PostMapping("/{id}/abandon")
    public Result<QuotationVO> abandon(@PathVariable Long id) {
        return Result.ok(service.abandon(id));
    }

    @PutMapping("/{id}")
    public Result<QuotationVO> save(@PathVariable Long id, @Valid @RequestBody SaveQuotationRequest req) {
        return Result.ok(service.save(id, req));
    }

    @PostMapping("/{id}/items")
    public Result<QuotationVO> addItems(@PathVariable Long id, @Valid @RequestBody AddQuotationItemsRequest req) {
        return Result.ok(service.addItems(id, req));
    }

    @PostMapping("/{id}/recalc-rate")
    public Result<QuotationVO> recalcRate(@PathVariable Long id) {
        return Result.ok(service.recalcRate(id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/send")
    public Result<QuotationVO> markSent(@PathVariable Long id, @Valid @RequestBody MarkSentRequest req) {
        return Result.ok(service.markSent(id, req.getChannel()));
    }

    @PostMapping("/{id}/copy")
    public Result<QuotationVO> copy(@PathVariable Long id) {
        return Result.ok(service.copy(id));
    }

    @PostMapping("/{id}/lost")
    public Result<QuotationVO> markLost(@PathVariable Long id, @Valid @RequestBody MarkLostRequest req) {
        return Result.ok(service.markLost(id, req));
    }

    @PostMapping("/{id}/void")
    public Result<QuotationVO> voidQuotation(@PathVariable Long id) {
        return Result.ok(service.voidQuotation(id));
    }

    // ---------------------------------------------------------------- 对外文件

    @GetMapping("/{id}/text")
    public Result<QuoteTextVO> text(@PathVariable Long id, @RequestParam(required = false) Integer version) {
        return Result.ok(documentService.text(id, version));
    }

    @GetMapping("/{id}/export")
    public void export(@PathVariable Long id, @RequestParam(defaultValue = "xlsx") String format,
                       @RequestParam(required = false) Integer version, HttpServletResponse response) throws IOException {
        TemplateFile file = documentService.export(id, format, version);
        response.setContentType(file.contentType());
        response.setContentLength(file.content().length);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getOutputStream().write(file.content());
    }

    @PostMapping("/preview")
    public Result<PreviewVO> preview(@Valid @RequestBody PreviewRequest req) {
        return Result.ok(documentService.preview(req));
    }

    @GetMapping("/converter-status")
    public Result<Map<String, Boolean>> converterStatus() {
        return Result.ok(Map.of("available", documentService.converterAvailable()));
    }
}
