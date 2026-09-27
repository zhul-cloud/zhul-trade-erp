package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

/** 收款账户出参：accountNo、payeePhone 在详情里脱敏、编辑取数里为明文；身份证号任何时候都只给脱敏值 */
@Data
public class SupplierBankAccountVO {

    private Long id;
    /** 账户类型（1-对公、2-对私） */
    private Integer accountType;
    private String accountName;
    private String bankName;
    private String accountNo;
    private String payeePhone;
    /** 身份证号脱敏值，如 310101********1234；未填写为空串 */
    private String payeeIdNoMasked;
    /** 是否默认收款账户 */
    private boolean defaultAccount;
}
