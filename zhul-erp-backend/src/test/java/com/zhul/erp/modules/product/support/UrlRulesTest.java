package com.zhul.erp.modules.product.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlRulesTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://example.com/a.pdf",
            "https://example.com/s7-1200.pdf",
            "HTTPS://EXAMPLE.COM/A.PDF",
            "HtTp://example.com",
            "/datasheets/6ES7212-1AE40-0XB0.pdf",
            "/",
            "/uploads/product/202609/abc.png",
            "https://example.com/a.pdf?x=1&y=2#frag"
    })
    void acceptsHttpHttpsAndSitePaths(String url) {
        assertTrue(UrlRules.isSafe(url), url);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "data:text/html,x",
            "vbscript:x",
            "file:///etc/passwd",
            "ftp://example.com/a.pdf",
            "//evil.example/x.pdf",
            "/\\evil.example/x.pdf",
            "///evil.example",
            "http://",
            "https://",
            "example.com/a.pdf",
            "relative/path.pdf",
            "https://exa mple.com/a.pdf",
            "https://example.com/a\n.pdf",
            "https://example.com/\u0000",
            "/path with space",
            " https://example.com"
    })
    void rejectsEverythingElse(String url) {
        assertFalse(UrlRules.isSafe(url), url);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsNullAndEmpty(String url) {
        assertFalse(UrlRules.isSafe(url));
    }
}
