package com.zhul.erp.modules.website.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.modules.website.dto.PreviewVO;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 与上次发布的差异：逐字段比较的边界 */
class SitePreviewChangesTest {

    private final SitePreviewService service = new SitePreviewService(null, null, null, null, null, new ObjectMapper());

    @Test
    void emptyValues_areSame_numbersByValue_realChangeReported() {
        Map<String, Object> now = new HashMap<>();
        now.put("series", "");
        now.put("faq", null);
        now.put("sell_price", 246.1);
        now.put("cat", "servo");
        List<PreviewVO.Change> out = service.changes(
                "{\"series\":null,\"sell_price\":246.10,\"cat\":\"\",\"release\":\"WP1\"}", now);
        assertEquals(1, out.size(), "没有、null、空字符串都算空；数字按数值比较；发布单号不算");
        assertEquals("cat", out.get(0).field());
    }
}
