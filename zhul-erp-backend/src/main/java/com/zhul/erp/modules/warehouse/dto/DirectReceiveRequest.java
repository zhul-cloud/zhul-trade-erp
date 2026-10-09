package com.zhul.erp.modules.warehouse.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/** 直接收货：采购员还没登记发货，仓库选采购单直接验收 */
@Data
public class DirectReceiveRequest {
    @NotNull(message = "请选择采购单")
    private Long poId;
    @NotNull(message = "请填写收货日期")
    private LocalDate receivedDate;
    @Size(max = 300, message = "差异说明不能超过300个字")
    private String note;
    @NotEmpty
    @Valid
    private List<Line> items;
    private List<Long> attachmentIds;

    @Data
    public static class Line {
        @NotNull
        private Long poItemId;
        @NotNull(message = "请填写实收数量")
        private Integer receivedQty;
        @NotNull(message = "请填写合格数量")
        private Integer qualifiedQty;
        @NotNull(message = "请填写不良数量")
        private Integer defectiveQty;
        @Size(max = 300, message = "说明不能超过300个字")
        private String note;
    }
}
