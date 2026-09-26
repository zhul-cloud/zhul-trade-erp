package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 更新供应商（只有管理页编辑在用，类型、状态必填）。没有编码字段——编码创建后不可修改 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class UpdateSupplierRequest extends AbstractSupplierRequest {
    /** 状态（0-禁用、1-启用） */
    @NotNull(message = "请选择状态")
    @Min(value = 0, message = "状态不正确")
    @Max(value = 1, message = "状态不正确")
    private Integer status;

    @Override
    @NotNull(message = "请选择供应商类型")
    public Integer getSupplierType() {
        return super.getSupplierType();
    }
}
