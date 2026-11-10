package com.zhul.erp.modules.upload.service;

import com.aliyun.oss.model.SetBucketCORSRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 直传的后台清理与启动检查（spec system/file-attachment「未完成上传的清理」，design D5、D6） */
class UploadHousekeepingTest {

    @TempDir
    Path root;

    @Test
    void cleanup_deletesOnlyTempFilesOlderThanOneDay_andEmptyDirs() throws Exception {
        Path tmp = Files.createDirectories(root.resolve("tmp/0"));
        Path old = Files.writeString(tmp.resolve("old.pdf"), "x");
        Path fresh = Files.writeString(Files.createDirectories(root.resolve("tmp/7")).resolve("fresh.pdf"), "x");
        Path stale = Files.createDirectories(root.resolve("tmp/9"));
        Files.writeString(stale.resolve("gone.pdf"), "x");
        Files.setLastModifiedTime(old, FileTime.from(Instant.now().minus(Duration.ofHours(25))));
        Files.setLastModifiedTime(stale.resolve("gone.pdf"), FileTime.from(Instant.now().minus(Duration.ofDays(3))));
        Path formal = Files.writeString(Files.createDirectories(root.resolve("tenant/0/inquiry/202611")).resolve("keep.pdf"), "x");
        Files.setLastModifiedTime(formal, FileTime.from(Instant.now().minus(Duration.ofDays(30))));

        int removed = LocalUploadCleanupJob.cleanup(root.resolve("tmp"), Instant.now().minus(Duration.ofDays(1)));

        assertThat(removed).isEqualTo(2);
        assertThat(old).doesNotExist();
        assertThat(stale).doesNotExist();
        assertThat(fresh).exists();
        assertThat(formal).as("正式目录不受影响").exists();
        assertThat(root.resolve("tmp")).exists();
    }

    @Test
    void cleanup_withoutTempDir_doesNothing() {
        assertThat(LocalUploadCleanupJob.cleanup(root.resolve("tmp"), Instant.now())).isZero();
    }

    @Test
    void corsReady_requiresPutAndExposedEtag() {
        SetBucketCORSRequest.CORSRule getOnly = new SetBucketCORSRequest.CORSRule();
        getOnly.setAllowedMethods(List.of("GET", "HEAD"));
        getOnly.setExposeHeaders(List.of("ETag"));
        SetBucketCORSRequest.CORSRule putNoEtag = new SetBucketCORSRequest.CORSRule();
        putNoEtag.setAllowedMethods(List.of("PUT"));
        putNoEtag.setExposeHeaders(List.of("Content-Length"));
        SetBucketCORSRequest.CORSRule ready = new SetBucketCORSRequest.CORSRule();
        ready.setAllowedMethods(List.of("GET", "put"));
        ready.setExposeHeaders(List.of("etag"));

        assertThat(UploadStartupCheck.corsReady(null)).isFalse();
        assertThat(UploadStartupCheck.corsReady(List.of(getOnly, putNoEtag))).isFalse();
        assertThat(UploadStartupCheck.corsReady(List.of(getOnly, ready))).isTrue();
    }
}
