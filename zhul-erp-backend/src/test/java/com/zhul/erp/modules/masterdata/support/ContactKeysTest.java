package com.zhul.erp.modules.masterdata.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContactKeysTest {

    @Test
    void email_trimsSpacesAndLowercases() {
        assertThat(ContactKeys.email(" John.Doe@Example.COM ")).isEqualTo("john.doe@example.com");
        assertThat(ContactKeys.email(null)).isEmpty();
    }

    @Test
    void phone_keepsDigits_dropsLeading00_ignoresShortValues() {
        assertThat(ContactKeys.phone("+49 151 2345-6789")).isEqualTo("4915123456789");
        assertThat(ContactKeys.phone("0049 (151) 23456789")).isEqualTo("4915123456789");
        assertThat(ContactKeys.phone("123")).isEmpty();
        assertThat(ContactKeys.phone("")).isEmpty();
        assertThat(ContactKeys.phone(null)).isEmpty();
    }
}
