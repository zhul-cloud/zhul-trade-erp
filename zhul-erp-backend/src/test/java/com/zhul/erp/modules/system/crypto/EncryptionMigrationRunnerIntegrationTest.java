package com.zhul.erp.modules.system.crypto;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.symmetric.AES;
import com.zhul.erp.common.utils.AesUtils;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(OutputCaptureExtension.class)
class EncryptionMigrationRunnerIntegrationTest extends IntegrationTestBase {

    private static final String KEY_PREFIX = "it.crypto-migration.";
    private static final String MARK = "IT-CRYPTO-MIGRATION";
    private static final String ID_NO = "110101199003076515";
    private static final String CONFIG_SECRET = "sk-it-crypto-migration-secret";

    @Autowired private EncryptionMigrationRunner runner;
    @Autowired private AesUtils aesUtils;
    @Value("${zhul.crypto.aes-key}") private String aesKey;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM sys_config WHERE config_key LIKE ?", KEY_PREFIX + "%");
        jdbc.update("DELETE FROM supplier_bank_account WHERE account_name = ?", MARK);
    }

    private String legacy(String plain) {
        return new AES(DigestUtil.md5(aesKey.getBytes(StandardCharsets.UTF_8))).encryptHex(plain);
    }

    private int insertConfig(int tenantId, String key, String value) {
        jdbc.update("INSERT INTO sys_config (tenant_id, config_key, config_name, config_value, config_type, is_builtin,"
                + " is_encrypted, config_group) VALUES (?, ?, '迁移测试', ?, 'STRING', 0, 1, 'custom')", tenantId, key, value);
        return jdbc.queryForObject("SELECT id FROM sys_config WHERE tenant_id = ? AND config_key = ?", Integer.class, tenantId, key);
    }

    private long insertAccount(String payeeIdNo) {
        jdbc.update("INSERT INTO supplier_bank_account (account_name, payee_id_no) VALUES (?, ?)", MARK, payeeIdNo);
        return jdbc.queryForObject("SELECT MAX(id) FROM supplier_bank_account WHERE account_name = ?", Long.class, MARK);
    }

    private String config(int id) {
        return jdbc.queryForObject("SELECT config_value FROM sys_config WHERE id = ?", String.class, id);
    }

    private String payee(long id) {
        return jdbc.queryForObject("SELECT payee_id_no FROM supplier_bank_account WHERE id = ?", String.class, id);
    }

    @Test
    void migratesLegacyCipherAcrossTenantsAndIsIdempotent(CapturedOutput output) {
        int platform = insertConfig(0, KEY_PREFIX + "platform", legacy(CONFIG_SECRET));
        int tenant = insertConfig(1001, KEY_PREFIX + "tenant", legacy(CONFIG_SECRET));
        jdbc.update("UPDATE sys_config SET deleted_at = NOW() WHERE id = ?", tenant);
        long account = insertAccount(legacy(ID_NO));
        String corrupted = "0123456789abcdef";
        long broken = insertAccount(corrupted);
        String alreadyV2 = aesUtils.encrypt(ID_NO);
        long current = insertAccount(alreadyV2);

        Map<String, EncryptionMigrationRunner.Stat> first = runner.migrate();

        for (String value : new String[] {config(platform), config(tenant), payee(account)}) {
            assertTrue(value.startsWith(AesUtils.V2_PREFIX));
        }
        assertEquals(CONFIG_SECRET, aesUtils.decrypt(config(platform)));
        assertEquals(CONFIG_SECRET, aesUtils.decrypt(config(tenant)));
        assertEquals(ID_NO, aesUtils.decrypt(payee(account)));
        assertEquals(corrupted, payee(broken));
        assertEquals(alreadyV2, payee(current));
        assertTrue(first.get("sys_config").migrated >= 2);
        assertTrue(first.get("supplier_bank_account").migrated >= 1);
        assertTrue(first.get("supplier_bank_account").failed >= 1);

        String platformAfter = config(platform);
        String accountAfter = payee(account);
        Map<String, EncryptionMigrationRunner.Stat> second = runner.migrate();
        assertEquals(0, second.get("sys_config").migrated);
        assertEquals(0, second.get("supplier_bank_account").migrated);
        assertEquals(platformAfter, config(platform));
        assertEquals(accountAfter, payee(account));

        assertTrue(output.getAll().contains("supplier_bank_account id=" + broken));
        assertFalse(output.getAll().contains(ID_NO));
        assertFalse(output.getAll().contains(CONFIG_SECRET));
        assertFalse(output.getAll().contains(corrupted));
    }
}
