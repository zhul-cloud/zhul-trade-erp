package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

import java.util.List;

/** 省/市/区行政区划节点；区县一级没有 children */
@Data
public class RegionVO {
    /** 行政区划代码，如 310115 */
    private String code;
    /** 名称，如 浦东新区 */
    private String name;
    private List<RegionVO> children;
}
