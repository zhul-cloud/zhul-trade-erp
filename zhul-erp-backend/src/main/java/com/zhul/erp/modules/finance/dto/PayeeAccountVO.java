package com.zhul.erp.modules.finance.dto;

import lombok.Data;

/** 登记付款时可选的对方收款账户：账号为明文（方便转账），手机号脱敏，不含身份证号 */
@Data
public class PayeeAccountVO {
    private Long id;
    /** 账户类型（1-对公、2-对私） */
    private Integer accountType;
    private String accountName;
    private String bankName;
    private String accountNo;
    private String payeePhoneMasked;
    private boolean defaultAccount;
}
