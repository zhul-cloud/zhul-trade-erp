package com.zhul.erp.modules.sales.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 订单型号行：在 PI 型号行的基础上加跟单信息 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class SalesOrderItemVO extends PiItemVO {
    /** 1-现货、2-期货 */
    private Integer stockType;
    private String progressCode;
    private String progressName;
    private Long purchaserId;
    private String purchaserName;
}
