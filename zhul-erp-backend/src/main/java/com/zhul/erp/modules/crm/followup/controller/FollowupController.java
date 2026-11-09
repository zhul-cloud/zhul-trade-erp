package com.zhul.erp.modules.crm.followup.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.AccountCardVO;
import com.zhul.erp.modules.crm.followup.dto.AnnualPlanRequest;
import com.zhul.erp.modules.crm.followup.dto.BatchFollowupRequest;
import com.zhul.erp.modules.crm.followup.dto.BatchResultVO;
import com.zhul.erp.modules.crm.followup.dto.BatchSnoozeRequest;
import com.zhul.erp.modules.crm.followup.dto.ConvertRequest;
import com.zhul.erp.modules.crm.followup.dto.CustomerGroupListVO;
import com.zhul.erp.modules.crm.followup.dto.CustomerFollowupVO;
import com.zhul.erp.modules.crm.followup.dto.DevelopCardVO;
import com.zhul.erp.modules.crm.followup.dto.ExtractRequest;
import com.zhul.erp.modules.crm.followup.dto.ExtractResultVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupListVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupOverviewVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupQuery;
import com.zhul.erp.modules.crm.followup.dto.FollowupTeamVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupTodayVO;
import com.zhul.erp.modules.crm.followup.dto.JoinDevelopRequest;
import com.zhul.erp.modules.crm.followup.dto.NurtureRequest;
import com.zhul.erp.modules.crm.followup.dto.OrderPartsVO;
import com.zhul.erp.modules.crm.followup.dto.ProspectRequest;
import com.zhul.erp.modules.crm.followup.dto.RecordFollowupRequest;
import com.zhul.erp.modules.crm.followup.dto.RecordResultVO;
import com.zhul.erp.modules.crm.followup.dto.ReplyDraftRequest;
import com.zhul.erp.modules.crm.followup.dto.ReplyDraftVO;
import com.zhul.erp.modules.crm.followup.dto.SnoozeRequest;
import com.zhul.erp.modules.crm.followup.dto.SopCardVO;
import com.zhul.erp.modules.crm.followup.dto.StopDevelopRequest;
import com.zhul.erp.modules.crm.followup.dto.StrategicRequest;
import com.zhul.erp.modules.crm.followup.service.FollowupAccountService;
import com.zhul.erp.modules.crm.followup.service.FollowupAiService;
import com.zhul.erp.modules.crm.followup.service.FollowupBatchService;
import com.zhul.erp.modules.crm.followup.service.FollowupDevelopService;
import com.zhul.erp.modules.crm.followup.service.FollowupService;
import com.zhul.erp.modules.crm.followup.service.FollowupTeamService;
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
    private final FollowupBatchService batchService;
    private final FollowupAiService followupAiService;
    private final FollowupTeamService followupTeamService;
    private final FollowupAccountService followupAccountService;
    private final FollowupDevelopService followupDevelopService;

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
    /** 按客户分组的跟进列表（商机成交、订单履约、售后评价） */
    @GetMapping("/groups")
    @PreAuthorize(VIEW)
    public Result<CustomerGroupListVO> groups(FollowupQuery query) {
        return Result.ok(followupService.groups(query));
    }

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

    /** 批量跟进：每个客户各写一条跟进记录，逐个客户处理，返回成功数和跳过原因 */
    @PostMapping("/batch-record")
    @PreAuthorize(RECORD)
    public Result<BatchResultVO> batchRecord(@Valid @RequestBody BatchFollowupRequest req) {
        return Result.ok(batchService.record(req));
    }

    /** 批量延后（今日待办客户卡片的「延后」也走这里，只传一个客户） */
    @PostMapping("/batch-snooze")
    @PreAuthorize(RECORD)
    public Result<BatchResultVO> batchSnooze(@Valid @RequestBody BatchSnoozeRequest req) {
        return Result.ok(batchService.snooze(req));
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

    /** 客户经营作战卡顶部：G1–G3、触达理由、交叉销售、战略客户与年度计划（第三期） */
    @GetMapping("/accounts/{customerId}")
    @PreAuthorize(VIEW)
    public Result<AccountCardVO> account(@PathVariable Long customerId) {
        return Result.ok(followupAccountService.card(customerId));
    }

    @PutMapping("/accounts/{customerId}/strategic")
    @PreAuthorize(VIEW + " and " + RECORD)
    public Result<Void> strategic(@PathVariable Long customerId, @Valid @RequestBody StrategicRequest req) {
        followupAccountService.setStrategic(customerId, req.getStrategic());
        return Result.ok();
    }

    @PutMapping("/accounts/{customerId}/plan")
    @PreAuthorize(VIEW + " and " + RECORD)
    public Result<AccountCardVO.Plan> plan(@PathVariable Long customerId, @Valid @RequestBody AnnualPlanRequest req) {
        return Result.ok(followupAccountService.savePlan(customerId, req));
    }

    /** 新建开发对象：建客户（来源主动开发）并加入主动开发（第四期） */
    @PostMapping("/develop")
    @PreAuthorize(VIEW + " and " + RECORD)
    public Result<DevelopCardVO> createProspect(@Valid @RequestBody ProspectRequest req) {
        return Result.ok(followupDevelopService.create(req));
    }

    @GetMapping("/develop/{customerId}")
    @PreAuthorize(VIEW)
    public Result<DevelopCardVO> develop(@PathVariable Long customerId) {
        return Result.ok(followupDevelopService.card(customerId));
    }

    @PostMapping("/develop/{customerId}/join")
    @PreAuthorize(VIEW + " and " + RECORD)
    public Result<DevelopCardVO> joinDevelop(@PathVariable Long customerId, @Valid @RequestBody JoinDevelopRequest req) {
        return Result.ok(followupDevelopService.join(customerId, req));
    }

    @PostMapping("/develop/{customerId}/reopen")
    @PreAuthorize(VIEW + " and " + RECORD)
    public Result<DevelopCardVO> reopenDevelop(@PathVariable Long customerId) {
        return Result.ok(followupDevelopService.reopen(customerId));
    }

    @PostMapping("/items/{id}/convert")
    @PreAuthorize(VIEW + " and " + RECORD + " and @perm.has('crm:opportunity:add')")
    public Result<DevelopCardVO> convert(@PathVariable Long id, @Valid @RequestBody ConvertRequest req) {
        return Result.ok(followupDevelopService.convert(id, req));
    }

    @PostMapping("/items/{id}/stop")
    @PreAuthorize(VIEW + " and " + RECORD)
    public Result<Void> stopDevelop(@PathVariable Long id, @Valid @RequestBody StopDevelopRequest req) {
        followupDevelopService.stop(id, req);
        return Result.ok();
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
