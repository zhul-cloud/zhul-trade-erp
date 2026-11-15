package com.zhul.erp.modules.aitask.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 发给编排服务、要模型原样带回的条目 key（audit AI-011）：末尾加「~」+ 6 位摘要校验码。
 * 模型抄错 key（如把商品 ID 写错一位）时校验码对不上，回调丢弃这一条，不会写到本次没有提交的数据上。
 * 只防抄错，不防伪造：回调本身由共享令牌鉴权。
 */
public final class AiTaskKeys {

    private static final char SEP = '~';
    private static final int CHECK_LEN = 6;

    private AiTaskKeys() {
    }

    public static String sign(String raw) {
        return raw + SEP + check(raw);
    }

    /** 校验通过返回去掉校验码的原 key；缺校验码或对不上返回 null */
    public static String verify(String key) {
        if (key == null) {
            return null;
        }
        int i = key.lastIndexOf(SEP);
        if (i <= 0 || key.length() - i - 1 != CHECK_LEN) {
            return null;
        }
        String raw = key.substring(0, i);
        byte[] expected = check(raw).getBytes(StandardCharsets.UTF_8);
        byte[] actual = key.substring(i + 1).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual) ? raw : null;
    }

    private static String check(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d, 0, CHECK_LEN / 2);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("缺少 SHA-256", e);
        }
    }
}
