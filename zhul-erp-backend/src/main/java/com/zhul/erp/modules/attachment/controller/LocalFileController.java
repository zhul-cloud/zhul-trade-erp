package com.zhul.erp.modules.attachment.controller;

import com.zhul.erp.framework.storage.FileStorages;
import com.zhul.erp.framework.storage.LocalFileStorage;
import com.zhul.erp.framework.storage.UploadRules;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 本地存储方式下私有文件的签名链接（规格「附件访问控制」）：不需要登录，安全性来自 HMAC 签名与过期时间，
 * 与 OSS 签名链接一致；支持 Range 请求，视频可以拖动播放。签名无效或过期返回 403，文件不存在返回 404。
 */
@RestController
@RequestMapping("/api/v1/public/files")
@RequiredArgsConstructor
public class LocalFileController {

    private final FileStorages storages;

    @GetMapping
    public ResponseEntity<Resource> file(@RequestParam("k") String key, @RequestParam("n") String fileName,
                                         @RequestParam("d") String download, @RequestParam("e") long exp,
                                         @RequestParam("s") String sig) {
        LocalFileStorage local = storages.local();
        if (!local.verify(key, fileName, download, exp, sig)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Path path = local.privatePath(key);
        if (!Files.isRegularFile(path)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(UploadRules.contentTypeOf(key)))
                .header(HttpHeaders.CONTENT_DISPOSITION, ("1".equals(download) ? "attachment" : "inline")
                        + "; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20"))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=600")
                .body(new FileSystemResource(path));
    }
}
