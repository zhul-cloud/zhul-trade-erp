package com.zhul.erp.modules.aftersales.dto;

import lombok.Data;

/** 销售退货页、退货入库页查询 */
@Data
public class ReturnQuery {
    private Integer page;
    private Integer pageSize;
    /** 状态；为空为全部 */
    private Integer status;
    /** 处理方式（销售退货页） */
    private Integer handling;
    private Long customerId;
    /** 单号、售后单号、订单号、型号、快递单号 */
    private String keyword;
}
