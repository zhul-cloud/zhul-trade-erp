package com.zhul.erp.modules.product.content;

import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** V1.2.57 关键词迁移：按平台存的关键词合并成总库（同词不分大小写、档位取最高），平台关系变成选用 */
class KeywordMigrationIntegrationTest extends IntegrationTestBase {

    private static final long PRODUCT = 990001L;
    private static final int TENANT = 1009;

    @AfterEach
    void cleanup() {
        jdbc.update("delete from product_keyword_use where product_id = ?", PRODUCT);
        jdbc.update("delete from product_keyword where product_id = ?", PRODUCT);
        jdbc.update("delete from product_platform_keyword where product_id = ?", PRODUCT);
    }

    /** 迁移脚本里的两条关键词迁移语句（INSERT INTO product_keyword / product_keyword_use） */
    private static List<String> migrationStatements() throws IOException {
        String sql;
        try (InputStream in = new ClassPathResource("db/migration/V1.2.57__content_schema_v2.sql").getInputStream()) {
            sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<String> result = new ArrayList<>();
        for (String stmt : sql.split(";")) {
            StringBuilder clean = new StringBuilder();
            for (String line : stmt.split("\n")) {
                if (!line.trim().startsWith("--")) {
                    clean.append(line).append('\n');
                }
            }
            String s = clean.toString().trim();
            if (s.startsWith("INSERT INTO `product_keyword")) {
                result.add(s);
            }
        }
        assertEquals(2, result.size(), "迁移脚本里应有两条关键词迁移语句");
        return result;
    }

    private void legacy(String platform, String keyword, int tier, int sort) {
        jdbc.update("insert into product_platform_keyword (tenant_id, product_id, platform, lang, keyword, tier, sort_order) "
                + "values (?, ?, ?, 'en', ?, ?, ?)", TENANT, PRODUCT, platform, keyword, tier, sort);
    }

    @Test
    void mergeSameWordTakeHighestTierAndKeepPlatformSelections() throws IOException {
        legacy("alibaba", "Delta X1", 1, 1);
        legacy("alibaba", "X1 drive", 2, 1);
        legacy("mic", "delta x1", 2, 1);
        legacy("mic", "X1 price", 3, 1);
        legacy("mic", "DELTA X1 ", 3, 2);
        for (String s : migrationStatements()) {
            jdbc.execute(s);
        }

        List<Map<String, Object>> library = jdbc.queryForList("select keyword, tier, sort_order from product_keyword "
                + "where product_id = ? and deleted_at is null order by sort_order", PRODUCT);
        assertEquals(3, library.size(), library.toString());
        assertEquals("Delta X1", library.get(0).get("keyword"), "同词合并，文字取最高档里最先出现的写法");
        assertEquals(1, ((Number) library.get(0).get("tier")).intValue(), "档位取最高");
        assertEquals("X1 drive", library.get(1).get("keyword"));
        assertEquals("X1 price", library.get(2).get("keyword"));

        List<String> alibaba = jdbc.queryForList("select k.keyword from product_keyword_use u join product_keyword k on k.id = u.keyword_id "
                + "where u.product_id = ? and u.platform = 'alibaba' order by u.sort_order", String.class, PRODUCT);
        assertEquals(List.of("Delta X1", "X1 drive"), alibaba);
        List<String> mic = jdbc.queryForList("select k.keyword from product_keyword_use u join product_keyword k on k.id = u.keyword_id "
                + "where u.product_id = ? and u.platform = 'mic' order by u.sort_order", String.class, PRODUCT);
        assertEquals(List.of("Delta X1", "X1 price"), mic, "同一平台里大小写不同的同一个词只留一条选用");
        assertEquals(TENANT, jdbc.queryForObject("select distinct tenant_id from product_keyword where product_id = ?",
                Integer.class, PRODUCT));
    }
}
