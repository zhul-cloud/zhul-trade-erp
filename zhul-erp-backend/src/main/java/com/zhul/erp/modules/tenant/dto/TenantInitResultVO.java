package com.zhul.erp.modules.tenant.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 租户数据初始化结果：每类数据新建与已存在的数量、商品主数据范围、需要手工完成的事项 */
@Data
public class TenantInitResultVO {

    private Integer tenantId;
    private String tenantName;
    /** 部门、岗位、角色、汇率 */
    private List<Item> items = new ArrayList<>();
    /** 租户行业编码；为空表示还没有选择行业 */
    private String industryCode;
    private String industryName;
    private long brandCount;
    private long categoryCount;
    private long seriesCount;
    /** 需要手工完成的事项 */
    private List<String> todos = new ArrayList<>();

    @Data
    public static class Item {
        /** 部门、岗位、角色、汇率 */
        private String type;
        private int created;
        private int existed;
        /** 补充说明，如「两个事业部已标记为独立核算，2026-10 起核算」 */
        private String note = "";
    }
}
