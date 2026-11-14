package com.zhul.erp.modules.finance.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 其他收入 */
@Data
public class OtherIncomeVO {
    private Long id;
    private String incomeNo;
    private LocalDate incomeDate;
    /** 所属事业部；为空表示「公共」 */
    private Integer businessUnitId;
    private String category;
    private String categoryName;
    private BigDecimal amount;
    private String remark;
    private Integer status;
    private String voidReason;
    private List<PayableVO.AttachmentRef> attachments;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
    /** 历史迁移的单据（只读） */
    private Boolean migrated;
    /** 来源：空-手动登记、REFUND-余额提现自动生成的汇兑损益（不能修改、作废） */
    private String sourceType;
    private Long sourceId;
    /** 以下只在详情返回（change finance-expense-docs） */
    private List<com.zhul.erp.modules.attachment.dto.AttachmentVO> files;
    private List<com.zhul.erp.modules.system.doclog.DocRefVO> related;
    private List<com.zhul.erp.modules.system.doclog.DocLogVO> logs;
    private Boolean editable;
}
