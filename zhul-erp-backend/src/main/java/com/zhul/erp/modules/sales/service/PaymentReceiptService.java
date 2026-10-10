package com.zhul.erp.modules.sales.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.sales.dto.ConfirmReceiptRequest;
import com.zhul.erp.modules.sales.dto.PiVO;
import com.zhul.erp.modules.sales.dto.ReceiptDeskQuery;
import com.zhul.erp.modules.sales.dto.ReceiptDeskRowVO;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/** 收款登记：业务员上传水单（不计入到账），财务登记与作废到账；收款记在 PI 上 */
public interface PaymentReceiptService {

    PiVO uploadSlip(Long piId, List<MultipartFile> files, BigDecimal amount, LocalDate paidDate, String paymentMethod, String note);

    PiVO deleteSlip(Long piId, Long slipId);

    record SlipFile(Path path, String fileName) {
    }

    SlipFile slipFile(Long piId, Long slipId, int index);

    PiVO confirm(Long piId, ConfirmReceiptRequest req);

    PiVO voidReceipt(Long piId, Long receiptId, String reason);

    /** 业务员登记平台收款（线上付款方式），登记即计入到账 */
    PiVO platformReceipt(Long piId, com.zhul.erp.modules.sales.dto.PlatformReceiptRequest req);

    /** 可认领到这张 PI 的未认领到账（同币种） */
    List<com.zhul.erp.modules.sales.dto.ReceiptRowVO> claimable(Long piId);

    /** 把一笔未认领到账认领到 PI */
    PiVO claim(Long piId, com.zhul.erp.modules.sales.dto.ClaimReceiptRequest req);

    /** 到账登记工作列表：默认只列有待确认水单、或已有到账但未收齐的 PI */
    PageResult<ReceiptDeskRowVO> desk(ReceiptDeskQuery query);

    /** 可记为手续费的差额上限（PI 币种金额） */
    BigDecimal feeTolerance();

    // ---------------------------------------------------------------- 手动创建的订单：收款直接登记在订单上

    com.zhul.erp.modules.sales.dto.SalesOrderVO uploadOrderSlip(Long soId, List<MultipartFile> files, BigDecimal amount, LocalDate paidDate,
                                                               String paymentMethod, String note);

    com.zhul.erp.modules.sales.dto.SalesOrderVO deleteOrderSlip(Long soId, Long slipId);

    SlipFile orderSlipFile(Long soId, Long slipId, int index);

    com.zhul.erp.modules.sales.dto.SalesOrderVO confirmOrder(Long soId, ConfirmReceiptRequest req);

    com.zhul.erp.modules.sales.dto.SalesOrderVO voidOrderReceipt(Long soId, Long receiptId, String reason);

    com.zhul.erp.modules.sales.dto.SalesOrderVO orderPlatformReceipt(Long soId, com.zhul.erp.modules.sales.dto.PlatformReceiptRequest req);

    List<com.zhul.erp.modules.sales.dto.ReceiptRowVO> orderClaimable(Long soId);

    com.zhul.erp.modules.sales.dto.SalesOrderVO claimToOrder(Long soId, com.zhul.erp.modules.sales.dto.ClaimReceiptRequest req);
}
