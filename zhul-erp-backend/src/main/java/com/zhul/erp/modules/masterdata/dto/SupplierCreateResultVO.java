package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

/**
 * 供应商创建结果。名称重复且未强制新建时，duplicate=true 并携带已有供应商信息，
 * 不产生新记录，由前端决定"使用已有供应商"还是"仍然新建"（重新提交并将 force 置为 true）。
 */
@Data
public class SupplierCreateResultVO {
    private boolean duplicate;
    private SupplierVO existingSupplier;
    private SupplierVO createdSupplier;
}
