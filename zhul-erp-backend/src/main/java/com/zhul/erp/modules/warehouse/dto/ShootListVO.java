package com.zhul.erp.modules.warehouse.dto;

import lombok.Data;
import java.time.LocalDateTime;

/** 实拍任务列表行 */
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
    /** 已上传的营销图张数 */
    private Integer photoCount;
    /** 完成需要的营销图张数（6） */
    private Integer needPhotos;
    private Long reusedFromTaskId;
    private String skipReason;
    private String shooterName;
    private LocalDateTime completedAt;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
