package com.zhul.erp.modules.system.dto;

import lombok.Data;

import java.util.List;

/** 本公司的事业部设置与当前用户的可见范围（前端据此决定是否显示事业部字段与筛选） */
@Data
public class BusinessUnitVO {
    /** 公司是否设置了事业部；没有时各页面不显示事业部 */
    private Boolean enabled;
    private List<Unit> units;
    /** 公共费用最低分摊比例（%） */
    private Integer minSharePercent;
    /** 统计页可选的事业部 */
    private List<Integer> statsUnitIds;
    /** 统计页能否看全公司合计 */
    private Boolean statsTotal;
    /** 当前用户主部门所在的事业部（新建费用单等的默认值），公共部门为空 */
    private Integer myUnitId;

    @Data
    public static class Unit {
        private Integer id;
        private String name;
        /** 开始核算月份 yyyy-MM */
        private String startMonth;
    }
}
