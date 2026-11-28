package com.zhul.erp.modules.website.dto;

import java.util.List;

/**
 * 品类对照的一行：一级品类及其二级品类；siteCategory 为空表示没有设置（二级没有设置时用一级的）。
 * productCount 为站点范围内这个品类的商品数（一级含下级）。
 */
public record CategoryMapVO(Long categoryId, String name, String siteCategory, int productCount, List<CategoryMapVO> children) {
}
