package com.zhul.erp.modules.logistics.dto;

import lombok.Data;

/** 货代管理页签上的数量 */
@Data
public class ForwarderCountsVO {
    private long all;
    /** 公司货代 */
    private long company;
    /** 客户指定 */
    private long customer;
}
