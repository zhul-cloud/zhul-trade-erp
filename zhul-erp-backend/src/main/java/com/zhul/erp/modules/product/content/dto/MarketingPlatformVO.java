package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

import java.util.List;

/** 营销平台与本公司是否启用 */
@Data
public class MarketingPlatformVO {
    /** alibaba、mic、site、social */
    private String code;
    private String label;
    private List<String> langs;
    private boolean enabled;
}
