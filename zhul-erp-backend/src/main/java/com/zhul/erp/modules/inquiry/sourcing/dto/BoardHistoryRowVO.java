package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 询价分配「全部」页签的一行（spec inquiry/sourcing-task「全部询价任务」） */
@Data
public class BoardHistoryRowVO {
    private Long id;
    private String taskCode;
    private Long customerInquiryId;
    private String inquiryCode;
    private String brand;
    private Integer itemCount;
    private Boolean urgent;
    /** 采购：当前在办的在前，被改派掉的在后 */
    private List<String> purchaserNames;
    /** 显示状态（1-待分配、2-询价中、3-已回价、4-已报价、5-已关闭） */
    private Integer state;
    private String stateName;
    /** 待分配中由业务员优先处理 */
    private Boolean salesHandling;
    /** 回价时间 */
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
