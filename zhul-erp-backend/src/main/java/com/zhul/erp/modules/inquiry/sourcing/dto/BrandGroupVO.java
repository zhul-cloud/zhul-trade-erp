package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

/** 按品牌分组的汇总，覆盖全部页，用于分页后的组头 */
@Data
public class BrandGroupVO {
    private String brand;
    /** 该品牌在当前页签的任务总数（不止本页） */
    private Integer total;
    private Integer urgent;
    private Integer timeout;
    /** 待分配：最久已等分钟数；待认领：最早自动转给采购的剩余分钟数 */
    private Long minutes;
}
