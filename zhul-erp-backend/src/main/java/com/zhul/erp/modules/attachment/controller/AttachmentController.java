package com.zhul.erp.modules.attachment.controller;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.attachment.dto.AttachmentFile;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 业务附件上传与读取：按所属单据类型校验菜单权限；
 * 返回 Resource 时由 Spring 处理 Range 请求（206），视频可以拖动播放。
 */
@RestController
@RequestMapping("/api/v1/attachments")
@RequiredArgsConstructor
public class AttachmentController {

    /** 所属单据类型 → 能看这类附件的菜单（任一即可） */
    private static final Map<String, List<String>> MENUS = Map.of(
            AttachmentService.SHIPMENT, List.of("/purchase/shipments", "/warehouse/receipts"),
            AttachmentService.RECEIPT, List.of("/warehouse/receipts", "/purchase/shipments"),
            AttachmentService.SHOOT, List.of("/warehouse/shoots"));

    private final AttachmentService service;
    private final PermissionChecker perm;

    @PostMapping
    public Result<AttachmentVO> upload(@RequestParam String ownerType, @RequestPart("file") MultipartFile file) {
        requireMenu(ownerType);
        return Result.ok(service.upload(ownerType, file));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id, @RequestParam(defaultValue = "true") boolean inline) {
        BizAttachmentDO a = service.require(id);
        requireMenu(a.getOwnerType());
        AttachmentFile f = service.file(a);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, (inline ? "inline" : "attachment")
                        + "; filename*=UTF-8''" + URLEncoder.encode(f.fileName(), StandardCharsets.UTF_8).replace("+", "%20"))
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(f.resource());
    }

    private void requireMenu(String ownerType) {
        List<String> menus = MENUS.get(ownerType);
        if (menus == null || menus.stream().noneMatch(perm::canAccessMenu)) {
            throw new org.springframework.security.access.AccessDeniedException("没有权限访问这个附件");
        }
    }
}
