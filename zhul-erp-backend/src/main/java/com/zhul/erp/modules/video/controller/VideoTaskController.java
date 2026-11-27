package com.zhul.erp.modules.video.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.framework.storage.FileStorageService.FileLink;
import com.zhul.erp.modules.video.constants.VideoConstants;
import com.zhul.erp.modules.video.dto.BatchQueueVO;
import com.zhul.erp.modules.video.dto.FolderMatchRequest;
import com.zhul.erp.modules.video.dto.FolderMatchVO;
import com.zhul.erp.modules.video.dto.IdsRequest;
import com.zhul.erp.modules.video.dto.LinksRequest;
import com.zhul.erp.modules.video.dto.MaterialsRequest;
import com.zhul.erp.modules.video.dto.RejectRequest;
import com.zhul.erp.modules.video.dto.VideoScript;
import com.zhul.erp.modules.video.dto.VideoTaskDetailVO;
import com.zhul.erp.modules.video.dto.VideoTaskQuery;
import com.zhul.erp.modules.video.dto.VideoTaskVO;
import com.zhul.erp.modules.video.dto.WindowVO;
import com.zhul.erp.modules.video.service.VideoFolderMatcher;
import com.zhul.erp.modules.video.service.VideoScriptService;
import com.zhul.erp.modules.video.service.VideoTaskService;
import com.zhul.erp.modules.video.support.VideoSettings;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 视频任务（spec「视频任务权限」）：查看需要「视频任务」菜单；上传、调整素材、改脚本、加入或移出队列需要「视频任务上传」；
 * 通过、退回需要「视频任务审核」；修改生成时段只有平台账号可以（服务里校验）。
 */
@RestController
@RequestMapping("/api/v1/video-tasks")
@RequiredArgsConstructor
public class VideoTaskController {

    private static final String VIEW = "@perm.canAccessMenu('" + VideoConstants.MENU + "')";
    private static final String UPLOAD = VIEW + " and @perm.has('" + VideoConstants.PERM_UPLOAD + "')";
    private static final String REVIEW = VIEW + " and @perm.has('" + VideoConstants.PERM_REVIEW + "')";

    private final VideoTaskService service;
    private final VideoScriptService scripts;
    private final VideoFolderMatcher folders;
    private final VideoSettings settings;

    @PostMapping("/page")
    @PreAuthorize(VIEW)
    public Result<PageResult<VideoTaskVO>> page(@RequestBody VideoTaskQuery query) {
        return Result.ok(service.page(query));
    }

    @PostMapping("/counts")
    @PreAuthorize(VIEW)
    public Result<Map<String, Long>> counts(@RequestBody VideoTaskQuery query) {
        return Result.ok(service.counts(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public Result<VideoTaskDetailVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    /** 商品的视频任务；没有时 data 为空 */
    @GetMapping("/products/{productId}")
    @PreAuthorize(VIEW)
    public Result<VideoTaskDetailVO> byProduct(@PathVariable Long productId) {
        return Result.ok(service.byProduct(productId));
    }

    /** 下一个待审核的任务 ID；没有时 data 为空 */
    @GetMapping("/next-review")
    @PreAuthorize(VIEW)
    public Result<Long> nextReview(@RequestParam(required = false) Long after) {
        return Result.ok(service.nextReview(after));
    }

    @PostMapping("/{id}/links")
    @PreAuthorize(VIEW)
    public Result<List<FileLink>> links(@PathVariable Long id, @Valid @RequestBody LinksRequest req) {
        return Result.ok(service.links(id, req.getAttachmentIds(), req.isDownload()));
    }

    /** 为一个商品上传一张图片：没有任务时创建 */
    @PostMapping("/products/{productId}/images")
    @PreAuthorize(UPLOAD)
    public Result<VideoTaskDetailVO.Material> upload(@PathVariable Long productId, @RequestPart("file") MultipartFile file) {
        return Result.ok(service.upload(productId, file));
    }

    @PutMapping("/{id}/materials")
    @PreAuthorize(UPLOAD)
    public Result<VideoTaskDetailVO> materials(@PathVariable Long id, @Valid @RequestBody MaterialsRequest req) {
        return Result.ok(service.saveMaterials(id, req));
    }

    @DeleteMapping("/{id}/materials/{attachmentId}")
    @PreAuthorize(UPLOAD)
    public Result<VideoTaskDetailVO> removeMaterial(@PathVariable Long id, @PathVariable Long attachmentId) {
        return Result.ok(service.removeMaterial(id, attachmentId));
    }

    @PutMapping("/{id}/script")
    @PreAuthorize(UPLOAD)
    public Result<VideoTaskDetailVO> script(@PathVariable Long id, @RequestBody VideoScript script) {
        return Result.ok(service.saveScript(id, script));
    }

    /** AI 生成脚本：onlyIfMissing=true 时只在还没有脚本时生成（上传完图片后自动调用） */
    @PostMapping("/{id}/script/generate")
    @PreAuthorize(UPLOAD)
    public Result<VideoTaskDetailVO> generateScript(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean onlyIfMissing) {
        return Result.ok(scripts.generate(id, onlyIfMissing));
    }

    @PostMapping("/{id}/queue")
    @PreAuthorize(UPLOAD)
    public Result<VideoTaskDetailVO> queue(@PathVariable Long id) {
        return Result.ok(service.queue(id));
    }

    @PostMapping("/{id}/dequeue")
    @PreAuthorize(UPLOAD)
    public Result<VideoTaskDetailVO> dequeue(@PathVariable Long id) {
        return Result.ok(service.dequeue(id));
    }

    @PostMapping("/batch-queue")
    @PreAuthorize(UPLOAD)
    public Result<BatchQueueVO> batchQueue(@Valid @RequestBody IdsRequest req) {
        return Result.ok(service.batchQueue(req.getIds()));
    }

    /** 批量上传预览：子文件夹名匹配商品 */
    @PostMapping("/folders/match")
    @PreAuthorize(UPLOAD)
    public Result<List<FolderMatchVO>> matchFolders(@Valid @RequestBody FolderMatchRequest req) {
        return Result.ok(folders.match(req.getFolders()));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(REVIEW)
    public Result<VideoTaskDetailVO> approve(@PathVariable Long id) {
        return Result.ok(service.approve(id));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize(REVIEW)
    public Result<VideoTaskDetailVO> reject(@PathVariable Long id, @RequestBody RejectRequest req) {
        return Result.ok(service.reject(id, req.getReason()));
    }

    @GetMapping("/settings/window")
    @PreAuthorize(VIEW)
    public Result<WindowVO> window() {
        return Result.ok(new WindowVO(settings.window().text()));
    }

    @PutMapping("/settings/window")
    @PreAuthorize(VIEW)
    public Result<WindowVO> saveWindow(@RequestBody WindowVO req) {
        return Result.ok(new WindowVO(settings.saveWindow(req.getWindow()).text()));
    }
}
