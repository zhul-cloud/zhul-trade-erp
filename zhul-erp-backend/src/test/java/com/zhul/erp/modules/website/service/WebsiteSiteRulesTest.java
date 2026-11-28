package com.zhul.erp.modules.website.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.dto.SiteLanguage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 站点配置的校验规则（spec website-site「站点」）与凭据末四位 */
class WebsiteSiteRulesTest {

    @Test
    void url_normalizedAndPathRejected() {
        assertEquals("https://fouwell.com", WebsiteSiteService.normalizeUrl(" HTTPS://Fouwell.com/ "));
        assertEquals("https://shop.example.co:8443", WebsiteSiteService.normalizeUrl("https://shop.example.co:8443"));
        for (String bad : List.of("http://fouwell.com", "https://fouwell.com/en", "fouwell.com", "https://", "https://user@fouwell.com", "")) {
            assertThrows(BizException.class, () -> WebsiteSiteService.normalizeUrl(bad), bad);
        }
    }

    @Test
    void languages_atLeastOne_uniqueCodesAndPrefixes() {
        assertEquals(List.of(new SiteLanguage("en", ""), new SiteLanguage("ru", "/ru")),
                WebsiteSiteService.languages(List.of(new SiteLanguage("EN", ""), new SiteLanguage("ru", "/ru/"))));
        assertThrows(BizException.class, () -> WebsiteSiteService.languages(List.of()));
        assertThrows(BizException.class, () -> WebsiteSiteService.languages(List.of(new SiteLanguage("de", "/de"))));
        assertThrows(BizException.class, () -> WebsiteSiteService.languages(List.of(new SiteLanguage("en", ""), new SiteLanguage("en", "/en"))));
        assertThrows(BizException.class, () -> WebsiteSiteService.languages(List.of(new SiteLanguage("en", ""), new SiteLanguage("ru", ""))));
        assertThrows(BizException.class, () -> WebsiteSiteService.languages(List.of(new SiteLanguage("ru", "ru"))));
    }

    @Test
    void siteCode_isHostWithoutPort() {
        assertEquals("fouwell.com", WebsiteSiteService.siteCode("https://fouwell.com"));
        assertEquals("shop.example.co", WebsiteSiteService.siteCode("https://shop.example.co:8443"));
    }

    @Test
    void last4_ofTokenAndPrivateKeyBody() {
        assertEquals("a1b2", SiteSecrets.last4(WebsiteConstants.SECRET_REPO_TOKEN, "ghp_xxxxxxxxa1b2"));
        String key = SiteSecrets.normalize(WebsiteConstants.SECRET_DEPLOY_KEY,
                "-----BEGIN OPENSSH PRIVATE KEY-----\r\nAAAAB3NzaC1\r\nZZQx9k\r\n-----END OPENSSH PRIVATE KEY-----\r\n\r\n");
        assertEquals("-----BEGIN OPENSSH PRIVATE KEY-----\nAAAAB3NzaC1\nZZQx9k\n-----END OPENSSH PRIVATE KEY-----\n", key);
        assertEquals("Qx9k", SiteSecrets.last4(WebsiteConstants.SECRET_DEPLOY_KEY, key));
        assertEquals("", SiteSecrets.last4(WebsiteConstants.SECRET_INDEXNOW_KEY, "abcd"), "太短不显示末四位");
    }
}
