package com.zhul.erp.common.utils;

import cn.hutool.core.util.HexUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.symmetric.AES;
import com.zhul.erp.common.exception.BizException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AesUtilsTest {

    private static final String KEY = "unit-test-key";

    private AesUtils aes;

    @BeforeEach
    void setUp() {
        aes = withKey(KEY);
    }

    private static AesUtils withKey(String key) {
        AesUtils utils = new AesUtils();
        ReflectionTestUtils.setField(utils, "secret", key);
        return utils;
    }

    @Test
    void hkdfMatchesRfc5869TestCase1() {
        byte[] ikm = HexUtil.decodeHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        byte[] salt = HexUtil.decodeHex("000102030405060708090a0b0c");
        byte[] info = HexUtil.decodeHex("f0f1f2f3f4f5f6f7f8f9");
        byte[] expected = HexUtil.decodeHex(
                "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865");
        assertArrayEquals(expected, AesUtils.hkdfSha256(ikm, salt, info, 42));
    }

    @Test
    void roundTripIdNumberAndChinese() {
        for (String plain : new String[] {"110101199003076515", "收款人 张三 sk-ant-api03-xyz"}) {
            String cipher = aes.encrypt(plain);
            assertTrue(cipher.startsWith(AesUtils.V2_PREFIX));
            assertFalse(cipher.contains(plain));
            assertEquals(plain, aes.decrypt(cipher));
        }
    }

    @Test
    void samePlainTextEncryptsDifferently() {
        String a = aes.encrypt("110101199003076515");
        String b = aes.encrypt("110101199003076515");
        assertNotEquals(a, b);
        assertEquals(aes.decrypt(a), aes.decrypt(b));
    }

    @Test
    void idNumberCipherFitsColumn() {
        assertTrue(aes.encrypt("11010119900307651X").length() <= 128);
    }

    @Test
    void emptyAndNullPassThrough() {
        assertEquals("", aes.encrypt(""));
        assertNull(aes.encrypt(null));
        assertEquals("", aes.decrypt(""));
        assertNull(aes.decrypt(null));
    }

    @Test
    void tamperedCipherIsRejectedWithoutLeakingContent() {
        String plain = "110101199003076515";
        String cipher = aes.encrypt(plain);
        byte[] data = Base64.getDecoder().decode(cipher.substring(3));
        for (int i = 0; i < data.length; i++) {
            byte[] changed = data.clone();
            changed[i] ^= 0x01;
            String tampered = AesUtils.V2_PREFIX + Base64.getEncoder().encodeToString(changed);
            BizException e = assertThrows(BizException.class, () -> aes.decrypt(tampered));
            assertFalse(e.getMessage().contains(plain));
            assertFalse(e.getMessage().contains(tampered));
        }
    }

    @Test
    void truncatedOrMalformedCipherIsRejected() {
        String cipher = aes.encrypt("110101199003076515");
        assertThrows(BizException.class, () -> aes.decrypt(cipher.substring(0, cipher.length() - 4)));
        assertThrows(BizException.class, () -> aes.decrypt("v2:AAAA"));
        assertThrows(BizException.class, () -> aes.decrypt("v2:不是base64"));
    }

    @Test
    void differentAesKeyCannotDecrypt() {
        String cipher = aes.encrypt("110101199003076515");
        assertThrows(BizException.class, () -> withKey("another-key").decrypt(cipher));
    }

    @Test
    void cipherWithoutV2PrefixIsRejected() {
        String plain = "110101199003076515";
        // 旧格式（AES/ECB，密钥为 AES_KEY 的 MD5）已不再支持
        String legacy = new AES(DigestUtil.md5(KEY.getBytes(StandardCharsets.UTF_8))).encryptHex(plain);
        for (String cipher : new String[] {legacy, "0a1b2c", plain}) {
            BizException e = assertThrows(BizException.class, () -> aes.decrypt(cipher));
            assertFalse(e.getMessage().contains(plain));
            assertFalse(e.getMessage().contains(cipher));
        }
    }
}
