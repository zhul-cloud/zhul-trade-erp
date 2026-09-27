package com.zhul.erp.modules.masterdata.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * 新增供应商。编码一律由系统生成（SUP + 5 位流水号）；管理页新增时类型必填由前端保证，询盘内联创建只传名称等少量字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SaveSupplierRequest extends AbstractSupplierRequest {
    /** 状态（0-禁用、1-启用），为空时默认启用 */
    @Min(value = 0, message = "状态不正确")
    @Max(value = 1, message = "状态不正确")
    private Integer status;
    /** 名称与已有供应商重复时，是否仍然强制新建（默认 false：先返回重复提示，不新建） */
    private boolean force = false;
}
