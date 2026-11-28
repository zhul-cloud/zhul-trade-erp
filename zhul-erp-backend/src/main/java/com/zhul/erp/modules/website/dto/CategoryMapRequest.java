package com.zhul.erp.modules.website.dto;

import java.util.List;

/** 保存品类对照：整体替换；siteCategory 为空表示不设置 */
public record CategoryMapRequest(List<Item> items) {
    public record Item(Long categoryId, String siteCategory) {
    }
}
