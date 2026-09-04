package com.zhul.erp.modules.inquiry.inquiryorder.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.AddInquiryOrderSupplierRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.AdvanceInquiryOrderStatusRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.AssignPurchaserRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.ConvertToSupplierRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.CreateInquiryOrderManualRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderItemVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderPageQuery;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderSupplierVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryOrderVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.InquiryTemplateVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.QuoteComparisonVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.QuoteVO;
import com.zhul.erp.modules.inquiry.inquiryorder.dto.RecordQuoteRequest;
import com.zhul.erp.modules.inquiry.inquiryorder.service.InquiryOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inquiry/inquiry-orders")
@RequiredArgsConstructor
public class InquiryOrderController {

    private final InquiryOrderService inquiryOrderService;

    @PostMapping("/manual")
    public Result<InquiryOrderVO> createManual(@Valid @RequestBody CreateInquiryOrderManualRequest req) {
        return Result.ok(inquiryOrderService.createManual(req));
    }

    @PostMapping("/page")
    public Result<PageResult<InquiryOrderVO>> page(@RequestBody InquiryOrderPageQuery query) {
        return Result.ok(inquiryOrderService.page(query));
    }

    @GetMapping("/{id}")
    public Result<InquiryOrderVO> getById(@PathVariable Long id) {
        return Result.ok(inquiryOrderService.getById(id));
    }

    @GetMapping
    public Result<List<InquiryOrderVO>> listByCustomerInquiryId(Long customerInquiryId) {
        return Result.ok(inquiryOrderService.listByCustomerInquiryId(customerInquiryId));
    }

    @GetMapping("/{id}/items")
    public Result<List<InquiryOrderItemVO>> listItems(@PathVariable Long id) {
        return Result.ok(inquiryOrderService.listItems(id));
    }

    @PutMapping("/{id}/assign")
    public Result<Void> assign(@PathVariable Long id, @Valid @RequestBody AssignPurchaserRequest req) {
        inquiryOrderService.assign(id, req);
        return Result.ok();
    }

    @PostMapping("/{id}/suppliers")
    public Result<InquiryOrderSupplierVO> addSupplier(@PathVariable Long id,
                                                        @Valid @RequestBody AddInquiryOrderSupplierRequest req) {
        return Result.ok(inquiryOrderService.addSupplier(id, req));
    }

    @GetMapping("/{id}/suppliers")
    public Result<List<InquiryOrderSupplierVO>> listSuppliers(@PathVariable Long id) {
        return Result.ok(inquiryOrderService.listSuppliers(id));
    }

    @PutMapping("/suppliers/{inquiryOrderSupplierId}/convert-to-formal")
    public Result<InquiryOrderSupplierVO> convertToFormalSupplier(@PathVariable Long inquiryOrderSupplierId,
                                                                    @RequestBody ConvertToSupplierRequest req) {
        return Result.ok(inquiryOrderService.convertToFormalSupplier(inquiryOrderSupplierId, req));
    }

    @PostMapping("/{id}/quotes")
    public Result<QuoteVO> recordQuote(@PathVariable Long id, @Valid @RequestBody RecordQuoteRequest req) {
        return Result.ok(inquiryOrderService.recordQuote(id, req));
    }

    @GetMapping("/{id}/quote-comparison")
    public Result<QuoteComparisonVO> getQuoteComparison(@PathVariable Long id) {
        return Result.ok(inquiryOrderService.getQuoteComparison(id));
    }

    @PutMapping("/{id}/status")
    public Result<Void> advanceStatus(@PathVariable Long id, @Valid @RequestBody AdvanceInquiryOrderStatusRequest req) {
        inquiryOrderService.advanceStatus(id, req);
        return Result.ok();
    }

    @GetMapping("/{id}/templates")
    public Result<InquiryTemplateVO> getTemplates(@PathVariable Long id) {
        return Result.ok(inquiryOrderService.getTemplates(id));
    }
}
