package com.zhul.erp.modules.system.dto;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class OperateLogQuery {
    private Integer page = 1;
    private Integer pageSize = 20;
    private String operatorName;
    private String menu;
    private String operation;
    private Integer result;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startTime;
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endTime;
}
