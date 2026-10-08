package com.zhul.erp.modules.sales.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.sales.dto.ClosePiRequest;
import com.zhul.erp.modules.sales.dto.OverduePiVO;
import com.zhul.erp.modules.sales.dto.ReopenPiRequest;
import com.zhul.erp.modules.sales.dto.AddPiItemsRequest;
import com.zhul.erp.modules.sales.dto.CreatePiRequest;
import com.zhul.erp.modules.sales.dto.MarkPiSentRequest;
import com.zhul.erp.modules.sales.dto.PiCandidateCustomerVO;
import com.zhul.erp.modules.sales.dto.PiCandidateQuotationVO;
import com.zhul.erp.modules.sales.dto.PartyOptionVO;
import com.zhul.erp.modules.sales.dto.PiListVO;
import com.zhul.erp.modules.sales.dto.PiPageQuery;
import com.zhul.erp.modules.sales.dto.PiStatsVO;
import com.zhul.erp.modules.sales.dto.PiVO;
import com.zhul.erp.modules.sales.dto.SavePiRequest;
import com.zhul.erp.modules.sales.service.PiDocumentService;
import com.zhul.erp.modules.sales.service.PiService;
import com.zhul.erp.modules.document.dto.TemplateFile;
import com.zhul.erp.modules.quotation.dto.PreviewVO;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** PI：需要「PI」菜单；报价单详情里的 PI 列表只需要「报价单」菜单 */
@RestController
@RequestMapping("/api/v1/sales/pis")
@RequiredArgsConstructor
public class PiController {

    private final PiService service;
    private final PiDocumentService documentService;

    @GetMapping("/candidates/customers")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<List<PiCandidateCustomerVO>> candidateCustomers(@RequestParam(required = false) String keyword) {
        return Result.ok(service.candidateCustomers(keyword));
    }

    @GetMapping("/candidates/quotations")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<List<PiCandidateQuotationVO>> candidateQuotations(@RequestParam Long customerId) {
        return Result.ok(service.candidateQuotations(customerId));
    }

    @GetMapping("/candidates/quotations/{quotationId}")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<PiCandidateQuotationVO> candidateQuotation(@PathVariable Long quotationId) {
        return Result.ok(service.candidateQuotation(quotationId));
    }

    @PostMapping
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<PiVO> create(@Valid @RequestBody CreatePiRequest req) {
        return Result.ok(service.create(req));
    }

    @PostMapping("/page")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<PageResult<PiListVO>> page(@RequestBody PiPageQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/stats")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<PiStatsVO> stats() {
        return Result.ok(service.stats());
    }

    @GetMapping("/by-quotation/{quotationId}")
    @PreAuthorize("@perm.canAccessMenu('/quotation/quotations') or @perm.canAccessMenu('/sales/pi')")
    public Result<List<PiListVO>> byQuotation(@PathVariable Long quotationId) {
        return Result.ok(service.byQuotation(quotationId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi') or @perm.canAccessMenu('/finance/receipts')")
    public Result<PiVO> detail(@PathVariable Long id, @RequestParam(required = false) Integer version) {
        return Result.ok(service.detail(id, version));
    }

    @GetMapping("/{id}/parties")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<List<PartyOptionVO>> parties(@PathVariable Long id, @RequestParam(required = false) Long customerId) {
        return Result.ok(service.parties(id, customerId));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<PiVO> save(@PathVariable Long id, @Valid @RequestBody SavePiRequest req) {
        return Result.ok(service.save(id, req));
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<PiVO> addItems(@PathVariable Long id, @Valid @RequestBody AddPiItemsRequest req) {
        return Result.ok(service.addItems(id, req));
    }

    @PostMapping("/{id}/sent")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<PiVO> markSent(@PathVariable Long id, @Valid @RequestBody MarkPiSentRequest req) {
        return Result.ok(service.markSent(id, req.getChannel()));
    }

    @PostMapping("/{id}/revise")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<PiVO> revise(@PathVariable Long id) {
        return Result.ok(service.revise(id));
    }

    @PostMapping("/{id}/abandon")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<PiVO> abandon(@PathVariable Long id) {
        return Result.ok(service.abandon(id));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<PiVO> voidPi(@PathVariable Long id) {
        return Result.ok(service.voidPi(id));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<PiVO> close(@PathVariable Long id, @Valid @RequestBody ClosePiRequest req) {
        return Result.ok(service.close(id, req));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<PiVO> reopen(@PathVariable Long id, @RequestBody(required = false) ReopenPiRequest req) {
        return Result.ok(service.reopen(id, req == null ? new ReopenPiRequest() : req));
    }

    @GetMapping("/overdue")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<OverduePiVO> overdue() {
        return Result.ok(service.overdue());
    }

    @GetMapping("/{id}/export")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public void export(@PathVariable Long id, @RequestParam(required = false) Integer version,
                       @RequestParam(defaultValue = "xlsx") String format, HttpServletResponse response) throws IOException {
        TemplateFile file = documentService.export(id, version, format);
        response.setContentType(file.contentType());
        response.setContentLength(file.content().length);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getOutputStream().write(file.content());
    }

    /** 议价测算表：只在系统内使用，含采购成本 */
    @GetMapping("/{id}/bargain-export")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public void bargainExport(@PathVariable Long id, @RequestParam(required = false) Integer version,
                              @RequestParam(required = false) java.math.BigDecimal rate,
                              @RequestParam(required = false) Integer discountType,
                              @RequestParam(required = false) java.math.BigDecimal discountValue,
                              HttpServletResponse response) throws IOException {
        TemplateFile file = documentService.bargainExport(id, version, rate, discountType, discountValue);
        response.setContentType(file.contentType());
        response.setContentLength(file.content().length);
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.getOutputStream().write(file.content());
    }

    /** 实时预览：内容同保存请求，不做字段校验（编辑中可能还没填完） */
    @PostMapping("/{id}/preview")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<PreviewVO> preview(@PathVariable Long id, @RequestBody SavePiRequest content) {
        return Result.ok(documentService.preview(id, content));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.canAccessMenu('/sales/pi')")
    public Result<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return Result.ok();
    }
}
