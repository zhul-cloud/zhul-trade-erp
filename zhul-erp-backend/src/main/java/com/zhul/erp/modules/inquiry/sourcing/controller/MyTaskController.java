package com.zhul.erp.modules.inquiry.sourcing.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.inquiry.sourcing.dto.ActualModelRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.ImportConfirmRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.ImportFileVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.MyTaskDetailVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.MyTaskItemVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.MyTaskVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.PastePreviewRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.PastePreviewVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.ReturnTaskRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.SaveQuotesRequest;
import com.zhul.erp.modules.inquiry.sourcing.service.MyTaskService;
import com.zhul.erp.modules.inquiry.sourcing.service.SourcingExcelService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.io.IOException;
import java.util.List;

/** 我的询价任务（采购与兼职采购）：只操作分配给自己的任务 */
@RestController
@RequestMapping("/api/v1/inquiry/my-tasks")
@RequiredArgsConstructor
@PreAuthorize("@perm.canAccessMenu('/inquiry/my-tasks')")
public class MyTaskController {

    private final MyTaskService myTaskService;
    private final SourcingExcelService excelService;

    @GetMapping
    public Result<List<MyTaskVO>> list(@RequestParam(defaultValue = "false") boolean done) {
        return Result.ok(myTaskService.myTasks(done));
    }

    @GetMapping("/{id}")
    public Result<MyTaskDetailVO> detail(@PathVariable Long id) {
        return Result.ok(myTaskService.detail(id));
    }

    @PutMapping("/{id}/quotes")
    public Result<Void> saveQuotes(@PathVariable Long id, @Valid @RequestBody SaveQuotesRequest req) {
        myTaskService.saveQuotes(id, req);
        return Result.ok();
    }

    @PostMapping("/{id}/paste-preview")
    public Result<PastePreviewVO> pastePreview(@PathVariable Long id, @Valid @RequestBody PastePreviewRequest req) {
        return Result.ok(myTaskService.pastePreview(id, req.getText()));
    }

    /** 采购回填真实型号（离开输入框即保存，不受报价锁定限制） */
    @PutMapping("/{id}/items/{itemId}/actual-model")
    public Result<MyTaskItemVO> saveActualModel(@PathVariable Long id, @PathVariable Long itemId,
                                               @Valid @RequestBody ActualModelRequest req) {
        return Result.ok(myTaskService.saveActualModel(id, itemId, req.getActualModel()));
    }

    @PostMapping("/{id}/return")
    public Result<Void> returnTask(@PathVariable Long id, @Valid @RequestBody ReturnTaskRequest req) {
        myTaskService.returnTask(id, req);
        return Result.ok();
    }

    @GetMapping("/package")
    public void download(@RequestParam List<Long> taskIds, HttpServletResponse response) throws IOException {
        ExcelResponses.write(excelService.download(taskIds, false), response);
    }

    @PostMapping("/import/preview")
    public Result<List<ImportFileVO>> importPreview(@RequestPart("files") List<MultipartFile> files) {
        return Result.ok(excelService.preview(files, false));
    }

    @PostMapping("/import/confirm")
    public Result<Void> importConfirm(@Valid @RequestBody ImportConfirmRequest req) {
        excelService.confirm(req, false);
        return Result.ok();
    }
}
