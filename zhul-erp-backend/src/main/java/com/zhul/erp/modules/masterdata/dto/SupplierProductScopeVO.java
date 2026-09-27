package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

import java.util.List;

/** 供应商的一个主营品牌：正式品牌或待确认品牌，以及细分品类（为空表示全部品类） */
@Data
public class SupplierProductScopeVO {
    /** 正式品牌 ID，待确认品牌为空 */
    private Long brandId;
    private String brandName;
    private boolean pending;
    private List<CategoryRef> categories;

    @Data
    public static class CategoryRef {
        private Long id;
        /** 中文名，没有中文名时为英文名 */
        private String name;
    }
}
