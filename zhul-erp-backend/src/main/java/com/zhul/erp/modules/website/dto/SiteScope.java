package com.zhul.erp.modules.website.dto;

import java.util.List;

/** 商品范围：按品牌、品类（含下级）筛选，都为空表示本公司全部商品 */
public record SiteScope(List<Long> brandIds, List<Long> categoryIds) {
}
