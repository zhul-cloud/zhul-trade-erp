package com.zhul.erp.modules.masterdata.customerbackground.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 背调报告（列表与发起结果） */
@Data
public class BackgroundReportVO {
    private Long id;
    private String reportNo;
    private Long customerId;
    /** 来源（1-AI背调、2-上传） */
    private Integer source;
    /** 状态（1-背调中、2-已完成、3-失败） */
    private Integer status;
    private Long requestedBy;
    private String requestedByName;
    private LocalDateTime createTime;
}
