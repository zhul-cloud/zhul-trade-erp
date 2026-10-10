package com.zhul.erp.modules.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 收款账户；accountNoMasked 只显示后 4 位，accountNo 只在详情 / 单据中给出 */
@Data
public class BankAccountVO {
    private Integer id;
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
    private Boolean isDefault;
    private Boolean enabled;
    private LocalDateTime updateTime;
    private String updateBy;
}
