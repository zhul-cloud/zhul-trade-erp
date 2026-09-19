package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.util.List;

/** 规格整体替换：提交后商品的规格集合与 items 完全一致。 */
@Data
public class SaveSpecificationsRequest {
    private List<SpecificationItem> items;

    @Data
    public static class SpecificationItem {
        /** 规格编码，小写字母开头，只含小写字母、数字、下划线；同一集合内唯一 */
        private String specKey;
        private String specLabel;
        private String specValue;
        private String specUnit;
        private String source;
        private Integer verified;
        private Integer sortOrder;
    }
}
