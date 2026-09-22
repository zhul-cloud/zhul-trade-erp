package com.zhul.erp.modules.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PositionVO {
    private Integer id;
    private String code;
    private String name;
    private Integer sort;
    private Integer status;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
    private String updateBy;
    private LocalDateTime updateTime;
}
