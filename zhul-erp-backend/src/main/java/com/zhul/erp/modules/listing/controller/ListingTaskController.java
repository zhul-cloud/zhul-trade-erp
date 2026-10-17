package com.zhul.erp.modules.listing.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.framework.storage.FileStorageService.FileLink;
import com.zhul.erp.modules.listing.constants.ListingConstants;
import com.zhul.erp.modules.listing.dto.ListingTaskDetailVO;
import com.zhul.erp.modules.listing.dto.ListingTaskQuery;
import com.zhul.erp.modules.listing.dto.ListingTaskVO;
import com.zhul.erp.modules.listing.dto.PublishRequest;
import com.zhul.erp.modules.listing.dto.ReferenceRequest;
import com.zhul.erp.modules.listing.service.ListingTaskService;
import com.zhul.erp.modules.media.dto.MediaReferenceVO;
import com.zhul.erp.modules.media.dto.MediaUrlRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 发品任务：查看需要「发品任务」菜单；选参考素材、标记已发布、修改记录、已同步、已替换需要「处理发品任务」 */
@RestController
@RequestMapping("/api/v1/listing-tasks")
@RequiredArgsConstructor
public class ListingTaskController {

    private static final String VIEW = "@perm.canAccessMenu('" + ListingConstants.MENU + "')";
    private static final String EDIT = VIEW + " and @perm.has('" + ListingConstants.PERM_EDIT + "')";

    private final ListingTaskService service;

    @PostMapping("/page")
    @PreAuthorize(VIEW)
    public Result<PageResult<ListingTaskVO>> page(@RequestBody ListingTaskQuery query) {
        return Result.ok(service.page(query));
    }

    /** 各页签数量（按同样的筛选条件） */
    @PostMapping("/counts")
    @PreAuthorize(VIEW)
    public Result<Map<String, Long>> counts(@RequestBody ListingTaskQuery query) {
        return Result.ok(service.counts(query));
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    public Result<ListingTaskDetailVO> detail(@PathVariable Long id) {
        return Result.ok(service.detail(id));
    }

    /** 任务素材的预览或下载链接 */
    @PostMapping("/{id}/urls")
    @PreAuthorize(VIEW)
    public Result<List<FileLink>> urls(@PathVariable Long id, @Valid @RequestBody MediaUrlRequest req) {
        return Result.ok(service.links(id, req.getRefs(), req.isDownload()));
    }

    /** 选参考素材用：这个任务型号的参考型号 */
    @GetMapping("/{id}/references")
    @PreAuthorize(VIEW)
    public Result<MediaReferenceVO> references(@PathVariable Long id) {
        return Result.ok(service.references(id));
    }

    @PutMapping("/{id}/reference")
    @PreAuthorize(EDIT)
    public Result<ListingTaskDetailVO> reference(@PathVariable Long id, @RequestBody(required = false) ReferenceRequest req) {
        return Result.ok(service.setReference(id, req));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize(EDIT)
    public Result<ListingTaskDetailVO> publish(@PathVariable Long id, @Valid @RequestBody PublishRequest req) {
        return Result.ok(service.publish(id, req));
    }

    @PutMapping("/{id}/record")
    @PreAuthorize(EDIT)
    public Result<ListingTaskDetailVO> record(@PathVariable Long id, @Valid @RequestBody PublishRequest req) {
        return Result.ok(service.updateRecord(id, req));
    }

    @PostMapping("/{id}/sync")
    @PreAuthorize(EDIT)
    public Result<ListingTaskDetailVO> sync(@PathVariable Long id) {
        return Result.ok(service.markSynced(id));
    }

    @PostMapping("/{id}/replace")
    @PreAuthorize(EDIT)
    public Result<ListingTaskDetailVO> replace(@PathVariable Long id) {
        return Result.ok(service.markReplaced(id));
    }
}
