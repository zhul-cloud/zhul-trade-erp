package com.zhul.erp.modules.inventory.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 仓库档案一行 */
@Data
public class InvWarehouseVO {
    private Long id;
    private String code;
    private String name;
    /** 1-自有仓、2-货代仓 */
    private Integer type;
    private String typeName;
    private Long forwarderId;
    /** 在库件数（合格 + 不良） */
    private Integer onHand;
    /** 0-停用、1-启用 */
    private Integer status;
    private String statusName;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
