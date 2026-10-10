package com.zhul.erp.modules.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

/** 保存编辑中的 PI 版本；型号行以 id 对应已有行，请求里没有的行删除 */
@Data
public class SavePiRequest {
    @NotNull(message = "请填写买方")
    @Valid
    private PartyDTO buyer;
    @Valid
    private PartyDTO consignee;
    /** 把手填的买方存为客户的发票抬头 */
    private Boolean saveBuyerToCustomer;
    /** 把手填的收货人存为客户的收货人 */
    private Boolean saveConsigneeToCustomer;
    @Size(max = 100, message = "交期不能超过 100 个字符")
    private String deliveryTime;
    @Size(max = 200, message = "付款条件不能超过 200 个字符")
    private String paymentTerm;
    @Size(max = 16, message = "贸易术语不能超过 16 个字符")
    private String incoterm;
    @Size(max = 64, message = "术语地点不能超过 64 个字符")
    private String incotermPlace;
    @Size(max = 64, message = "起运港不能超过 64 个字符")
    private String portOfShipment;
    @Size(max = 500, message = "备注不能超过 500 字")
    private String remark;
    /** 有效期至；为空时不改 */
    private java.time.LocalDate validUntil;
    private Integer bankAccountId;
    /** 整单折扣方式（0-无、1-按百分比、2-按金额） */
    private Integer discountType;
    private BigDecimal discountValue;
    @NotNull(message = "请至少保留一个型号")
    @Size(min = 1, max = 300, message = "型号行需要 1–300 行")
    @Valid
    private List<Item> items;
    @Size(max = 20, message = "费用行最多 20 行")
    @Valid
    private List<Fee> fees;

    @Data
    public static class Item {
        @NotNull(message = "型号行缺少 ID")
        private Long id;
        @Size(max = 300, message = "描述不能超过 300 字")
        private String description;
        @Size(max = 300, message = "英文描述不能超过 300 字")
        /** 英文描述（给客户看的单据用；系统内显示中文 description） */
        private String descriptionEn;
        private Integer leadTime;
        @Size(max = 32, message = "质保不能超过 32 个字符")
        private String warranty;
        @NotNull(message = "请填写数量")
        @Min(value = 1, message = "数量需要是正整数")
        private Integer quantity;
        @NotNull(message = "请填写单价")
        private BigDecimal unitPrice;
        @Size(max = 16, message = "HS 编码不能超过 16 个字符")
        private String hsCode;
        @Size(max = 64, message = "原产国不能超过 64 个字符")
        private String originCountry;
        @Size(max = 300, message = "备注不能超过 300 字")
        private String remark;
    }

    @Data
    public static class Fee {
        @NotBlank(message = "请填写费用名称")
        @Size(max = 64, message = "费用名称不能超过 64 个字符")
        private String feeName;
        @NotNull(message = "请填写费用金额")
        private BigDecimal amount;
        @Size(max = 300, message = "备注不能超过 300 字")
        private String remark;
    }
}
