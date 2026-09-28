package com.zhul.erp.modules.masterdata.support;

import java.util.Locale;

/**
 * 客户联系方式的比较键，用于登记商机时查重：
 * 邮箱去空格、转小写；电话与 WhatsApp 只保留数字并去掉开头的 00 国际前缀（+49… 与 0049… 视为相同），
 * 不足 6 位数字时为空串（不参与比较，避免「123」这类占位值误伤）。
 */
public final class ContactKeys {

    private static final int MIN_DIGITS = 6;

    private ContactKeys() {
    }

    public static String email(String email) {
        return email == null ? "" : email.replaceAll("\\s", "").toLowerCase(Locale.ROOT);
    }

    public static String phone(String phone) {
        if (phone == null) {
            return "";
        }
        String digits = phone.replaceAll("[^0-9]", "").replaceFirst("^00", "");
        return digits.length() < MIN_DIGITS ? "" : digits;
    }
}
