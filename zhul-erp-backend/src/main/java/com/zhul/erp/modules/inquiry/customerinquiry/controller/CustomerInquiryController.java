package com.zhul.erp.modules.inquiry.customerinquiry.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.ConfirmRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryDetailVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryPageQuery;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerTypeVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.DraftVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceMatchVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.SubmitCustomerInquiryRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.UpdateInquiryLevelRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.UploadedFileVO;
import com.zhul.erp.modules.inquiry.customerinquiry.service.CustomerInquiryService;
import com.zhul.erp.modules.inquiry.sourcing.service.PriceHistoryService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/** 客户询盘：要求能访问「客户询盘」菜单，数据范围按负责人 */
@RestController
@RequestMapping("/api/v1/inquiry/customer-inquiries")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/inquiry/customer-inquiries')")
public class CustomerInquiryController {

    private final CustomerInquiryService service;
    private final PriceHistoryService priceHistoryService;

    @PostMapping("/attachments")
    public Result<UploadedFileVO> upload(@RequestPart("file") MultipartFile file) {
        return Result.ok(service.uploadAttachment(file));
    }

    @PostMapping("/attachments/from-opportunity")
    public Result<UploadedFileVO> copyFromOpportunity(@RequestBody Map<String, Long> body) {
        return Result.ok(service.copyOpportunityAttachment(body.get("opportunityId"), body.get("attachmentId")));
    }

    @GetMapping("/customer-type")
    public Result<CustomerTypeVO> customerType(@RequestParam Long customerId) {
        return Result.ok(service.customerType(customerId));
    }

    /** 确认页逐行查询历史询价（手动录入时填完型号即调用） */
    @GetMapping("/price-match")
    public Result<PriceMatchVO> priceMatch(@RequestParam String brand, @RequestParam String model) {
        return Result.ok(priceHistoryService.match(brand, model));
    }

    @PostMapping
    public Result<CustomerInquiryVO> submit(@Valid @RequestBody SubmitCustomerInquiryRequest req) {
        return Result.ok(service.submit(req));
    }

    @PostMapping("/page")
    public Result<PageResult<CustomerInquiryVO>> page(@RequestBody CustomerInquiryPageQuery query) {
        return Result.ok(service.page(query));
    }

    @GetMapping("/{id}")
    public Result<CustomerInquiryDetailVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public void attachment(@PathVariable Long id, @PathVariable Long attachmentId,
                           @RequestParam(defaultValue = "false") boolean inline, HttpServletResponse response) throws IOException {
        CustomerInquiryService.AttachmentFile file = service.attachmentFile(id, attachmentId);
        response.setContentType(file.contentType());
        response.setContentLengthLong(Files.size(file.path()));
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, (inline ? "inline" : "attachment")
                + "; filename*=UTF-8''" + URLEncoder.encode(file.fileName(), StandardCharsets.UTF_8).replace("+", "%20"));
        response.setHeader("X-Content-Type-Options", "nosniff");
        Files.copy(file.path(), response.getOutputStream());
    }

    @PostMapping("/{id}/start-parse")
    public Result<CustomerInquiryVO> startParse(@PathVariable Long id) {
        return Result.ok(service.startParse(id));
    }

    @PostMapping("/{id}/retry-parse")
    public Result<CustomerInquiryVO> retryParse(@PathVariable Long id) {
        return Result.ok(service.retryParse(id));
    }

    @GetMapping("/{id}/draft")
    public Result<DraftVO> draft(@PathVariable Long id) {
        return Result.ok(service.draft(id));
    }

    @PostMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id, @Valid @RequestBody ConfirmRequest req) {
        service.confirm(id, req);
        return Result.ok();
    }

    @PutMapping("/{id}/level")
    public Result<Void> updateLevel(@PathVariable Long id, @Valid @RequestBody UpdateInquiryLevelRequest req) {
        service.updateLevel(id, req.getLevel());
        return Result.ok();
    }

    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return Result.ok();
    }

    @PostMapping("/{id}/reviewed")
    public Result<Void> reviewed(@PathVariable Long id) {
        service.markReviewed(id);
        return Result.ok();
    }
}
