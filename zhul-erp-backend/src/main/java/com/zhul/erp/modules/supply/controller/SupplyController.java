package com.zhul.erp.modules.supply.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.supply.dto.BoardVOs;
import com.zhul.erp.modules.supply.dto.DemandDetailVO;
import com.zhul.erp.modules.supply.dto.DemandVO;
import com.zhul.erp.modules.supply.dto.MatrixVO;
import com.zhul.erp.modules.supply.dto.ProjectVO;
import com.zhul.erp.modules.supply.dto.SignalVO;
import com.zhul.erp.modules.supply.dto.SupplyRequests;
import com.zhul.erp.modules.supply.dto.TeamVO;
import com.zhul.erp.modules.supply.service.CoverageMatrixService;
import com.zhul.erp.modules.supply.service.CoverageService;
import com.zhul.erp.modules.supply.service.DemandDetailService;
import com.zhul.erp.modules.supply.service.DemandService;
import com.zhul.erp.modules.supply.service.GapProjectService;
import com.zhul.erp.modules.supply.service.SupplyBoardService;
import com.zhul.erp.modules.supply.service.SupplyTeamService;
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

import java.util.Map;

/**
 * 供应链管理第一期（OpenSpec supply-chain-phase1-demand-coverage）。
 * 查看要求能访问「供应链跟进」或「覆盖矩阵」菜单；写操作按四个按钮权限控制；供应商与价格另受「查看货源信息」控制（服务层处理）。
 */
@RestController
@RequestMapping("/api/v1/supply")
@RequiredArgsConstructor
public class SupplyController {

    private static final String FOLLOWUP = "@perm.canAccessMenu('/supply-chain/followup')";
    private static final String MATRIX = "@perm.canAccessMenu('/supply-chain/coverage')";
    private static final String ANY = "(" + FOLLOWUP + " or " + MATRIX + ")";
    private static final String EDIT = FOLLOWUP + " and @perm.has('supply:followup:edit')";
    private static final String TEAM = FOLLOWUP + " and @perm.has('supply:followup:team')";
    private static final String PROJECT = ANY + " and @perm.has('supply:project:edit')";
    private static final String COVERAGE = ANY + " and @perm.has('supply:coverage:edit')";

    private final SupplyBoardService boardService;
    private final DemandService demandService;
    private final DemandDetailService detailService;
    private final CoverageService coverageService;
    private final GapProjectService projectService;
    private final SupplyTeamService teamService;
    private final CoverageMatrixService matrixService;

    // ---------------------------------------------------------------- 供应链跟进

    /** 顶部卡片与阶段计数；ownerId 为空为当前用户，0 为全部 */
    @GetMapping("/overview")
    @PreAuthorize(FOLLOWUP)
    public Result<BoardVOs.Overview> overview(@RequestParam(required = false) Long ownerId) {
        return Result.ok(boardService.overview(ownerId));
    }

    /** 分组列表：board 为 intel（需求情报）或 cover（供应覆盖） */
    @GetMapping("/groups")
    @PreAuthorize(FOLLOWUP)
    public Result<BoardVOs.GroupPage> groups(@RequestParam String board, @RequestParam(required = false) String stage,
                                             @RequestParam(required = false) Boolean stockout,
                                             @RequestParam(required = false) Integer stockoutDays,
                                             @RequestParam(required = false) Boolean pending,
                                             @RequestParam(required = false) Boolean single,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Long brandId,
                                             @RequestParam(required = false) Long categoryId,
                                             @RequestParam(required = false) Integer priority,
                                             @RequestParam(required = false) Long ownerId,
                                             @RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "20") int size) {
        return Result.ok(boardService.groups(new SupplyBoardService.Filter(board, stage, stockout, stockoutDays, pending, single,
                keyword, brandId, categoryId, priority, ownerId), page, Math.min(size, 100)));
    }

    @GetMapping("/today")
    @PreAuthorize(FOLLOWUP)
    public Result<BoardVOs.Today> today() {
        return Result.ok(boardService.today());
    }

    /** 作战卡：作战地图节点（C1–G5）→ 内容 */
    @GetMapping("/cards")
    @PreAuthorize(ANY)
    public Result<Map<String, BoardVOs.Card>> cards() {
        return Result.ok(boardService.cards());
    }

    @PostMapping("/demands/defer")
    @PreAuthorize(EDIT)
    public Result<Integer> defer(@Valid @RequestBody SupplyRequests.Defer req) {
        return Result.ok(boardService.defer(req));
    }

    /** 记一次处理 */
    @PostMapping("/records")
    @PreAuthorize(EDIT)
    public Result<BoardVOs.RecordResult> record(@Valid @RequestBody SupplyRequests.Record req) {
        return Result.ok(boardService.record(req));
    }

    @GetMapping("/team")
    @PreAuthorize(TEAM)
    public Result<TeamVO> team(@RequestParam(required = false) Integer period) {
        return Result.ok(teamService.team(period));
    }

    // ---------------------------------------------------------------- 需求信号与需求对象

    @GetMapping("/signals/unclassified")
    @PreAuthorize(FOLLOWUP)
    public Result<PageResult<SignalVO>> unclassified(@RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     @RequestParam(required = false) Integer type) {
        var p = demandService.unclassified(page, size, type);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @PostMapping("/signals")
    @PreAuthorize(EDIT)
    public Result<Long> addSignal(@Valid @RequestBody SupplyRequests.ManualSignal req) {
        return Result.ok(demandService.addSignal(req));
    }

    @PostMapping("/signals/classify")
    @PreAuthorize(EDIT)
    public Result<Integer> classify(@Valid @RequestBody SupplyRequests.Classify req) {
        return Result.ok(demandService.classify(req));
    }

    @PostMapping("/signals/ignore")
    @PreAuthorize(EDIT)
    public Result<Integer> ignore(@Valid @RequestBody SupplyRequests.Ignore req) {
        return Result.ok(demandService.ignore(req));
    }

    @PostMapping("/demands/merge")
    @PreAuthorize(EDIT)
    public Result<Void> merge(@Valid @RequestBody SupplyRequests.Merge req) {
        demandService.merge(req);
        return Result.ok();
    }

    @GetMapping("/demands/{id}")
    @PreAuthorize(ANY)
    public Result<DemandDetailVO> detail(@PathVariable Long id) {
        return Result.ok(detailService.detail(id));
    }

    @PutMapping("/demands/{id}/priority")
    @PreAuthorize(EDIT)
    public Result<Void> priority(@PathVariable Long id, @Valid @RequestBody SupplyRequests.ChangePriority req) {
        demandService.changePriority(id, req);
        return Result.ok();
    }

    @PostMapping("/demands/{id}/join")
    @PreAuthorize(EDIT)
    public Result<Void> join(@PathVariable Long id) {
        demandService.joinMatrix(id);
        return Result.ok();
    }

    @PostMapping("/demands/{id}/pause")
    @PreAuthorize(EDIT)
    public Result<Void> pause(@PathVariable Long id, @Valid @RequestBody SupplyRequests.Reason req) {
        demandService.pause(id, req);
        return Result.ok();
    }

    @PostMapping("/demands/{id}/resume")
    @PreAuthorize(EDIT)
    public Result<Void> resume(@PathVariable Long id) {
        demandService.resume(id);
        return Result.ok();
    }

    @PutMapping("/demands/{id}/next")
    @PreAuthorize(EDIT)
    public Result<Void> next(@PathVariable Long id, @Valid @RequestBody SupplyRequests.NextStep req) {
        boardService.setNext(id, req);
        return Result.ok();
    }

    @PutMapping("/demands/{id}/coverage")
    @PreAuthorize(COVERAGE)
    public Result<Void> coverage(@PathVariable Long id, @Valid @RequestBody SupplyRequests.Coverage req) {
        coverageService.setCoverage(id, req);
        return Result.ok();
    }

    @PutMapping("/demands/{id}/channels")
    @PreAuthorize(COVERAGE + " and @perm.has('inquiry:supplier:view')")
    public Result<Void> channels(@PathVariable Long id, @Valid @RequestBody SupplyRequests.Channels req) {
        coverageService.setChannels(id, req);
        return Result.ok();
    }

    // ---------------------------------------------------------------- 专项

    @GetMapping("/projects/candidates")
    @PreAuthorize(PROJECT)
    public Result<Map<String, Object>> candidates(@RequestParam Long brandId, @RequestParam(required = false) Long categoryId) {
        return Result.ok(projectService.candidates(brandId, categoryId));
    }

    @PostMapping("/projects")
    @PreAuthorize(PROJECT)
    public Result<Long> createProject(@Valid @RequestBody SupplyRequests.Project req) {
        return Result.ok(projectService.create(req));
    }

    @GetMapping("/projects/{id}")
    @PreAuthorize(ANY)
    public Result<ProjectVO> project(@PathVariable Long id) {
        ProjectVO v = projectService.detail(id);
        v.setRecords(detailService.records(projectService.itemIds(id)));
        return Result.ok(v);
    }

    @PutMapping("/projects/{id}/items")
    @PreAuthorize(PROJECT)
    public Result<Void> projectItems(@PathVariable Long id, @Valid @RequestBody SupplyRequests.ProjectItems req) {
        projectService.changeItems(id, req);
        return Result.ok();
    }

    /** 结束专项：负责人或有「维护专项」权限（服务层校验） */
    @PostMapping("/projects/{id}/close")
    @PreAuthorize(ANY)
    public Result<Void> closeProject(@PathVariable Long id, @Valid @RequestBody SupplyRequests.CloseProject req) {
        projectService.close(id, req);
        return Result.ok();
    }

    // ---------------------------------------------------------------- 覆盖矩阵

    @GetMapping("/matrix")
    @PreAuthorize(MATRIX)
    public Result<MatrixVO> matrix(@RequestParam(required = false) String mode, @RequestParam(required = false) Integer period,
                                   @RequestParam(required = false) Long parentCategoryId,
                                   @RequestParam(required = false) Long brandId,
                                   @RequestParam(required = false) Integer priority,
                                   @RequestParam(required = false) Long ownerId,
                                   @RequestParam(required = false) Boolean gapsOnly) {
        return Result.ok(matrixService.matrix(mode, period, parentCategoryId, brandId, priority, ownerId, gapsOnly));
    }

    @GetMapping("/matrix/models")
    @PreAuthorize(MATRIX)
    public Result<PageResult<DemandVO>> matrixModels(@RequestParam(required = false) Long brandId,
                                                     @RequestParam(required = false) Long categoryId,
                                                     @RequestParam(required = false) Integer coverage,
                                                     @RequestParam(required = false) Integer priority,
                                                     @RequestParam(required = false) String keyword,
                                                     @RequestParam(defaultValue = "false") boolean all,
                                                     @RequestParam(defaultValue = "1") int page,
                                                     @RequestParam(defaultValue = "20") int size) {
        var p = matrixService.models(brandId, categoryId, coverage, priority, keyword, all, page, size);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }
}
