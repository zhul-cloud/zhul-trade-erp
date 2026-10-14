package com.zhul.erp.modules.finance.dto;

import lombok.Data;

/** 费用单分摊时可选的订单型号行 */
@Data
public class SoItemOptionVO {
    private Long soId;
    private String soNo;
    private Long soItemId;
    private Integer lineNo;
    private String model;
    private String brand;
    private Integer quantity;
}
