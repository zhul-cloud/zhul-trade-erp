package com.zhul.erp.common.utils;

import com.zhul.erp.common.exception.BizException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 敏感数据加密存储（系统参数加密项、收款人身份证号等）。
 * <p>
 * 格式 {@code v2:} + Base64(12 字节初始向量 ‖ 密文 ‖ 16 字节认证标签)，AES-256-GCM，
 * 密钥由 AES_KEY 经 HKDF-SHA256 派生；密文被改动、AES_KEY 不同或没有 v2: 前缀时解密报错。
 */
@Component
public class AesUtils {

    public static final String V2_PREFIX = "v2:";

    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_LENGTH = 32;
    private static final byte[] HKDF_SALT = "zhul-erp".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HKDF_INFO = "data-encryption/v2".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${zhul.crypto.aes-key}")
    private String secret;

    private SecretKeySpec gcmKey;

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }
        byte[] iv = new byte[IV_LENGTH];
        RANDOM.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, gcmKey(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] out = ByteBuffer.allocate(IV_LENGTH + sealed.length).put(iv).put(sealed).array();
            return V2_PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new BizException("数据加密失败", e);
        }
    }

    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }
        if (!cipherText.startsWith(V2_PREFIX)) {
            throw new BizException("数据解密失败：密文格式不正确");
        }
        byte[] data;
        try {
            data = Base64.getDecoder().decode(cipherText.substring(V2_PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new BizException("数据解密失败：密文格式不正确", e);
        }
        if (data.length < IV_LENGTH + TAG_BITS / 8) {
            throw new BizException("数据解密失败：密文格式不正确");
        }
        try {
            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, gcmKey(), new GCMParameterSpec(TAG_BITS, data, 0, IV_LENGTH));
            byte[] plain = cipher.doFinal(data, IV_LENGTH, data.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new BizException("数据解密失败：密文被改动或密钥不一致", e);
        }
    }

    private SecretKeySpec gcmKey() {
        if (gcmKey == null) {
            gcmKey = new SecretKeySpec(hkdfSha256(secret.getBytes(StandardCharsets.UTF_8), HKDF_SALT, HKDF_INFO, KEY_LENGTH), "AES");
        }
        return gcmKey;
    }

    /** HKDF-SHA256（RFC 5869）：先提取再扩展到指定长度 */
    static byte[] hkdfSha256(byte[] ikm, byte[] salt, byte[] info, int length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            byte[] prk = mac.doFinal(ikm);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            ByteArrayOutputStream okm = new ByteArrayOutputStream(length + 32);
            byte[] block = new byte[0];
            for (int i = 1; okm.size() < length; i++) {
                mac.update(block);
                mac.update(info);
                mac.update((byte) i);
                block = mac.doFinal();
                okm.write(block, 0, block.length);
            }
            return Arrays.copyOf(okm.toByteArray(), length);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 不可用", e);
        }
    }
}
