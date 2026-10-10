package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

/** 出库单列表的查询 */
@Data
public class OutboundPageQuery {
    private Integer page;
    private Integer pageSize;
    /** 出库单号、订单号、客户、型号、快递单号 */
    private String keyword;
    private Integer status;
    private Long forwarderId;
}
