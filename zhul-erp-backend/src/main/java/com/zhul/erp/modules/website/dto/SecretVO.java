package com.zhul.erp.modules.website.dto;

/** 凭据只返回是否已配置与末四位，不返回明文 */
public record SecretVO(boolean configured, String last4) {
    public static final SecretVO NONE = new SecretVO(false, "");
}
