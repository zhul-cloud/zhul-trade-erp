package com.zhul.erp.modules.logistics.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** 发起 / 修改发货通知 */
@Data
public class SaveNoticeRequest {
    /** 发起时必填；修改时忽略 */
    private Long soId;
    @NotNull(message = "请选择货代")
    private Long forwarderId;
    @Size(max = 300, message = "备注不能超过300个字")
    private String note;
    @NotEmpty(message = "请勾选要发货的型号")
    @Valid
    private List<Line> items;

    @Data
    public static class Line {
        @NotNull
        private Long soItemId;
        @NotNull(message = "请填写数量")
        private Integer quantity;
    }
}
