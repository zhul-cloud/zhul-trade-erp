package com.zhul.erp.modules.media.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.framework.storage.FileStorageService.FileLink;
import com.zhul.erp.modules.media.dto.MediaDetailVO;
import com.zhul.erp.modules.media.dto.MediaFlagRequest;
import com.zhul.erp.modules.media.dto.MediaItemVO;
import com.zhul.erp.modules.media.dto.MediaModelQuery;
import com.zhul.erp.modules.media.dto.MediaModelVO;
import com.zhul.erp.modules.media.dto.MediaReferenceVO;
import com.zhul.erp.modules.media.dto.MediaUrlRequest;
import com.zhul.erp.modules.media.service.MediaLibraryService;
import com.zhul.erp.modules.media.service.MediaReferenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** 素材库：查看需要「素材库」菜单；上传网络图片、修改素材标记各有按钮权限 */
@RestController
@RequestMapping("/api/v1/media")
@RequiredArgsConstructor
public class MediaLibraryController {

    private static final String VIEW = "@perm.canAccessMenu('/operations/media')";

    private final MediaLibraryService service;
    private final MediaReferenceService references;

    @PostMapping("/models/page")
    @PreAuthorize(VIEW)
    public Result<PageResult<MediaModelVO>> page(@Valid @RequestBody MediaModelQuery query) {
        return Result.ok(service.page(query));
    }

    /** 一个型号的全部素材（按分组），合并共享商品库的官方图片与资料 */
    @GetMapping("/models/detail")
    @PreAuthorize(VIEW)
    public Result<MediaDetailVO> detail(@RequestParam String brand, @RequestParam String model) {
        return Result.ok(service.detail(brand, model));
    }

    @GetMapping("/references")
    @PreAuthorize(VIEW)
    public Result<MediaReferenceVO> references(@RequestParam String brand, @RequestParam String model) {
        return Result.ok(references.references(brand, model));
    }

    /** 预览或下载链接（下载只允许可发客户的素材），一次最多 50 个 */
    @PostMapping("/urls")
    @PreAuthorize(VIEW)
    public Result<List<FileLink>> urls(@Valid @RequestBody MediaUrlRequest req) {
        return Result.ok(service.links(req.getRefs(), req.isDownload()));
    }

    @PostMapping("/flags")
    @PreAuthorize(VIEW + " and @perm.has('" + MediaLibraryService.PERM_EDIT + "')")
    public Result<MediaItemVO> flag(@Valid @RequestBody MediaFlagRequest req) {
        return Result.ok(service.flag(req));
    }

    @PostMapping("/network")
    @PreAuthorize(VIEW + " and @perm.has('" + MediaLibraryService.PERM_UPLOAD + "')")
    public Result<MediaItemVO> uploadNetwork(@RequestParam String brand, @RequestParam String model,
                                             @RequestParam(required = false) String note, @RequestPart("file") MultipartFile file) {
        return Result.ok(service.uploadNetwork(brand, model, note, file));
    }

    /** 删除网络图片：上传人本人，或有「修改素材标记」权限的人 */
    @DeleteMapping("/network/{itemId}")
    @PreAuthorize(VIEW + " and (@perm.has('" + MediaLibraryService.PERM_UPLOAD + "') or @perm.has('" + MediaLibraryService.PERM_EDIT + "'))")
    public Result<Void> deleteNetwork(@PathVariable Long itemId) {
        service.deleteNetwork(itemId);
        return Result.ok();
    }
}
