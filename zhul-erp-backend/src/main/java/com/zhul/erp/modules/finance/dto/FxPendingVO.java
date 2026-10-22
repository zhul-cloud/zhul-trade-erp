package com.zhul.erp.modules.finance.dto;

import com.zhul.erp.modules.sales.dto.ReceiptRowVO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 某币种的待结汇：余额、估算汇率与按先进先出排好的到账 */
@Data
public class FxPendingVO {
    /** 所属事业部（公司没有事业部时为空） */
    private Integer businessUnitId;
    private String currencyCode;
    private BigDecimal balance;
    private BigDecimal estimateRate;
    /** 估算汇率来自哪张结汇单；为空表示按系统汇率 */
    private String latestSettleNo;
    private LocalDate latestSettleDate;
    private BigDecimal estimateCny;
    private List<ReceiptRowVO> receipts;
}
