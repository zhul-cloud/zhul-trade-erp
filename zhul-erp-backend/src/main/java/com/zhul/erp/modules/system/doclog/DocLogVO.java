package com.zhul.erp.modules.system.doclog;

import lombok.Data;

import java.time.LocalDateTime;

/** 单据详情页的一条操作记录 */
@Data
public class DocLogVO {
    private Long id;
    private String action;
    private String content;
    private String operatorName;
    private LocalDateTime createTime;
}
