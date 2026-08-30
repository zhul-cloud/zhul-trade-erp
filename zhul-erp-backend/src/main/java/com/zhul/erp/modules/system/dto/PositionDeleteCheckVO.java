package com.zhul.erp.modules.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class PositionDeleteCheckVO {
    private boolean blocked;
    private Integer userCount;
    private List<String> sampleUserNames;
}
