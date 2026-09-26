package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 新增供应商。管理页新增时编码、类型必填由前端保证；询盘内联创建只传名称等少量字段，编码为空时系统自动生成。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SaveSupplierRequest extends AbstractSupplierRequest {
    @Pattern(regexp = "^[A-Za-z0-9]{0,20}$", message = "供应商编码只能包含字母和数字，最多20位")
    private String supplierCode;
    /** 状态（0-禁用、1-启用），为空时默认启用 */
    @Min(value = 0, message = "状态不正确")
    @Max(value = 1, message = "状态不正确")
    private Integer status;
    /** 名称与已有供应商重复时，是否仍然强制新建（默认 false：先返回重复提示，不新建） */
    private boolean force = false;
}
