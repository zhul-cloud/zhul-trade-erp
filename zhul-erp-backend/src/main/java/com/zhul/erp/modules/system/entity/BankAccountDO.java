package com.zhul.erp.modules.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.zhul.erp.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 收款银行账户（按租户，每币种一个默认） */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = "accountNo")
@TableName("tenant_bank_account")
public class BankAccountDO extends BaseEntity {
    private Integer tenantId;
    /** 所属事业部（department.id）；NULL：公共，或公司没有设置事业部 */
    private Integer businessUnitId;
    /** 账户类型：1-对公、2-对私 */
    private Integer accountType;
    private String currencyCode;
    private String bankName;
    private String accountName;
    private String accountNo;
    private String swiftCode;
    private String country;
    private String bankAddress;
    private String bankCode;
    private String branchCode;
    private String remark;
    private Integer isDefault;
    /** 状态（0-停用、1-启用） */
    private Integer status;
}
