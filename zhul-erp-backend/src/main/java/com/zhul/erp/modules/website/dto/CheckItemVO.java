package com.zhul.erp.modules.website.dto;

/** 检测连接的一项：repo 仓库、adapter 适配脚本、deploy 部署服务器、url 网址 */
public record CheckItemVO(String key, String name, boolean ok, String message) {
}
