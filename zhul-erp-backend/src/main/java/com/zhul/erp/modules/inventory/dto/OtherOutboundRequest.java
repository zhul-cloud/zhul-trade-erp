package com.zhul.erp.modules.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/** 新建其他出库单（spec warehouse/other-outbound） */
@Data
public class OtherOutboundRequest {
    /** 出库类型（1-报废、2-转样品、3-货代丢失、4-货损） */
    @NotNull(message = "请选择出库类型")
    private Integer outboundType;
    @NotNull(message = "请选择仓库")
    private Long warehouseId;
    /** 出库日期，不填为今天 */
    private LocalDate outboundDate;
    @NotBlank(message = "请填写原因")
    @Size(max = 200, message = "原因不能超过 200 字")
    private String reason;
    @NotEmpty(message = "请选择要出库的商品")
    @Size(max = 50, message = "一次最多出库 50 行")
    @Valid
    private List<Line> lines;

    @Data
    public static class Line {
        @NotNull(message = "请选择批次")
        private Long batchId;
        /** 库存状态（1-合格、2-不良） */
        @NotNull(message = "请选择合格或不良")
        private Integer stockStatus;
        @NotNull(message = "请填写数量")
        @Min(value = 1, message = "数量至少为 1")
        private Integer quantity;
    }
}
