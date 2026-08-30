package com.zhul.erp.modules.system.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DictItemVO {
    private Integer id;
    private Integer dictTypeId;
    private String dictType;
    private String itemCode;
    private String itemName;
    private String itemValue;
    private String cssClass;
    private Integer sortOrder;
    private Integer isDefault;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
