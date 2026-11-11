package com.zhul.erp.modules.aftersales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 退货入库单验收；照片等附件上传到退货入库单 */
@Data
public class ReceiveReturnRequest {
    @NotNull(message = "请填写收货日期")
    private LocalDate receivedDate;
    @Size(max = 300, message = "说明不能超过 300 字")
    private String note;
    @Valid
    @NotEmpty(message = "请填写验收数量")
    private List<Line> items;
    private List<Long> attachmentIds;

    @Data
    public static class Line {
        /** 退货入库单行 ID */
        @NotNull(message = "请选择退货行")
        private Long itemId;
        @NotNull(message = "请填写实收数量")
        private Integer receivedQty;
        @NotNull(message = "请填写合格数量")
        private Integer qualifiedQty;
        @Size(max = 300, message = "说明不能超过 300 字")
        private String note;
    }
}
