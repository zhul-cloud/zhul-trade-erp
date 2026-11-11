package com.zhul.erp.modules.upload.service;

import com.aliyun.oss.model.SetBucketCORSRequest;
import com.zhul.erp.framework.storage.FileStorages;
import com.zhul.erp.framework.storage.OssFileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

/**
 * 启动时检查直传的部署条件，只写日志、不阻止启动（design D5、D6）：
 * OSS 私有存储桶要有允许 PUT、暴露 ETag 的跨域规则；本地存储时私有上传目录不能在 tmpfs（内存）上。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadStartupCheck {

    private static final String DOC = "docs/06-部署运维/12-大附件直传与解析配置.md";

    private final FileStorages storages;

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        if (storages.current() instanceof OssFileStorage oss) {
            checkCors(oss);
        } else {
            checkLocalDisk(storages.local().privatePath("tmp").getParent());
        }
    }

    void checkCors(OssFileStorage oss) {
        List<SetBucketCORSRequest.CORSRule> rules;
        try {
            rules = oss.privateCorsRules();
        } catch (RuntimeException e) {
            log.warn("读取 OSS 私有存储桶的跨域规则失败（可能缺少 oss:GetBucketCors 权限），无法确认大附件直传是否可用，见 {}：{}",
                    DOC, e.getMessage());
            return;
        }
        if (!corsReady(rules)) {
            log.warn("OSS 私有存储桶缺少允许 PUT 并暴露 ETag 的跨域规则，浏览器无法直传附件，见 {}", DOC);
        }
    }

    static boolean corsReady(List<SetBucketCORSRequest.CORSRule> rules) {
        if (rules == null) {
            return false;
        }
        return rules.stream().anyMatch(r -> r.getAllowedMethods() != null
                && r.getAllowedMethods().stream().anyMatch(m -> "PUT".equalsIgnoreCase(m))
                && r.getExposeHeaders() != null
                && r.getExposeHeaders().stream().anyMatch(h -> "etag".equals(h.toLowerCase(Locale.ROOT))));
    }

    void checkLocalDisk(Path privateRoot) {
        if (isTmpfs(privateRoot)) {
            log.warn("存储方式为本地，私有上传目录 {} 在 tmpfs（内存）上：大附件会占用内存，重启后丢失。"
                    + "收大附件前请改成磁盘目录，见 docs/06-部署运维/03-数据持久化与备份.md", privateRoot);
        }
    }

    static boolean isTmpfs(Path dir) {
        try {
            return Files.isDirectory(dir) && "tmpfs".equalsIgnoreCase(Files.getFileStore(dir).type());
        } catch (IOException e) {
            return false;
        }
    }
}
