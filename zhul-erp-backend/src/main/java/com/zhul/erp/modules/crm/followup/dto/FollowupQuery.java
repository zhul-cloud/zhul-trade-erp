package com.zhul.erp.modules.crm.followup.dto;

import lombok.Data;

/**
 * 跟进列表查询：group（1-报价前、2-报价后、3-PI 后）与 stage 二选一或都不传（全部）；nurture=true 只看培育中。
 * ownerScope：me（默认）、all（数据权限内全部），或业务员用户 ID；没有「查看团队」权限时只能看自己。
 */
@Data
public class FollowupQuery {
    private Integer board;
    private Integer group;
    private String stage;
    private Boolean nurture;
    private String ownerScope;
    /** 卡点编码 */
    private String blocker;
    /** 订单履约：只看有异常的 */
    private Boolean exception;
    /** 客户名称、客户编码或单据编号 */
    private String keyword;
    private Integer page;
    private Integer pageSize;
}
