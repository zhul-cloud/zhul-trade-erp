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

    /** 身份证号脱敏：保留前 6 位和后 4 位，如 {@code 310101********1234}；10 位及以下原样返回。空值原样返回。 */
    public static String maskIdNo(String idNo) {
        if (idNo == null || idNo.length() <= 10) {
            return idNo;
        }
        return idNo.substring(0, 6) + "*".repeat(idNo.length() - 10) + idNo.substring(idNo.length() - VISIBLE_DIGITS);
    }

    /** 手机号脱敏：11 位保留前 3 位和后 4 位，如 {@code 138****5678}；其他长度原样返回。空值原样返回。 */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
