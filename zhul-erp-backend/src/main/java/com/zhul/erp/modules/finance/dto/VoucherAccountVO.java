package com.zhul.erp.modules.finance.dto;

import lombok.Data;

/** 科目设置：一个业务项对应的 T+ 科目 */
@Data
public class VoucherAccountVO {
    private String itemKey;
    private String group;
    private String itemName;
    private String accountCode;
    private String accountName;
}
