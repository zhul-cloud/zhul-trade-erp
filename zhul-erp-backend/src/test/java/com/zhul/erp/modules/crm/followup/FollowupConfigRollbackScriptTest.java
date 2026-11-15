package com.zhul.erp.modules.crm.followup;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * audit OPS-009：V1.5.6 插入的平台参数都以 sales.followup. 开头，迁移文件末尾注释里的回退语句写成
 * LIKE 'followup.%'，一行也删不掉。已执行的迁移不能再改，回退以 sql/build/rollback 下的同名脚本为准，
 * 这里核对回退脚本删除的正好是迁移插入的那几个平台参数（后续版本插入的 sales.followup.* 不能被连带删掉）。
 */
class FollowupConfigRollbackScriptTest {

    private static final Path MIGRATION =
            Path.of("src/main/resources/db/migration/V1.5.6__customer_followup_menu_config.sql");
    private static final Path ROLLBACK =
            Path.of("../sql/build/rollback/V1.5.6__customer_followup_menu_config_rollback.sql");
    private static final Pattern KEY = Pattern.compile("'(sales\\.followup\\.[A-Za-z0-9.-]+)'");

    private static Set<String> keys(String sql) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = KEY.matcher(sql);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    @Test
    void rollbackDeletesExactlyTheInsertedConfigKeys() throws Exception {
        Set<String> inserted = keys(Files.readString(MIGRATION, StandardCharsets.UTF_8));
        assertEquals(6, inserted.size(), "V1.5.6 插入 6 个平台参数");

        assertTrue(Files.exists(ROLLBACK), "缺少 V1.5.6 的回退脚本：" + ROLLBACK);
        String delete = null;
        for (String line : Files.readAllLines(ROLLBACK, StandardCharsets.UTF_8)) {
            if (!line.trim().startsWith("--") && line.contains("DELETE FROM `sys_config`")) {
                delete = line;
            }
        }
        assertNotNull(delete, "回退脚本要删除本迁移插入的平台参数");
        assertTrue(delete.contains("`tenant_id` = 0"), "只删平台模板：" + delete);
        assertTrue(!delete.contains("LIKE"), "不用前缀匹配，避免删到后续版本的参数：" + delete);
        assertEquals(inserted, keys(delete));
    }
}
