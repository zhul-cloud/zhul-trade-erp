package com.zhul.erp.framework.storage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 文件键规则（规格「存储目录」）：
 * <ul>
 *   <li>一般环节：{@code tenant/{租户}/{环节}/{年月}/{随机名}.{扩展名}}</li>
 *   <li>实拍素材：{@code tenant/{租户}/media/{品牌}/{型号}/{随机名}.{扩展名}}；上传时还不知道型号的放
 *       {@code tenant/{租户}/media/unsorted/{年月}/}</li>
 *   <li>站点外观：{@code tenant/{租户}/system/{随机名}.{扩展名}}</li>
 *   <li>共享商品库：{@code shared/product/{品牌}/{型号}/{随机名}.{扩展名}}</li>
 * </ul>
 * 不使用用户上传的原文件名。迁 OSS 之前的旧键形如 {@code {模块}/{租户}/{年月}/{随机名}.{扩展名}}，只在本地存储中出现。
 */
public final class StorageKeys {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyyMM");
    private static final String EXTS = "(pdf|jpg|png|webp|mp4|mov|xlsx|xls|csv)";
    private static final String NAME = "[0-9a-f]{32}\\." + EXTS;

    private StorageKeys() {
    }

    public static String newKey(StorageArea area, int tenantId, String ext) {
        return newKey(area, tenantId, ext, LocalDate.now().format(MONTH));
    }

    /** 指定年月（yyyyMM）：历史文件迁移时沿用原来的年月 */
    public static String newKey(StorageArea area, int tenantId, String ext, String month) {
        if (area == StorageArea.PRODUCT) {
            throw new IllegalArgumentException(area + " 按品牌与型号归档，请用 modelKey");
        }
        String tail = randomName(ext);
        if (area == StorageArea.MEDIA) {
            return "tenant/" + tenantId + "/media/unsorted/" + month + "/" + tail;
        }
        if (area == StorageArea.SYSTEM) {
            return "tenant/" + tenantId + "/system/" + tail;
        }
        return "tenant/" + tenantId + "/" + area.dir() + "/" + month + "/" + tail;
    }

    /** 按「品牌 / 型号」归档：实拍素材（租户）或共享商品库 */
    public static String modelKey(StorageArea area, int tenantId, String brand, String model, String ext) {
        String folder = slug(brand) + "/" + slug(model) + "/" + randomName(ext);
        return switch (area) {
            case MEDIA -> "tenant/" + tenantId + "/media/" + folder;
            case PRODUCT -> "shared/product/" + folder;
            default -> throw new IllegalArgumentException(area + " 不按型号归档");
        };
    }

    /** 品牌、型号转为目录名：小写，非字母数字替换为 -，连续的 - 合并，去掉首尾的 - */
    public static String slug(String s) {
        String v = s == null ? "" : s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        v = v.replaceAll("^-+|-+$", "");
        return v.isEmpty() ? "unknown" : v;
    }

    /** 文件键属于该租户、该环节（含该环节迁移前的旧键），且格式合法 */
    public static boolean owned(StorageArea area, String key, int tenantId) {
        if (key == null) {
            return false;
        }
        String base = "tenant/" + tenantId + "/" + area.dir() + "/";
        String sub = switch (area) {
            case MEDIA -> "[a-z0-9-]+/[a-z0-9-]+/";
            case SYSTEM -> "";
            default -> "\\d{6}/";
        };
        if (Pattern.matches(Pattern.quote(base) + sub + NAME, key)) {
            return true;
        }
        return area.legacyModule() != null
                && Pattern.matches(Pattern.quote(area.legacyModule() + "/" + tenantId + "/") + "\\d{6}/" + NAME, key);
    }

    public static String extOf(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }

    private static String randomName(String ext) {
        return UUID.randomUUID().toString().replace("-", "") + "." + ext;
    }
}
