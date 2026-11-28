package com.zhul.erp.modules.website.dto;

/** 站点语言：代码（en、ru）与地址前缀（英文默认空，俄文默认 /ru） */
public record SiteLanguage(String code, String prefix) {
}
