package com.zhul.erp.modules.sales.dto;

import lombok.Data;

/** 收款账户快照（PI 版本保存，之后账户修改不影响已发送版本） */
@Data
public class BankSnapshotDTO {
    private Integer id;
    private String currencyCode;
    private String bankName;
    private String accountName;
    private String accountNo;
    private String swiftCode;
    private String country;
    private String bankAddress;
    private String bankCode;
    private String branchCode;
}
