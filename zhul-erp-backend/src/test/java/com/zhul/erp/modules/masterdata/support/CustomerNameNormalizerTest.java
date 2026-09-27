package com.zhul.erp.modules.masterdata.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CustomerNameNormalizerTest {

    @Test
    void suffixAndCaseIgnored() {
        assertThat(CustomerNameNormalizer.normalize("ABC Automation GmbH")).isEqualTo("abcautomation");
        assertThat(CustomerNameNormalizer.normalize("abc automation")).isEqualTo("abcautomation");
    }

    @Test
    void punctuationAndStackedSuffixesIgnored() {
        assertThat(CustomerNameNormalizer.normalize("ABC Trading Co., Ltd.")).isEqualTo("abctrading");
        assertThat(CustomerNameNormalizer.normalize("ABC Trading Company Limited")).isEqualTo("abctrading");
        assertThat(CustomerNameNormalizer.normalize("ABC-Trading  Pty Ltd")).isEqualTo("abctrading");
    }

    @Test
    void suffixWordInMiddleIsKept() {
        assertThat(CustomerNameNormalizer.normalize("AG Controls Inc")).isEqualTo("agcontrols");
    }

    @Test
    void nameMadeOnlyOfSuffixesKeepsOriginalWords() {
        assertThat(CustomerNameNormalizer.normalize("Co Ltd")).isEqualTo("coltd");
    }

    @Test
    void nullAndBlank() {
        assertThat(CustomerNameNormalizer.normalize(null)).isEmpty();
        assertThat(CustomerNameNormalizer.normalize("  ")).isEmpty();
    }
}
