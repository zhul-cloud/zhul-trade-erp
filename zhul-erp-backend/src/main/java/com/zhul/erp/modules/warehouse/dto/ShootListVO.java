package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 拍摄任务列表行 */
@Data
public class ShootListVO {
    private Long id;
    private String model;
    private String brand;
    private String category;
    private Long receiptId;
    private String grNo;
    private Long soId;
    private String soNo;
    private Integer status;
    private String statusName;
    private Integer unboxingCount;
    private Integer inspectionCount;
    private Integer photoCount;
    /** 同型号已有完成的素材，可以复用 */
    private Boolean reusable;
    /** 可复用素材的拍摄时间 */
    private LocalDateTime reusableShotAt;
    private Long reusedFromTaskId;
    private String skipReason;
    private String shooterName;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
