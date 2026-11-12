package com.zhul.erp.modules.masterdata.customerbackground.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.framework.storage.FileStorageService.FileLink;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundPrecheckVO;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundReportDetailVO;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundReportVO;
import com.zhul.erp.modules.masterdata.customerbackground.dto.BackgroundResearchRequest;
import com.zhul.erp.modules.masterdata.customerbackground.dto.SuggestionIndexesRequest;
import com.zhul.erp.modules.masterdata.customerbackground.service.BackgroundAdoptService;
import com.zhul.erp.modules.masterdata.customerbackground.service.BackgroundPrecheckService;
import com.zhul.erp.modules.masterdata.customerbackground.service.CustomerBackgroundService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 客户背调（spec crm/customer-background）。按数据权限只能操作自己范围内的客户；
 * 能看客户就能看报告，发起与上传需要「客户背调」，删除的规则见 service。
 */
@RestController
@RequestMapping("/api/v1/masterdata/customers/{id}/background-reports")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/customer/list')")
public class CustomerBackgroundController {

    private static final String BACKGROUND = "@perm.has('partner:customer:background')";
    /** 采纳会改客户档案，与修改画像一样需要「编辑客户」 */
    private static final String EDIT = "@perm.has('partner:customer:edit')";

    private final CustomerBackgroundService backgroundService;
    private final BackgroundPrecheckService precheckService;
    private final BackgroundAdoptService adoptService;

    @GetMapping
    public Result<List<BackgroundReportVO>> list(@PathVariable Long id) {
        return Result.ok(backgroundService.list(id));
    }

    /** 背调前检查：关键信息是否齐全与充足度，能看客户就能看 */
    @GetMapping("/precheck")
    public Result<BackgroundPrecheckVO> precheck(@PathVariable Long id) {
        return Result.ok(precheckService.check(id));
    }

    @GetMapping("/{reportId}")
    public Result<BackgroundReportDetailVO> detail(@PathVariable Long id, @PathVariable Long reportId) {
        return Result.ok(backgroundService.detail(id, reportId));
    }

    /** 导出 PDF：核心结论、报告正文和字段采纳情况 */
    @GetMapping("/{reportId}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id, @PathVariable Long reportId) {
        CustomerBackgroundService.ExportedPdf pdf = backgroundService.exportPdf(id, reportId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(pdf.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf.body());
    }

    /** 上传的 PDF 原件下载链接 */
    @GetMapping("/{reportId}/file-url")
    public Result<FileLink> fileUrl(@PathVariable Long id, @PathVariable Long reportId) {
        return Result.ok(backgroundService.file(id, reportId));
    }

    /** 发起 AI 背调；24 小时内背调过的返回 BACKGROUND_RECENT，确认后带 confirmed=true 再发 */
    @PostMapping("/research")
    @PreAuthorize(BACKGROUND)
    public Result<BackgroundReportVO> research(@PathVariable Long id, @RequestBody(required = false) BackgroundResearchRequest req) {
        return Result.ok(backgroundService.research(id, req != null && Boolean.TRUE.equals(req.getConfirmed())));
    }

    /** 上传背调报告：PDF 文件或粘贴的文字，二选一 */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(BACKGROUND)
    public Result<BackgroundReportVO> upload(@PathVariable Long id,
                                             @RequestPart(value = "file", required = false) MultipartFile file,
                                             @RequestParam(value = "text", required = false) String text) {
        return Result.ok(backgroundService.upload(id, file, text));
    }

    /** 采纳勾选的字段建议，写入客户档案，来源记为这份报告 */
    @PostMapping("/{reportId}/suggestions/adopt")
    @PreAuthorize(EDIT)
    public Result<Void> adopt(@PathVariable Long id, @PathVariable Long reportId, @Valid @RequestBody SuggestionIndexesRequest req) {
        adoptService.adopt(id, reportId, req.getIndexes());
        return Result.ok();
    }

    @PostMapping("/{reportId}/suggestions/ignore")
    @PreAuthorize(EDIT)
    public Result<Void> ignore(@PathVariable Long id, @PathVariable Long reportId, @Valid @RequestBody SuggestionIndexesRequest req) {
        adoptService.ignore(id, reportId, req.getIndexes());
        return Result.ok();
    }

    @DeleteMapping("/{reportId}")
    public Result<Void> delete(@PathVariable Long id, @PathVariable Long reportId) {
        backgroundService.delete(id, reportId);
        return Result.ok();
    }
}
