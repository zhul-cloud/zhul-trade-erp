package com.zhul.erp.modules.system.crypto;

import cn.hutool.crypto.CryptoException;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.utils.AesUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动时把旧格式（AES/ECB）密文重新加密为 v2（AES-GCM），见 openspec system/data-encryption。
 * 用 JdbcTemplate 直接读写，跨租户、含已软删除的行；每次启动都执行，已是 v2 的不再处理。
 * 单条解不开时只记表名和 id，保持原样继续；日志不输出任何密文或明文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EncryptionMigrationRunner implements ApplicationRunner {

    /** 需要迁移的加密列：表名 → 列名与筛选条件 */
    private static final List<Target> TARGETS = List.of(
            new Target("sys_config", "config_value", "is_encrypted = 1"),
            new Target("supplier_bank_account", "payee_id_no", "1 = 1"));

    private final JdbcTemplate jdbc;
    private final AesUtils aesUtils;

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Stat> report = migrate();
        if (report.values().stream().allMatch(s -> s.migrated == 0 && s.failed == 0)) {
            log.info("加密数据迁移：没有旧格式密文需要处理");
            return;
        }
        report.forEach((table, s) -> log.info("加密数据迁移：{} 迁移 {} 条，失败 {} 条", table, s.migrated, s.failed));
    }

    /** 迁移全部旧格式密文，返回每张表的迁移数与失败数 */
    public Map<String, Stat> migrate() {
        Map<String, Stat> report = new LinkedHashMap<>(4);
        for (Target t : TARGETS) {
            report.put(t.table, migrate(t));
        }
        return report;
    }

    private Stat migrate(Target t) {
        Stat stat = new Stat();
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT `id`, `" + t.column + "` AS v FROM `" + t.table + "` WHERE " + t.condition
                        + " AND `" + t.column + "` <> '' AND `" + t.column + "` NOT LIKE 'v2:%'");
        for (Map<String, Object> row : rows) {
            Object id = row.get("id");
            String old = (String) row.get("v");
            if (!AesUtils.isLegacy(old)) {
                continue;
            }
            String migrated;
            try {
                migrated = aesUtils.encrypt(aesUtils.decrypt(old));
            } catch (CryptoException | IllegalArgumentException | BizException e) {
                // 异常信息可能带有密文片段，只记表名、id 和异常类型
                log.warn("加密数据迁移失败：{} id={}（{}），保持原值", t.table, id, e.getClass().getSimpleName());
                stat.failed++;
                continue;
            }
            // 带上旧值作为条件，迁移期间被用户改过的行不会被覆盖
            int updated = jdbc.update("UPDATE `" + t.table + "` SET `" + t.column + "` = ?, `update_time` = NOW()"
                    + " WHERE `id` = ? AND `" + t.column + "` = ?", migrated, id, old);
            stat.migrated += updated;
        }
        return stat;
    }

    private record Target(String table, String column, String condition) {
    }

    /** 单张表的迁移结果 */
    public static class Stat {
        public int migrated;
        public int failed;

        @Override
        public String toString() {
            return "migrated=" + migrated + ", failed=" + failed;
        }
    }
}
