package com.zhul.erp.modules.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

/** 新建 PI：按报价单或跨报价单挑型号（同客户、同币种） */
@Data
public class CreatePiRequest {
    @NotEmpty(message = "请选择型号")
    @Size(max = 300, message = "一张 PI 最多 300 个型号")
    @Valid
    private List<PiLineRequest> items;
    /** 是否带入来源报价单的费用行，默认是 */
    private Boolean includeFees;
}
