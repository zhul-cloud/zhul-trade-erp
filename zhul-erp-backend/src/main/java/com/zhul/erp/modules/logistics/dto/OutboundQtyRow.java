package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

/** 订单型号行在出库单上的数量：占用（未撤回）、已交货代（还没出运）、已出运 */
@Data
public class OutboundQtyRow {
    private Long id;
    private Integer occupied;
    private Integer handed;
    private Integer shipped;
}
