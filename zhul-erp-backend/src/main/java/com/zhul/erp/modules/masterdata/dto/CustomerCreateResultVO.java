package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

/**
 * 客户创建结果。名称重复且未强制新建时，duplicate=true 并携带已有客户信息，
 * 不产生新记录，由前端决定"使用已有客户"（原样提交 existingCustomer）还是
 * "仍然新建"（重新提交并将 force 置为 true）。
 */
@Data
public class CustomerCreateResultVO {
    private boolean duplicate;
    private CustomerVO existingCustomer;
    private CustomerVO createdCustomer;
}
