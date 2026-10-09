package com.zhul.erp.modules.warehouse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/** 登记 / 修改发货单 */
@Data
public class SaveShipmentRequest {
    /** 登记时必填；修改时忽略 */
    private Long poId;
    @Size(max = 32, message = "快递公司不能超过32个字")
    private String carrier;
    @Size(max = 64, message = "快递单号不能超过64个字")
    private String trackingNo;
    @NotNull(message = "请填写发货日期")
    private LocalDate shipDate;
    /** 预计到货日期：为空时按快递时效估算 */
    private LocalDate expectedArrivalDate;
    @Size(max = 300, message = "备注不能超过300个字")
    private String note;
    @NotEmpty(message = "请选择发货的型号")
    @Valid
    private List<Line> items;
    private List<Long> attachmentIds;

    @Data
    public static class Line {
        @NotNull
        private Long poItemId;
        @NotNull(message = "请填写发货数量")
        private Integer quantity;
    }
}
