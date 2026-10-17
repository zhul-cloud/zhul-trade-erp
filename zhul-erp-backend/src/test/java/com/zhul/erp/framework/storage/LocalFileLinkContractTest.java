package com.zhul.erp.framework.storage;

import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** 本地存储方式下的签名链接：不需要登录、签名有效才给文件、支持 Range、过期或篡改返回 403 */
class LocalFileLinkContractTest extends TenantContractSupport {

    private static final String KEY = "tenant/99/finance/202610/0123456789abcdef0123456789abcdef.pdf";

    @Autowired
    private FileStorageService files;

    private Path src;

    @BeforeEach
    void put() throws Exception {
        src = Files.createTempFile("link", ".pdf");
        Files.writeString(src, "%PDF-1.7 0123456789");
        files.storages().local().put(false, KEY, src, "application/pdf");
    }

    @AfterEach
    void remove() throws Exception {
        files.storages().local().delete(false, KEY);
        Files.deleteIfExists(src);
    }

    @Test
    void validLink_servesFileWithOriginalName_andRange() throws Exception {
        String url = files.link(FileStorage.LOCAL, KEY, "水单 1.pdf", true).url();
        MockHttpServletResponse res = mvc.perform(get(URI.create(url))).andReturn().getResponse();
        assertEquals(200, res.getStatus());
        assertEquals("%PDF-1.7 0123456789", res.getContentAsString(StandardCharsets.UTF_8));
        assertTrue(res.getHeader("Content-Disposition").startsWith("attachment; filename*=UTF-8''%E6%B0%B4%E5%8D%95%201.pdf"),
                res.getHeader("Content-Disposition"));

        MockHttpServletResponse part = mvc.perform(get(URI.create(url)).header("Range", "bytes=9-12")).andReturn().getResponse();
        assertEquals(206, part.getStatus());
        assertEquals("0123", part.getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    void tamperedOrExpiredLink_forbidden() throws Exception {
        String url = files.link(FileStorage.LOCAL, KEY, "a.pdf", false).url();
        String tampered = url.replace("tenant%2F99", "tenant%2F98");
        assertEquals(403, mvc.perform(get(URI.create(tampered))).andReturn().getResponse().getStatus());

        String expired = files.link(FileStorage.LOCAL, KEY, "a.pdf", false, Duration.ofSeconds(-1)).url();
        assertEquals(403, mvc.perform(get(URI.create(expired))).andReturn().getResponse().getStatus());
    }
}
