package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** 待报销项（或报销明细）：来源、说明、金额（人民币，收到退款为负数）与截图 */
@Data
public class AdvanceItemVO {
    /** 1-采购货款、2-国内快递运费、3-退货运费、4-费用单、5-收到退款 */
    private Integer sourceType;
    private Long sourceId;
    private String sourceNo;
    private LocalDate occurDate;
    private String description;
    private BigDecimal amount;
    /** 截图所在的附件归属类型：PAYMENT、COURIER_PROOF、RETURN_PROOF、EXPENSE */
    private String proofOwnerType;
    /** 截图所在的归属 ID（来源 ID） */
    private Long proofOwnerId;
    private List<PayableVO.AttachmentRef> proofs = new ArrayList<>();
    /** 截图可以在这里补传（国内快递、退货运费） */
    private boolean proofUploadable;
}
