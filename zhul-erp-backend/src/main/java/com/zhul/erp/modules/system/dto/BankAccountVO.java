package com.zhul.erp.modules.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 收款账户；accountNoMasked 只显示后 4 位，accountNo 只在详情 / 单据中给出 */
@Data
public class BankAccountVO {
    private Integer id;
    /** 账户类型：1-对公、2-对私 */
    private Integer accountType;
    private String currencyCode;
    private String bankName;
    private String accountName;
    private String accountNo;
    private String accountNoMasked;
    private String swiftCode;
    private String country;
    private String bankAddress;
    private String bankCode;
    private String branchCode;
    private String remark;
    /** 所属事业部 */
    private Integer businessUnitId;
    private Boolean isDefault;
    private Boolean enabled;
    private LocalDateTime updateTime;
    private String updateBy;
}
