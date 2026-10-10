package com.zhul.erp.modules.crm.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** 商机列表筛选。stage 可为具体阶段编码，或 ACTIVE（进行中）、CLOSED（已结束） */
@Data
public class OpportunityPageQuery {

    private String keyword;
    private Integer sourceChannel;
    private String stage;
    private Long ownerId;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;
    private long page = 1;
    private long pageSize = 10;
}
