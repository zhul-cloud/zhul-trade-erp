package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;

/** 实拍任务列表的查询 */
@Data
public class ShootPageQuery {
    private Integer page;
    private Integer pageSize;
    /** 型号、品牌、采购入库单号、订单号 */
    private String keyword;
    private Integer status;
}
