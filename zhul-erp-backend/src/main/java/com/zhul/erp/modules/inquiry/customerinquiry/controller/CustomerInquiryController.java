package com.zhul.erp.modules.inquiry.customerinquiry.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AdvanceCustomerInquiryStatusRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AttachmentVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.ConfirmSplitRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryPageQuery;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.CustomerInquiryVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.InquiryPreviewVO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.SubmitCustomerInquiryRequest;
import com.zhul.erp.modules.inquiry.customerinquiry.service.AttachmentStorageService;
import com.zhul.erp.modules.inquiry.customerinquiry.service.CustomerInquiryService;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderVO;
import com.zhul.erp.modules.inquiry.inquiryorder.service.InquiryOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inquiry/customer-inquiries")
@RequiredArgsConstructor
public class CustomerInquiryController {

    private final CustomerInquiryService customerInquiryService;
    /** 仅用于 P03"已确认"态展示关联询盘单列表，只读查询，不做写操作编排 */
    private final InquiryOrderService inquiryOrderService;
    private final AttachmentStorageService attachmentStorageService;

    @PostMapping("/attachments/image")
    public Result<AttachmentVO> uploadImage(@RequestPart("file") MultipartFile file) {
        return Result.ok(attachmentStorageService.storeImage(file));
    }

    @PostMapping("/attachments/excel")
    public Result<AttachmentVO> uploadExcel(@RequestPart("file") MultipartFile file) {
        return Result.ok(attachmentStorageService.storeExcel(file));
    }

    @PostMapping
    public Result<CustomerInquiryVO> submit(@Valid @RequestBody SubmitCustomerInquiryRequest req) {
        return Result.ok(customerInquiryService.submit(req));
    }

    @PostMapping("/page")
    public Result<PageResult<CustomerInquiryVO>> page(@RequestBody CustomerInquiryPageQuery query) {
        return Result.ok(customerInquiryService.page(query));
    }

    @GetMapping("/{id}")
    public Result<CustomerInquiryVO> getById(@PathVariable Long id) {
        return Result.ok(customerInquiryService.getById(id));
    }

    @PostMapping("/{id}/start-parse")
    public Result<CustomerInquiryVO> startAiParse(@PathVariable Long id) {
        return Result.ok(customerInquiryService.startAiParse(id));
    }

    @PostMapping("/{id}/retry-parse")
    public Result<CustomerInquiryVO> retryParse(@PathVariable Long id) {
        return Result.ok(customerInquiryService.retryParse(id));
    }

    @GetMapping("/{id}/preview")
    public Result<InquiryPreviewVO> getPreview(@PathVariable Long id) {
        return Result.ok(customerInquiryService.getPreview(id));
    }

    @PostMapping("/{id}/confirm-split")
    public Result<Void> confirmSplit(@PathVariable Long id, @Valid @RequestBody ConfirmSplitRequest req) {
        customerInquiryService.confirmSplit(id, req);
        return Result.ok();
    }

    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        customerInquiryService.cancel(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> advanceStatus(@PathVariable Long id, @Valid @RequestBody AdvanceCustomerInquiryStatusRequest req) {
        customerInquiryService.advanceStatus(id, req);
        return Result.ok();
    }

    @GetMapping("/{id}/inquiry-orders")
    public Result<List<InquiryOrderVO>> listInquiryOrders(@PathVariable Long id) {
        return Result.ok(inquiryOrderService.listByCustomerInquiryId(id));
    }
}
