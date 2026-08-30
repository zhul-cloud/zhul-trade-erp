package com.zhul.erp.modules.system.dto;

import lombok.Data;

@Data
public class DictTypeDeleteCheckVO {
    private boolean blocked;
    private boolean builtin;
    private Integer itemCount;
}
