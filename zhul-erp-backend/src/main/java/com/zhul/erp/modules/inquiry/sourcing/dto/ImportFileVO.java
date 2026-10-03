package com.zhul.erp.modules.inquiry.sourcing.dto;

import lombok.Data;

import java.util.List;

/** 导入询价结果：一个文件的解析结果 */
@Data
public class ImportFileVO {
    private String fileName;
    private String fileKey;
    /** 文件被整体拒收时的原因 */
    private String rejectReason;
    private Long taskId;
    private String taskCode;
    private String brand;
    private String category;
    private Long assigneeId;
    private String assigneeName;
    private List<ImportRowVO> rows;
}
