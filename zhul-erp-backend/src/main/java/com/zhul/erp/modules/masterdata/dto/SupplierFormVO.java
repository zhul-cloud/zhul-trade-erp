package com.zhul.erp.modules.masterdata.dto;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 编辑页取数：字段同 {@link SupplierVO}，但收款账户的账号、手机号为明文（身份证号仍脱敏），接口需要供应商编辑权限 */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SupplierFormVO extends SupplierVO {
}
