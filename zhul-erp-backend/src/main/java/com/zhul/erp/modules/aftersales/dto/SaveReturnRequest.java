package com.zhul.erp.modules.aftersales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 发起 / 修改退货单 */
@Data
public class SaveReturnRequest {
    /** 发起时必填 */
    private Long afterSalesId;
    @Valid
    @NotEmpty(message = "请选择退货的型号")
    private List<Line> items;
    @Size(max = 32, message = "快递公司不能超过 32 字")
    private String carrier;
    @Size(max = 64, message = "快递单号不能超过 64 字")
    private String trackingNo;
    @Size(max = 300, message = "说明不能超过 300 字")
    private String remark;

    @Data
    public static class Line {
        @NotNull(message = "请选择型号")
        private Long soItemId;
        @NotNull(message = "请填写退回数量")
        private Integer quantity;
    }
}
