package com.zhul.erp.modules.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class DeptDeleteCheckVO {
    private boolean blocked;
    private Integer childCount;
    private Integer userCount;
    private List<String> sampleUserNames;
}
