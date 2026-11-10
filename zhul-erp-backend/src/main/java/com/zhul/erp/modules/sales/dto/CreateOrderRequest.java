package com.zhul.erp.modules.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 手动创建订单：只填必填项（客户、币种、销售日期、型号 / 数量 / 单价） */
@Data
public class CreateOrderRequest {
    @NotNull(message = "请选择客户")
    private Long customerId;
    /** 不传时为 USD */
    private String currencyCode;
    /** 不传时为当天 */
    private LocalDate salesDate;
    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
    /** PI、合同、客户 PO 等附件：先上传拿到 ID */
    @Size(max = 10, message = "附件最多 10 个")
    private List<Long> attachmentIds;
    /** 费用行（选填，如运费、银行手续费），最多 10 行 */
    @Valid
    @Size(max = 10, message = "费用最多 10 行")
    private List<OrderFeeLine> fees;
    @Valid
    private List<Line> items;

    @Data
    public static class Line {
        /** 商品档案（必填）：型号、品牌、品类以商品档案为准 */
        private Long productId;
        /** 只用于识别空行；保存时以商品档案的型号为准 */
        @Size(max = 128, message = "型号不能超过 128 个字符")
        private String model;
        private Integer quantity;
        private BigDecimal unitPrice;
        /** 采购成本价（CNY），选填 */
        private BigDecimal costPrice;
        /** 采购员，选填 */
        private Long purchaserId;
        /** 1-现货、2-期货，默认现货 */
        private Integer stockType;
    }
}
