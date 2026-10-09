package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 付款条件的一期：比例与到期时点（供应商默认付款条件与采购单共用） */
@Data
public class PaymentTermDTO {
    /** 比例（%），两位小数 */
    private BigDecimal percent;
    /** 到期时点：1-下单后、2-发货前、3-入库后 */
    private Integer trigger;
    /** 入库后天数，仅 trigger=3 时有意义，0 表示到货即付 */
    private Integer days;
}
