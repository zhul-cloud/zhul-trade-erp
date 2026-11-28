package com.zhul.erp.modules.website.service;

import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.UploadRules;
import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import com.zhul.erp.modules.listing.service.ListingTaskService;
import com.zhul.erp.modules.media.dto.MediaItemVO;
import com.zhul.erp.modules.media.dto.MediaRef;
import com.zhul.erp.modules.media.service.MediaLibraryService;
import com.zhul.erp.modules.media.support.MediaCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 站点数据里的图片和视频（spec website-publish「素材」，design 第 8 条）：
 * - 官方图片视频本来就在公开桶或是外部链接，直接用原地址；
 * - 本公司的私有素材复制到公开桶 `website/{siteId}/{slug}/{素材id}.{扩展名}`，已存在跳过；
 * - 站点数据里只写链接，由 publicUrl 在导出时生成。
 * 预览只算链接不复制（copy=false），发布时才复制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteMediaService {

    static final int KIND_IMAGE = 1;
    static final int KIND_VIDEO = 2;

    private final ListingTaskService listingTasks;
    private final MediaLibraryService mediaLibrary;
    private final FileStorageService files;

    public record Result(List<Map<String, Object>> photos, List<Map<String, Object>> videos, boolean reference, int copied) {
    }

    public Result of(Long siteId, String slug, ListingTaskDO task, String title, boolean copy) {
        ListingTaskService.SiteMedia media = listingTasks.siteMedia(task);
        List<Map<String, Object>> photos = new ArrayList<>();
        List<Map<String, Object>> videos = new ArrayList<>();
        int copied = 0;
        for (MediaItemVO i : media.items()) {
            boolean image = Objects.equals(i.getKind(), KIND_IMAGE);
            if (!image && !Objects.equals(i.getKind(), KIND_VIDEO)) {
                continue;
            }
            Located src = locate(siteId, slug, i, media.reference(), copy);
            if (src == null) {
                continue;
            }
            copied += src.copied() ? 1 : 0;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("src", src.url());
            if (image) {
                m.put("tag", Objects.equals(i.getCategory(), MediaCategory.MARKETING.code()) ? "Marketing" : "Photo");
                photos.add(m);
            } else {
                m.put("title", title);
                videos.add(m);
            }
        }
        return new Result(photos, videos, media.reference(), copied);
    }

    private record Located(String url, boolean copied) {
    }

    private Located locate(Long siteId, String slug, MediaItemVO i, boolean reference, boolean copy) {
        if (i.getItemId() == null || StringUtils.hasText(i.getOfficialTable())) {
            // 官方素材（含本公司改过标记的覆盖行）：原地址
            return siteUrl(i.getPublicUrl()) ? new Located(i.getPublicUrl(), false) : null;
        }
        MediaLibraryService.FileSource fs;
        if (reference) {
            MediaRef ref = new MediaRef();
            ref.setItemId(i.getItemId());
            fs = mediaLibrary.source(ref);
        } else {
            fs = mediaLibrary.listingSource(i.getItemId());
        }
        if (StringUtils.hasText(fs.externalUrl())) {
            return siteUrl(fs.externalUrl()) ? new Located(fs.externalUrl(), false) : null;
        }
        if (fs.publicRead()) {
            return new Located(files.publicUrl(fs.storage(), fs.key()), false);
        }
        String key = publicKey(siteId, slug, i.getItemId(), fs.key());
        if (!copy) {
            return new Located(files.publicUrl(files.storages().current().code(), key), false);
        }
        FileStorageService.PublicCopy c = files.copyToPublic(fs.storage(), fs.key(), key);
        return new Located(files.publicUrl(c.storage(), c.key()), c.copied());
    }

    /** 公开桶里的文件键：website/{siteId}/{slug}/{素材id}.{扩展名} */
    static String publicKey(Long siteId, String slug, Long itemId, String sourceKey) {
        String ext = UploadRules.extOf(sourceKey);
        return "website/" + siteId + "/" + slug + "/" + itemId + (StringUtils.hasText(ext) ? "." + ext : "");
    }

    /** 站点只接受以 / 或 https:// 开头的素材地址 */
    static boolean siteUrl(String url) {
        return url != null && (url.startsWith("/") || url.startsWith("https://"));
    }
}
