package com.zhul.erp.modules.supply.dto;

import com.zhul.erp.modules.system.doclog.DocLogVO;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 专项 */
@Data
public class ProjectVO {
    private Long id;
    private String name;
    private Long brandId;
    private String brandName;
    private Long categoryId;
    private String categoryName;
    private String goal;
    private Long ownerId;
    private String ownerName;
    private LocalDate dueDate;
    /** 开发供应商目标家数，为空表示只管型号 */
    private Integer supplierTarget;
    /** 本专项下合格的候选数 */
    private int devQualified;
    private Integer status;
    private String resultNote;
    private LocalDateTime closedAt;
    private int solved;
    private int total;
    /** 超期天数，没有超期为 0 */
    private int overdueDays;
    private boolean canClose;
    private List<DemandVO> items;
    private List<BoardVOs.TimelineItem> records;
    private List<DocLogVO> logs;
    /** 开发供应商区（带开发目标或有候选时） */
    private DevVOs.ProjectDev dev;
    private LocalDateTime createTime;
    private String createBy;
}
