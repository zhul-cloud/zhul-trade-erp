package com.zhul.erp.common.utils;

/**
 * 敏感数据展示脱敏。
 */
public final class SensitiveDataMasker {

    private static final int VISIBLE_DIGITS = 4;
    private static final int SHORT_ACCOUNT_LENGTH = 8;

    private SensitiveDataMasker() {
    }

    /**
     * 银行账号脱敏：超过 8 位保留前 4 位和后 4 位，如 {@code 6222 **** **** 8888}；
     * 5 到 8 位只保留后 4 位，如 {@code **** 5678}；4 位及以下原样返回。空值原样返回。
     */
    public static String maskBankAccount(String account) {
        if (account == null || account.length() <= VISIBLE_DIGITS) {
            return account;
        }
        String tail = account.substring(account.length() - VISIBLE_DIGITS);
        if (account.length() <= SHORT_ACCOUNT_LENGTH) {
            return "**** " + tail;
        }
        return account.substring(0, VISIBLE_DIGITS) + " **** **** " + tail;
    }
}
