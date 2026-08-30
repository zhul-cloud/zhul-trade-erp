package com.zhul.erp.modules.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperateLogVO {
    private Long id;
    private String operatorName;
    private String menu;
    private String operation;
    private Integer result;
    private String ip;
    private LocalDateTime operateTime;
}
