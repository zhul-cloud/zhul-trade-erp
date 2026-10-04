package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.util.List;

/** 我的询价任务详情（只含新老客户、客户名称与询盘等级，不含联系方式与原始内容；兼职采购不含客户名称） */
@Data
public class MyTaskDetailVO {
    private MyTaskVO task;
    private List<MyTaskItemVO> items;
    /** 本人回价是否需要采购负责人审核（兼职采购）：是则不显示「推荐」列 */
    private Boolean reviewRequired;
}
