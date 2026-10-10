package com.zhul.erp.modules.purchase.dto;

import lombok.Data;

import java.util.List;

/** 需求池生成采购单的分组预览：按建议供应商（或店铺）分组 */
@Data
public class GeneratePreviewVO {
    private List<Group> groups;

    @Data
    public static class Group {
        /** supplier:ID / shop:渠道:店铺名 / none */
        private String key;
        private String title;
        /** 对应的供应商（建议供应商，或与店铺同名的启用供应商）；没有时为空，须选定或转为供应商 */
        private Long supplierId;
        private String supplierName;
        /** 店铺分组：渠道与店铺名，用于「转为供应商」预填 */
        private Integer channel;
        private String shopName;
        private Boolean matchedByName;
        private List<Line> lines;
    }

    @Data
    public static class Line {
        private Long requirementId;
        private String model;
        private String brand;
        private String soNo;
        private Integer availableQty;
        private String purchaserName;
    }
}
