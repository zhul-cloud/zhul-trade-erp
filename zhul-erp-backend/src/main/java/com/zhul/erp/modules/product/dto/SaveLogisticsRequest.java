package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 物流信息整体覆盖保存：未填写的数值传 null，保存后仍为空而不是 0。 */
@Data
public class SaveLogisticsRequest {
    /** 净重（kg，保留 3 位小数），大于 0 */
    private BigDecimal netWeightKg;
    /** 毛重（kg，含包装），填写时不得小于净重 */
    private BigDecimal grossWeightKg;
    /** 单品长宽高（mm，保留 1 位小数），大于 0 */
    private BigDecimal lengthMm;
    private BigDecimal widthMm;
    private BigDecimal heightMm;
    private String packageType;
    private BigDecimal packageLengthMm;
    private BigDecimal packageWidthMm;
    private BigDecimal packageHeightMm;
    /** 每个包装内的件数，大于 0 */
    private Integer packageQuantity;
    /** 是否危险品或含电池等限运品（0/1），缺省 0 */
    private Integer isDangerous;
    private String shippingNote;
}
