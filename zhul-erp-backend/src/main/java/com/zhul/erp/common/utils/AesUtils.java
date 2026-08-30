package com.zhul.erp.common.utils;

import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.crypto.symmetric.AES;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 用于系统配置等加密存储场景，密钥经 MD5 派生为 16 字节，避免配置长度不合法导致启动失败。
 */
@Component
public class AesUtils {

    @Value("${zhul.crypto.aes-key}")
    private String secret;

    private AES aes;

    private AES getAes() {
        if (aes == null) {
            byte[] key = DigestUtil.md5(secret.getBytes(StandardCharsets.UTF_8));
            aes = new AES(key);
        }
        return aes;
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        return getAes().encryptHex(plainText);
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        return getAes().decryptStr(cipherText);
    }
}
