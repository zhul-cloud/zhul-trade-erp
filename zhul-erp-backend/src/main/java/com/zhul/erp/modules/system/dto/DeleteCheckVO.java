package com.zhul.erp.modules.system.dto;

import lombok.Data;
import java.util.List;

@Data
public class DeleteCheckVO {
    private boolean blocked;
    private List<ChildInfo> children;
    private List<String> referencedRoles;

    @Data
    public static class ChildInfo {
        private String name;
        private Integer type;
        private Integer buttonCount;
    }
}
