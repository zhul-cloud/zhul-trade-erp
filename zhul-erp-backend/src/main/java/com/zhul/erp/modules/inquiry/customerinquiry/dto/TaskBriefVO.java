package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 询盘单详情中的询价任务 */
@Data
public class TaskBriefVO {
    private Long id;
    private String taskCode;
    private String brand;
    private String category;
    private Integer itemCount;
    private Integer pricedCount;
    private Integer status;
    private Boolean timeout;
    private LocalDateTime firstAssignedAt;
    private List<String> assigneeNames;
    private Integer returnReason;
    private String returnNote;
    /** 待业务处理：距自动转给采购的分钟数 */
    private Long salesRemainingMinutes;
    /** 当前账号是询盘负责人且任务待业务处理，可以转采购 */
    private Boolean canTransfer;
    /** 可以转采购且有「我的询价」菜单，可以认领 */
    private Boolean canClaim;
    /** 业务员自询 */
    private Boolean selfSourcing;
    /** 待分配且延后到以后：延后到哪天、原因 */
    private java.time.LocalDate deferUntil;
    private String deferReason;
}
