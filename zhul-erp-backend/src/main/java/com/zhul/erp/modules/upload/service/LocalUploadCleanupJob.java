package com.zhul.erp.modules.upload.service;

import com.zhul.erp.framework.storage.FileStorages;
import com.zhul.erp.framework.tenant.TenantScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 本地存储的直传临时文件清理（spec system/file-attachment「未完成上传的清理」）：每小时删除私有上传目录 tmp/ 下
 * 超过 1 天没有改动的文件。OSS 由存储桶的生命周期规则清理。只按目录与修改时间判断，不读业务表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LocalUploadCleanupJob {

    static final Duration KEEP = Duration.ofDays(1);

    private final FileStorages storages;

    @Scheduled(fixedDelayString = "${zhul.upload.tmp-cleanup-interval-ms:3600000}", initialDelay = 600000)
    public void cleanup() {
        // 跨租户的后台任务，不查业务表；按约定显式标注平台范围
        TenantScope.platform(() -> {
            int removed = cleanup(storages.local().privatePath("tmp"), Instant.now().minus(KEEP));
            if (removed > 0) {
                log.info("清理过期的直传临时文件 {} 个", removed);
            }
        });
    }

    /** 删除 root 下修改时间早于 before 的文件及随之变空的目录，返回删除的文件数 */
    static int cleanup(Path root, Instant before) {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        int removed = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> all = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : all) {
                try {
                    if (Files.isRegularFile(p) && Files.getLastModifiedTime(p).compareTo(FileTime.from(before)) < 0) {
                        Files.delete(p);
                        removed++;
                    } else if (Files.isDirectory(p) && !p.equals(root) && isEmpty(p)) {
                        Files.delete(p);
                    }
                } catch (IOException e) {
                    log.warn("清理直传临时文件失败：{}", e.toString());
                }
            }
        } catch (IOException e) {
            log.warn("扫描直传临时目录失败：{}", e.toString());
        }
        return removed;
    }

    private static boolean isEmpty(Path dir) throws IOException {
        try (Stream<Path> s = Files.list(dir)) {
            return s.findAny().isEmpty();
        }
    }
}
