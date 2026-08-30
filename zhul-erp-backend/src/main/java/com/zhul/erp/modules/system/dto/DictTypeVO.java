package com.zhul.erp.modules.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DictTypeVO {
    private Integer id;
    private String dictType;
    private String dictName;
    private Integer isBuiltin;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
