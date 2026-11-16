package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

/** 客户列表上方的建档统计：按当前用户的数据范围，不带其他筛选 */
@Data
public class CustomerProfileCountsVO {
    private long all;
    /** 已建档（五个模块都完成） */
    private long done;
    /** 待完善 */
    private long pending;
}
