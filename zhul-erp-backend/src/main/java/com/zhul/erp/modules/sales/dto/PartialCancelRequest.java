package com.zhul.erp.modules.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 销售订单按型号部分取消 */
@Data
public class PartialCancelRequest {
    @Valid
    @NotEmpty(message = "请填写要取消的型号和数量")
    private List<Line> items;
    /** 字典 partial_cancel_reason 编码 */
    @NotBlank(message = "请选择取消原因")
    private String reason;
    @Size(max = 200, message = "说明不能超过 200 字")
    private String remark;

    @Data
    public static class Line {
        @NotNull(message = "请选择型号")
        private Long itemId;
        @NotNull(message = "请填写取消数量")
        private Integer quantity;
    }
}
