package com.zhul.erp.modules.crm.followup.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.CustomerFollowupVO;
import com.zhul.erp.modules.crm.followup.dto.ExtractRequest;
import com.zhul.erp.modules.crm.followup.dto.ExtractResultVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupListVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupOverviewVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupQuery;
import com.zhul.erp.modules.crm.followup.dto.FollowupTeamVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupTodayVO;
import com.zhul.erp.modules.crm.followup.dto.NurtureRequest;
import com.zhul.erp.modules.crm.followup.dto.OrderPartsVO;
import com.zhul.erp.modules.crm.followup.dto.RecordFollowupRequest;
import com.zhul.erp.modules.crm.followup.dto.RecordResultVO;
import com.zhul.erp.modules.crm.followup.dto.ReplyDraftRequest;
import com.zhul.erp.modules.crm.followup.dto.ReplyDraftVO;
import com.zhul.erp.modules.crm.followup.dto.SnoozeRequest;
import com.zhul.erp.modules.crm.followup.dto.SopCardVO;
import com.zhul.erp.modules.crm.followup.service.FollowupAiService;
import com.zhul.erp.modules.crm.followup.service.FollowupService;
import com.zhul.erp.modules.crm.followup.service.FollowupTeamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户跟进（design D14）。查看类接口要求能访问「客户跟进」菜单；记跟进、延后、转培育、激活要求「记跟进」按钮权限。
 * 客户的跟进记录跟随客户可见范围，客户详情能看就能看（客户不在数据权限内时由服务层拒绝）。
 */
@RestController
@RequestMapping("/api/v1/followups")
@RequiredArgsConstructor
public class FollowupController {

    private static final String VIEW = "@perm.canAccessMenu('/sales/followup')";
    private static final String RECORD = "@perm.has('crm:followup:record')";

    private final FollowupService followupService;
    private final FollowupAiService followupAiService;
    private final FollowupTeamService followupTeamService;

    @GetMapping("/overview")
    @PreAuthorize(VIEW)
    public Result<FollowupOverviewVO> overview(@RequestParam(required = false) String ownerScope) {
        return Result.ok(followupService.overview(ownerScope));
    }

    /**
     * 团队视图（第二期 design D10）：period 为 week / last-week / month，deptId 可选，exceptions=all 返回全部异常条目。
     * 需要「查看团队」权限，没有时返回 403。
     */
    @GetMapping("/team")
    @PreAuthorize(VIEW + " and @perm.has('crm:followup:team')")
    public Result<FollowupTeamVO> team(@RequestParam(required = false) String period, @RequestParam(required = false) Integer deptId,
                                       @RequestParam(required = false) String exceptions) {
        return Result.ok(followupTeamService.team(period, deptId, exceptions));
    }

    @GetMapping("/today")
    @PreAuthorize(VIEW)
    public Result<FollowupTodayVO> today(@RequestParam(required = false) String ownerScope) {
        return Result.ok(followupService.today(ownerScope));
    }

    @GetMapping("/items")
    @PreAuthorize(VIEW)
    public Result<FollowupListVO> items(FollowupQuery query) {
        return Result.ok(followupService.items(query));
    }

    /** 订单履约行展开：分部分进度 */
    @GetMapping("/items/{id}/parts")
    @PreAuthorize(VIEW)
    public Result<OrderPartsVO> parts(@PathVariable Long id) {
        return Result.ok(followupService.parts(id));
    }

    @PostMapping("/items/{id}/snooze")
    @PreAuthorize(RECORD)
    public Result<Void> snooze(@PathVariable Long id, @Valid @RequestBody SnoozeRequest req) {
        followupService.snooze(id, req.getDate());
        return Result.ok();
    }

    @PostMapping("/items/{id}/nurture")
    @PreAuthorize(RECORD)
    public Result<Void> nurture(@PathVariable Long id, @Valid @RequestBody NurtureRequest req) {
        followupService.nurture(id, req);
        return Result.ok();
    }

    @PostMapping("/items/{id}/activate")
    @PreAuthorize(RECORD)
    public Result<Void> activate(@PathVariable Long id) {
        followupService.activate(id);
        return Result.ok();
    }

    @PostMapping("/records")
    @PreAuthorize(RECORD)
    public Result<RecordResultVO> record(@Valid @RequestBody RecordFollowupRequest req) {
        return Result.ok(followupService.record(req));
    }

    @GetMapping("/sop/{board}/{stage}")
    public Result<SopCardVO> sop(@PathVariable int board, @PathVariable String stage) {
        return Result.ok(followupService.sop(board, stage));
    }

    /** 卡点字典；board 不传为商机成交 */
    @GetMapping("/sop/blockers")
    public Result<List<SopCardVO.Blocker>> blockers(@RequestParam(required = false) Integer board) {
        return Result.ok(followupService.blockerVos(board == null ? FollowupConstants.BOARD_SALES : board));
    }

    @GetMapping("/customers/{customerId}/records")
    public Result<CustomerFollowupVO> customerRecords(@PathVariable Long customerId,
                                                      @RequestParam(required = false) String type,
                                                      @RequestParam(required = false) Boolean blockerOnly,
                                                      @RequestParam(required = false) Integer page,
                                                      @RequestParam(required = false) Integer pageSize) {
        return Result.ok(followupService.customerRecords(customerId, type, blockerOnly, page, pageSize));
    }

    @GetMapping("/customers/{customerId}/summary")
    public Result<CustomerFollowupVO> customerSummary(@PathVariable Long customerId) {
        return Result.ok(followupService.customerSummary(customerId));
    }

    /** AI 整理：提交任务，返回 taskId；租户没配置 AI 时返回错误码 AI_NOT_CONFIGURED */
    @PostMapping("/extract")
    @PreAuthorize(RECORD)
    public Result<ExtractResultVO> extract(@Valid @RequestBody ExtractRequest req) {
        return Result.ok(followupAiService.extract(req));
    }

    @GetMapping("/extract/{taskId}")
    @PreAuthorize(RECORD)
    public Result<ExtractResultVO> extractResult(@PathVariable Long taskId, @RequestParam(required = false) Long customerId) {
        return Result.ok(followupAiService.extractResult(taskId, customerId));
    }

    /** AI 写一条英文回复：同步等待，最多 30 秒；不记为一次跟进 */
    @PostMapping("/reply-draft")
    @PreAuthorize(VIEW)
    public Result<ReplyDraftVO> replyDraft(@Valid @RequestBody ReplyDraftRequest req) {
        return Result.ok(followupAiService.replyDraft(req));
    }
}
