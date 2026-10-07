package com.zhul.erp.modules.sales.service;

import com.zhul.erp.modules.sales.dto.ConfirmReceiptRequest;
import com.zhul.erp.modules.sales.dto.PiVO;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/** 收款登记：业务员上传水单（不计入到账），财务登记与作废到账；收款记在 PI 上 */
public interface PaymentReceiptService {

    PiVO uploadSlip(Long piId, List<MultipartFile> files, BigDecimal amount, LocalDate paidDate, String note);

    PiVO deleteSlip(Long piId, Long slipId);

    record SlipFile(Path path, String fileName) {
    }

    SlipFile slipFile(Long piId, Long slipId, int index);

    PiVO confirm(Long piId, ConfirmReceiptRequest req);

    PiVO voidReceipt(Long piId, Long receiptId, String reason);

    /** 可记为手续费的差额上限（PI 币种金额） */
    BigDecimal feeTolerance();
}
