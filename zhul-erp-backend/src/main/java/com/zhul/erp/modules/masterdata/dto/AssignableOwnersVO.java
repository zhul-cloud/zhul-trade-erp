package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

import java.util.List;

/** 当前用户可指定 / 转移给的负责人，以及其数据范围类型（ALL / CUSTOM / SELF / NONE） */
@Data
public class AssignableOwnersVO {
    private String scope;
    private List<OwnerOptionVO> owners;

    @Data
    public static class OwnerOptionVO {
        private Long id;
        private String name;
        private String deptName;
    }
}
