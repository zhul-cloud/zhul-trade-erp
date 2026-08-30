package com.zhul.erp.modules.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class RoleDeleteCheckVO {
    private boolean blocked;
    private boolean builtIn;
    private Integer userCount;
    private List<String> sampleUserNames;
}
