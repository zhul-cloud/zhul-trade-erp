package com.zhul.erp.framework.storage;

import com.zhul.erp.common.exception.BizException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 服务器目录存储：私有文件在 {@code zhul.upload.private-dir}，公开文件在 {@code zhul.upload.dir}（经 /uploads/** 静态映射）。
 * 私有文件的签名链接指向 {@code /api/v1/public/files}，由后端校验 HMAC 签名与过期时间后提供文件，行为与 OSS 签名链接一致。
 */
public class LocalFileStorage implements FileStorage {

    static final String LINK_PATH = "/api/v1/public/files";
    private static final String HMAC = "HmacSHA256";

    private final Path privateRoot;
    private final Path publicRoot;
    private final byte[] signKey;

    public LocalFileStorage(String privateDir, String publicDir, String secret) {
        this.privateRoot = Paths.get(privateDir).toAbsolutePath().normalize();
        this.publicRoot = Paths.get(publicDir).toAbsolutePath().normalize();
        this.signKey = ("file-link:" + secret).getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String code() {
        return LOCAL;
    }

    @Override
    public void put(boolean publicRead, String key, Path source, String contentType) {
        Path target = resolve(publicRead, key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("保存文件失败", e);
        }
    }

    @Override
    public InputStream open(boolean publicRead, String key) {
        Path path = resolve(publicRead, key);
        if (!Files.isRegularFile(path)) {
            throw new BizException("附件不存在，请重新上传");
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException e) {
            throw new UncheckedIOException("读取文件失败", e);
        }
    }

    @Override
    public long size(boolean publicRead, String key) {
        Path path = resolve(publicRead, key);
        try {
            return Files.isRegularFile(path) ? Files.size(path) : -1;
        } catch (IOException e) {
            throw new UncheckedIOException("读取文件失败", e);
        }
    }

    @Override
    public void delete(boolean publicRead, String key) {
        try {
            Files.deleteIfExists(resolve(publicRead, key));
        } catch (IOException e) {
            throw new UncheckedIOException("删除文件失败", e);
        }
    }

    @Override
    public void copy(boolean publicRead, String fromKey, String toKey) {
        Path from = resolve(publicRead, fromKey);
        Path to = resolve(publicRead, toKey);
        try {
            Files.createDirectories(to.getParent());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("复制文件失败", e);
        }
    }

    @Override
    public String signedUrl(String key, String fileName, Instant expiresAt, boolean download) {
        long exp = expiresAt.getEpochSecond();
        String d = download ? "1" : "0";
        return LINK_PATH + "?k=" + enc(key) + "&n=" + enc(fileName) + "&d=" + d + "&e=" + exp
                + "&s=" + sign(key, fileName, d, exp);
    }

    @Override
    public String publicUrl(String key) {
        return "/uploads/" + key;
    }

    /** 校验本地签名链接：签名一致且未过期 */
    public boolean verify(String key, String fileName, String download, long exp, String sig) {
        if (key == null || fileName == null || download == null || sig == null || Instant.now().getEpochSecond() > exp) {
            return false;
        }
        return MessageDigest.isEqual(sign(key, fileName, download, exp).getBytes(StandardCharsets.UTF_8),
                sig.getBytes(StandardCharsets.UTF_8));
    }

    /** 私有文件的本地路径（签名校验通过后供接口按 Range 输出） */
    public Path privatePath(String key) {
        return resolve(false, key);
    }

    private Path resolve(boolean publicRead, String key) {
        Path root = publicRead ? publicRoot : privateRoot;
        Path path = root.resolve(key).normalize();
        if (!path.startsWith(root) || path.equals(root)) {
            throw new BizException("附件不存在，请重新上传");
        }
        return path;
    }

    private String sign(String key, String fileName, String download, long exp) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(signKey, HMAC));
            byte[] raw = mac.doFinal((key + "\n" + fileName + "\n" + download + "\n" + exp).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("签名失败", e);
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
