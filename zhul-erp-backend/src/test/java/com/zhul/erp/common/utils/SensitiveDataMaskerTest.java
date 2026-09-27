package com.zhul.erp.common.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    @Test
    void maskBankAccount_nullOrEmpty_returnsAsIs() {
        assertThat(SensitiveDataMasker.maskBankAccount(null)).isNull();
        assertThat(SensitiveDataMasker.maskBankAccount("")).isEmpty();
    }

    @Test
    void maskBankAccount_fourDigitsOrLess_returnsAsIs() {
        assertThat(SensitiveDataMasker.maskBankAccount("1234")).isEqualTo("1234");
    }

    @Test
    void maskBankAccount_eightDigits_keepsOnlyLastFour() {
        assertThat(SensitiveDataMasker.maskBankAccount("12345678")).isEqualTo("**** 5678");
    }

    @Test
    void maskBankAccount_nineteenDigits_keepsFirstAndLastFour() {
        assertThat(SensitiveDataMasker.maskBankAccount("6222021234560008888")).isEqualTo("6222 **** **** 8888");
    }

    @Test
    void maskBankAccount_thirtyDigits_keepsFirstAndLastFour() {
        assertThat(SensitiveDataMasker.maskBankAccount("123456789012345678901234567890"))
                .isEqualTo("1234 **** **** 7890");
    }
}
