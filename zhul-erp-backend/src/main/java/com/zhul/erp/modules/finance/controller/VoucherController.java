package com.zhul.erp.modules.finance.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.finance.dto.SaveVoucherAccountsRequest;
import com.zhul.erp.modules.finance.dto.SaveVoucherSupplierRequest;
import com.zhul.erp.modules.finance.dto.VoucherAccountVO;
import com.zhul.erp.modules.finance.dto.VoucherMonthDetailVO;
import com.zhul.erp.modules.finance.dto.VoucherMonthVO;
import com.zhul.erp.modules.finance.dto.VoucherSupplierVO;
import com.zhul.erp.modules.finance.service.VoucherService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** 业务凭证（spec finance/business-voucher）：「财务管理 → 业务凭证」 */
@RestController
@RequestMapping("/api/v1/finance/vouchers")
@RequiredArgsConstructor
public class VoucherController {

    private static final String VIEW = "@perm.canAccessMenu('/finance/vouchers')";
    private static final String EXPORT = VIEW + " and @perm.has('finance:voucher:export')";
    private static final String ACCOUNT = VIEW + " and @perm.has('finance:voucher:account')";

    private final VoucherService service;

    @GetMapping("/months")
    @PreAuthorize(VIEW)
    public Result<List<VoucherMonthVO>> months() {
        return Result.ok(service.months());
    }

    @GetMapping("/months/{month}")
    @PreAuthorize(VIEW)
    public Result<VoucherMonthDetailVO> detail(@PathVariable String month) {
        return Result.ok(service.detail(month));
    }

    /** 上线前已结账、还没有凭证的月份手动生成 */
    @PostMapping("/months/{month}/generate")
    @PreAuthorize(EXPORT)
    public Result<VoucherMonthDetailVO> generate(@PathVariable String month) {
        return Result.ok(service.generateManually(month));
    }

    /** 导出 T+ 凭证导入表；重复导出或核对不相符时先返回 VOUCHER_EXPORT_CONFIRM，确认后带 confirmed=true 再调 */
    @PostMapping("/months/{month}/export")
    @PreAuthorize(EXPORT)
    public ResponseEntity<byte[]> export(@PathVariable String month,
                                         @RequestParam(defaultValue = "false") boolean confirmed) {
        byte[] file = service.export(month, confirmed);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("业务凭证-" + month + ".xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(file);
    }

    @GetMapping("/accounts")
    @PreAuthorize(VIEW)
    public Result<List<VoucherAccountVO>> accounts() {
        return Result.ok(service.accounts());
    }

    @PutMapping("/accounts")
    @PreAuthorize(ACCOUNT)
    public Result<List<VoucherAccountVO>> saveAccounts(@Valid @RequestBody SaveVoucherAccountsRequest req) {
        return Result.ok(service.saveAccounts(req));
    }

    /** 凭证里出现过的供应商与 T+ 往来单位编码 */
    @GetMapping("/suppliers")
    @PreAuthorize(VIEW)
    public Result<List<VoucherSupplierVO>> suppliers(@RequestParam(required = false) String keyword) {
        return Result.ok(service.voucherSuppliers(keyword));
    }

    @PutMapping("/suppliers/{supplierId}")
    @PreAuthorize(ACCOUNT)
    public Result<VoucherSupplierVO> saveSupplier(@PathVariable Long supplierId,
                                                  @Valid @RequestBody SaveVoucherSupplierRequest req) {
        return Result.ok(service.saveSupplierCode(supplierId, req.getPartnerCode()));
    }
}
