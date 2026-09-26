package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 待确认品牌汇总（跨租户）：只有名称和计数，不含租户、供应商信息 */
@Data
public class PendingBrandVO {
    /** 比较键，确认操作时回传 */
    private String pendingKey;
    /** 名称（同一比较键下取最早出现的写法） */
    private String name;
    private Integer supplierCount;
    private LocalDateTime firstSeen;
}
