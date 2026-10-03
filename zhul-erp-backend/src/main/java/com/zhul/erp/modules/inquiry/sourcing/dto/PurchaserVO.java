package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

/** 采购人员及其负载 */
@Data
public class PurchaserVO {
    private Long id;
    private String name;
    /** 是否兼职采购 */
    private Boolean partTime;
    private Integer activeTasks;
    private Integer timeoutTasks;
}
