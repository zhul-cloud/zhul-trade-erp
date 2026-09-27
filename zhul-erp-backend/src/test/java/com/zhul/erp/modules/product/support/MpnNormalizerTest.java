package com.zhul.erp.modules.product.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MpnNormalizerTest {

    @Test
    void spacesAndHyphensProduceSameResult() {
        String expected = "6es72141bd230xb0";
        assertEquals(expected, MpnNormalizer.normalize("6ES7 214-1BD23-0XB0"));
        assertEquals(expected, MpnNormalizer.normalize("6ES7214-1BD23-0XB0"));
        assertEquals(expected, MpnNormalizer.normalize("  6ES7214-1BD23-0XB0  "));
    }

    @Test
    void fullWidthCharactersAreFoldedByNfkc() {
        assertEquals("6es7214", MpnNormalizer.normalize("６ＥＳ７２１４"));
    }

    @ParameterizedTest
    @CsvSource({
            "SGMAH-04ADA-TF13, sgmah04adatf13",
            "abc, abc",
            "A/B_C.D, abcd",
            "0, 0"
    })
    void keepsOnlyLowercaseLettersAndDigits(String raw, String expected) {
        assertEquals(expected, MpnNormalizer.normalize(raw));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "---", "- - -", "_./"})
    void inputWithoutLettersOrDigitsBecomesEmpty(String raw) {
        assertEquals("", MpnNormalizer.normalize(raw));
    }

    @Test
    void ideographicSpaceIsRemoved() {
        assertEquals("ab", MpnNormalizer.normalize("a　b"));
    }
}
