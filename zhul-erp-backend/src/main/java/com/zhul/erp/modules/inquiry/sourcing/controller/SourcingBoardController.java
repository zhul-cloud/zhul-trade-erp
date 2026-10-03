package com.zhul.erp.modules.inquiry.sourcing.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.inquiry.sourcing.dto.AssignRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.AssignRulesVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.AssigneeRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.BoardTaskDetailVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.BoardVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.ItemHistoryEntryVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.SetCostQuoteRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.ImportConfirmRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.ImportFileVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.PurchaserVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.RulePreviewVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.SaveAssignRuleRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.TaskIdsRequest;
import com.zhul.erp.modules.inquiry.sourcing.service.SourcingExcelService;
import com.zhul.erp.modules.inquiry.sourcing.service.SourcingTaskService;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.util.List;
import java.util.Map;

/** 分配工作台：查看要求能访问菜单；分配、规则、代他人导入分别要求对应按钮权限 */
@RestController
@RequestMapping("/api/v1/inquiry/sourcing-board")
@RequiredArgsConstructor
public class SourcingBoardController {

    private static final String VIEW = "@perm.canAccessMenu('/inquiry/sourcing-board')";
    private static final String ASSIGN = "@perm.has('inquiry:task:assign')";
    private static final String RULE = "@perm.has('inquiry:rule:edit')";
    private static final String PROXY = "@perm.has('inquiry:import:proxy')";

    private final SourcingTaskService taskService;
    private final SourcingExcelService excelService;

    @GetMapping
    @PreAuthorize(VIEW)
    public Result<BoardVO> board(@RequestParam(required = false) Integer status) {
        return Result.ok(taskService.board(status));
    }

    @GetMapping("/tasks/{id}")
    @PreAuthorize(VIEW)
    public Result<BoardTaskDetailVO> taskDetail(@PathVariable Long id) {
        return Result.ok(taskService.taskDetail(id));
    }

    @PutMapping("/items/{itemId}/cost-quote")
    @PreAuthorize(ASSIGN)
    public Result<Void> setCostQuote(@PathVariable Long itemId, @RequestBody SetCostQuoteRequest req) {
        taskService.setCostQuote(itemId, req.getQuoteId());
        return Result.ok();
    }

    @GetMapping("/items/{itemId}/history")
    @PreAuthorize(VIEW)
    public Result<List<ItemHistoryEntryVO>> itemHistory(@PathVariable Long itemId) {
        return Result.ok(taskService.itemHistory(itemId));
    }

    @GetMapping("/purchasers")
    @PreAuthorize(VIEW)
    public Result<List<PurchaserVO>> purchasers() {
        return Result.ok(taskService.purchasers());
    }

    @PostMapping("/assign")
    @PreAuthorize(ASSIGN)
    public Result<Void> assign(@Valid @RequestBody AssignRequest req) {
        taskService.assign(req.getTaskIds(), req.getAssigneeId());
        return Result.ok();
    }

    @PostMapping("/assign-recommended")
    @PreAuthorize(ASSIGN)
    public Result<Void> assignRecommended(@Valid @RequestBody TaskIdsRequest req) {
        taskService.assignByRecommend(req.getTaskIds());
        return Result.ok();
    }

    @PostMapping("/tasks/{id}/reassign")
    @PreAuthorize(ASSIGN)
    public Result<Void> reassign(@PathVariable Long id, @Valid @RequestBody AssigneeRequest req) {
        taskService.reassign(id, req.getAssigneeId());
        return Result.ok();
    }

    @PostMapping("/tasks/{id}/assignees")
    @PreAuthorize(ASSIGN)
    public Result<Void> addAssignee(@PathVariable Long id, @Valid @RequestBody AssigneeRequest req) {
        taskService.addAssignee(id, req.getAssigneeId());
        return Result.ok();
    }

    @PostMapping("/rule-preview")
    @PreAuthorize(ASSIGN)
    public Result<List<RulePreviewVO>> rulePreview(@Valid @RequestBody TaskIdsRequest req) {
        return Result.ok(taskService.previewRules(req.getTaskIds()));
    }

    @PostMapping("/assign-by-rule")
    @PreAuthorize(ASSIGN)
    public Result<Void> assignByRule(@Valid @RequestBody TaskIdsRequest req) {
        taskService.applyRules(req.getTaskIds());
        return Result.ok();
    }

    @GetMapping("/rules")
    @PreAuthorize(VIEW)
    public Result<AssignRulesVO> rules() {
        return Result.ok(taskService.rules());
    }

    @PostMapping("/rules")
    @PreAuthorize(RULE)
    public Result<Void> createRule(@Valid @RequestBody SaveAssignRuleRequest req) {
        taskService.createRule(req);
        return Result.ok();
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize(RULE)
    public Result<Void> updateRule(@PathVariable Long id, @Valid @RequestBody SaveAssignRuleRequest req) {
        taskService.updateRule(id, req);
        return Result.ok();
    }

    @DeleteMapping("/rules/{id}")
    @PreAuthorize(RULE)
    public Result<Void> deleteRule(@PathVariable Long id) {
        taskService.deleteRule(id);
        return Result.ok();
    }

    @PutMapping("/rules/order")
    @PreAuthorize(RULE)
    public Result<Void> reorder(@RequestBody List<Long> ruleIds) {
        taskService.reorderRules(ruleIds);
        return Result.ok();
    }

    @PutMapping("/rules/auto-assign")
    @PreAuthorize(RULE)
    public Result<Void> autoAssign(@RequestBody Map<String, Boolean> body) {
        taskService.setAutoAssign(Boolean.TRUE.equals(body.get("enabled")));
        return Result.ok();
    }

    @GetMapping("/package")
    @PreAuthorize(PROXY)
    public void download(@RequestParam List<Long> taskIds, HttpServletResponse response) throws IOException {
        ExcelResponses.write(excelService.download(taskIds, true), response);
    }

    @PostMapping("/import/preview")
    @PreAuthorize(PROXY)
    public Result<List<ImportFileVO>> importPreview(@RequestPart("files") List<MultipartFile> files) {
        return Result.ok(excelService.preview(files, true));
    }

    @PostMapping("/import/confirm")
    @PreAuthorize(PROXY)
    public Result<Void> importConfirm(@Valid @RequestBody ImportConfirmRequest req) {
        excelService.confirm(req, true);
        return Result.ok();
    }
}
