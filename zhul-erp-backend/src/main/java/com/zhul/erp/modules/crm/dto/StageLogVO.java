package com.zhul.erp.modules.crm.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class StageLogVO {

    private Long id;
    /** 1-登记、2-阶段变更、3-标记结束、4-重新打开 */
    private Integer action;
    private String fromStage;
    private String fromStageName;
    private String toStage;
    private String toStageName;
    private Integer reason;
    private String reasonLabel;
    private String note;
    private String operator;
    private LocalDateTime createTime;
}
