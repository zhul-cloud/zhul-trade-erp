package com.zhul.erp.modules.purchase.dto;

import lombok.Data;

/** 采购需求统计（当前用户的数据范围） */
@Data
public class RequirementStatsVO {
    /** 还没全部下单 */
    private Long open;
    /** 其中已全部排入草稿 */
    private Long inDraft;
    /** 需要生成采购单（还有可下单数量） */
    private Long need;
    /** 未指定采购员（只有数据权限为全部时有值） */
    private Long unassigned;
    /** 草稿采购单 */
    private Long drafts;
}
