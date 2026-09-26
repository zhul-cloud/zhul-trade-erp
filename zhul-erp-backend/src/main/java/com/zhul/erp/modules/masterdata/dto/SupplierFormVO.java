package com.zhul.erp.modules.masterdata.dto;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 编辑页取数：字段同 {@link SupplierVO}，但 bankAccount 为明文，接口需要供应商编辑权限 */
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SupplierFormVO extends SupplierVO {
}
