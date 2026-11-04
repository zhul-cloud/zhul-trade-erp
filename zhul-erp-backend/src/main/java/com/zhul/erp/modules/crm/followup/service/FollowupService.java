package com.zhul.erp.modules.crm.followup.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.dto.CustomerFollowupVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupItemVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupListVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupOverviewVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupQuery;
import com.zhul.erp.modules.crm.followup.dto.FollowupRecordVO;
import com.zhul.erp.modules.crm.followup.dto.FollowupTodayVO;
import com.zhul.erp.modules.crm.followup.dto.NurtureRequest;
import com.zhul.erp.modules.crm.followup.dto.RecordFollowupRequest;
import com.zhul.erp.modules.crm.followup.dto.RecordResultVO;
import com.zhul.erp.modules.crm.followup.dto.SopCardVO;
import com.zhul.erp.modules.crm.followup.entity.CustomerFollowupDO;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.entity.SopBlockerDO;
import com.zhul.erp.modules.crm.followup.entity.SopStageDO;
import com.zhul.erp.modules.crm.followup.repository.CustomerFollowupMapper;
import com.zhul.erp.modules.crm.followup.repository.FollowupItemFilter;
import com.zhul.erp.modules.crm.followup.repository.FollowupItemMapper;
import com.zhul.erp.modules.crm.followup.repository.SopBlockerMapper;
import com.zhul.erp.modules.crm.followup.repository.SopStageMapper;
import com.zhul.erp.modules.crm.followup.support.Cadence;
import com.zhul.erp.modules.crm.followup.support.FollowupSettings;
import com.zhul.erp.modules.crm.followup.support.FollowupSync;
import com.zhul.erp.modules.crm.service.OpportunityService;
import com.zhul.erp.modules.crm.support.OpportunityStageRules;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.customerprofile.service.CustomerProfileService;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.masterdata.service.CustomerService;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 客户跟进（openspec change customer-followup，spec crm/customer-followup）：链路计数、跟进列表、今日待办、
 * 延后 / 转培育 / 激活、记一次跟进、SOP 作战卡、客户的跟进时间线。
 * <p>
 * 可见范围：业务员只看自己负责的跟进对象；有「查看团队」权限的按数据权限看。跟进记录跟随客户的可见范围。
 * 事务边界：写操作各自一个事务；记一次跟进按 design D9 在一个事务内完成，任一步失败整体回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowupService {

    private static final String SCOPE_ME = "me";
    private static final String SCOPE_ALL = "all";
    private static final int TODAY_LIMIT = 200;
    private static final int RECORD_PAGE = 5;
    private static final int RECORD_PAGE_MAX = 50;

    private final FollowupItemMapper itemMapper;
    private final CustomerFollowupMapper recordMapper;
    private final SopStageMapper sopStageMapper;
    private final SopBlockerMapper sopBlockerMapper;
    private final CustomerMapper customerMapper;
    private final QuotationMapper quotationMapper;
    private final ProformaInvoiceMapper piMapper;
    private final UserBasicMapper userBasicMapper;
    private final CustomerService customerService;
    private final CustomerProfileService profileService;
    private final OpportunityService opportunityService;
    private final OpportunityStageRules stageRules;
    private final FollowupSync sync;
    private final FollowupSettings settings;
    private final DataScopeResolver dataScopeResolver;
    private final PermissionChecker perm;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;

    // ---------------------------------------------------------------- 链路卡片

    public FollowupOverviewVO overview(String ownerScope) {
        Collection<Long> owners = owners(ownerScope);
        LocalDate today = LocalDate.now();
        OpportunityStageRules.Stages stages = stageRules.load();
        List<FollowupItemDO> rows = owners != null && owners.isEmpty() ? List.of()
                : itemMapper.selectList(new LambdaQueryWrapper<FollowupItemDO>()
                .select(FollowupItemDO::getId, FollowupItemDO::getCustomerId, FollowupItemDO::getBoard,
                        FollowupItemDO::getStageGroup, FollowupItemDO::getStageCode, FollowupItemDO::getNextActionDate,
                        FollowupItemDO::getNurtureUntil)
                .isNull(FollowupItemDO::getClosedAt)
                .isNull(FollowupItemDO::getDeletedAt)
                .in(owners != null, FollowupItemDO::getOwnerId, owners));
        FollowupOverviewVO vo = new FollowupOverviewVO();
        List<FollowupItemDO> active = rows.stream().filter(r -> r.getNurtureUntil() == null).toList();
        vo.setTodayOverdue((int) active.stream().filter(r -> due(r) && r.getNextActionDate().isBefore(today)).count());
        vo.setTodayDue((int) active.stream().filter(r -> today.equals(r.getNextActionDate())).count());
        for (int b = 1; b <= FollowupConstants.BOARD_NAMES.size(); b++) {
            int board = b;
            FollowupOverviewVO.Board card = new FollowupOverviewVO.Board();
            card.setBoard(board);
            card.setStep(board);
            card.setName(FollowupConstants.BOARD_NAMES.get(board - 1));
            card.setAvailable(board == FollowupConstants.BOARD_SALES);
            List<FollowupItemDO> in = active.stream().filter(r -> r.getBoard() == board).toList();
            card.setCustomers((int) in.stream().map(FollowupItemDO::getCustomerId).distinct().count());
            card.setTotal(in.size());
            card.setOverdue((int) in.stream().filter(r -> due(r) && r.getNextActionDate().isBefore(today)).count());
            card.setNurture((int) rows.stream().filter(r -> r.getBoard() == board && r.getNurtureUntil() != null).count());
            if (board == FollowupConstants.BOARD_SALES) {
                card.setGroups(salesGroups(in, stages));
            }
            vo.getBoards().add(card);
        }
        return vo;
    }

    private static List<FollowupOverviewVO.Group> salesGroups(List<FollowupItemDO> in, OpportunityStageRules.Stages stages) {
        Map<Integer, List<String>> byGroup = new LinkedHashMap<>(4);
        byGroup.put(FollowupConstants.GROUP_BEFORE_QUOTE, List.of("S1", "S2", "S3"));
        byGroup.put(FollowupConstants.GROUP_AFTER_QUOTE, List.of(FollowupConstants.S4, FollowupConstants.S5, FollowupConstants.S6));
        byGroup.put(FollowupConstants.GROUP_AFTER_PI, List.of(FollowupConstants.S7));
        List<FollowupOverviewVO.Group> out = new ArrayList<>(3);
        for (Map.Entry<Integer, List<String>> e : byGroup.entrySet()) {
            FollowupOverviewVO.Group g = new FollowupOverviewVO.Group();
            g.setGroup(e.getKey());
            g.setName(FollowupConstants.GROUP_NAMES.get(e.getKey()));
            g.setCount((int) in.stream().filter(r -> Objects.equals(r.getStageGroup(), e.getKey())).count());
            for (String code : e.getValue()) {
                FollowupOverviewVO.Stage s = new FollowupOverviewVO.Stage();
                s.setCode(code);
                s.setName(stages.nameOf(code));
                s.setCount((int) in.stream().filter(r -> Objects.equals(r.getStageGroup(), e.getKey())
                        && code.equals(r.getStageCode())).count());
                g.getStages().add(s);
            }
            out.add(g);
        }
        return out;
    }

    // ---------------------------------------------------------------- 列表

    public FollowupListVO items(FollowupQuery q) {
        Collection<Long> owners = owners(q.getOwnerScope());
        FollowupListVO vo = new FollowupListVO();
        if (owners != null && owners.isEmpty()) {
            vo.setTotal(0L);
            return vo;
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? FollowupConstants.PAGE_SIZE
                : Math.min(q.getPageSize(), 100);
        int page = q.getPage() == null || q.getPage() <= 0 ? 1 : q.getPage();
        LocalDate today = LocalDate.now();
        FollowupItemFilter f = filter(owners, today);
        f.setBoard(q.getBoard() == null ? FollowupConstants.BOARD_SALES : q.getBoard());
        f.setNurture(Boolean.TRUE.equals(q.getNurture()));
        if (!Boolean.TRUE.equals(q.getNurture())) {
            f.setGroup(q.getGroup());
            f.setStage(StringUtils.hasText(q.getStage()) ? q.getStage() : null);
        }
        f.setBlocker(StringUtils.hasText(q.getBlocker()) ? q.getBlocker() : null);
        // LIKE 的通配符按普通字符查
        f.setKeyword(StringUtils.hasText(q.getKeyword())
                ? q.getKeyword().trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") : null);
        vo.setTotal(itemMapper.countBy(f));
        if (Objects.equals(f.getGroup(), FollowupConstants.GROUP_AFTER_PI)
                || FollowupConstants.S7.equals(f.getStage())) {
            for (Map<String, Object> row : itemMapper.unpaidByCurrency(f)) {
                FollowupListVO.CurrencyAmount a = new FollowupListVO.CurrencyAmount();
                a.setCurrencyCode(String.valueOf(row.get("currencyCode")));
                a.setAmount(row.get("amount") instanceof BigDecimal d ? d : BigDecimal.ZERO);
                vo.getUnpaid().add(a);
            }
        }
        f.setOffset((page - 1) * size);
        f.setSize(size);
        vo.setRecords(toVos(byRank(itemMapper.rankedIds(f)), today, owners == null || owners.size() > 1));
        return vo;
    }

    // ---------------------------------------------------------------- 今日待办

    public FollowupTodayVO today(String ownerScope) {
        Collection<Long> owners = owners(ownerScope);
        FollowupTodayVO vo = new FollowupTodayVO();
        vo.setWeekRemaining(0);
        vo.setTomorrow(0);
        if (owners != null && owners.isEmpty()) {
            return vo;
        }
        LocalDate today = LocalDate.now();
        boolean team = !isMe(ownerScope);
        FollowupItemFilter f = filter(owners, today);
        f.setNurture(false);
        f.setOffset(0);
        f.setSize(TODAY_LIMIT);
        f.setDueTo(today.minusDays(1));
        vo.setOverdue(toVos(byRank(itemMapper.rankedIds(f)), today, team));
        f.setDueFrom(today);
        f.setDueTo(today);
        vo.setToday(toVos(byRank(itemMapper.rankedIds(f)), today, team));
        f.setDueFrom(today.plusDays(1));
        f.setDueTo(today.plusDays(1));
        f.setSize(null);
        vo.setTomorrow((int) itemMapper.countBy(f));
        LocalDate sunday = today.with(DayOfWeek.SUNDAY);
        if (sunday.isAfter(today)) {
            f.setDueTo(sunday);
            vo.setWeekRemaining((int) itemMapper.countBy(f));
        }
        return vo;
    }

    // ---------------------------------------------------------------- 延后 / 转培育 / 激活

    /** 延后：只改提醒日期，不写跟进记录，延后次数加 1；系统提醒延后后日期固定，不再按节奏刷新 */
    @Transactional(rollbackFor = Exception.class)
    public void snooze(Long id, LocalDate date) {
        FollowupItemDO item = requireOpen(id);
        if (item.getNurtureUntil() != null) {
            throw new BizException("培育中的跟进不能延后，请先激活");
        }
        if (date == null || !date.isAfter(LocalDate.now())) {
            throw new BizException("请选择今天以后的日期");
        }
        if (Objects.equals(item.getNextActionSource(), FollowupConstants.NEXT_SYSTEM)) {
            item.setNextActionSource(FollowupConstants.NEXT_SYSTEM_ONCE);
        }
        item.setNextActionDate(date);
        item.setSnoozeCount(nz(item.getSnoozeCount()) + 1);
        itemMapper.updateById(item);
    }

    /** 转培育：必须选原因（SOP 卡点）和激活日期；不改变报价单状态和商机阶段 */
    @Transactional(rollbackFor = Exception.class)
    public void nurture(Long id, NurtureRequest req) {
        FollowupItemDO item = requireOpen(id);
        if (item.getNurtureUntil() != null) {
            throw new BizException("这条跟进已经在培育中");
        }
        if (!StringUtils.hasText(req.getReason()) || !blockers().containsKey(req.getReason())) {
            throw new BizException("请选择培育原因");
        }
        if (req.getUntil() == null || !req.getUntil().isAfter(LocalDate.now())) {
            throw new BizException("激活日期要在今天以后");
        }
        item.setNurtureUntil(req.getUntil());
        item.setNurtureReason(req.getReason());
        item.setNurtureNote(req.getNote() == null ? "" : req.getNote().trim());
        item.setNextActionDate(null);
        itemMapper.updateById(item);
    }

    /** 手动激活：回到原阶段，生成一条今天的系统提醒 */
    @Transactional(rollbackFor = Exception.class)
    public void activate(Long id) {
        FollowupItemDO item = requireOpen(id);
        if (item.getNurtureUntil() == null) {
            throw new BizException("这条跟进不在培育中");
        }
        item.setNurtureUntil(null);
        item.setNurtureReason("");
        item.setNurtureNote("");
        item.setNextActionSource(FollowupConstants.NEXT_SYSTEM_ONCE);
        item.setNextAction("重新激活，联系客户");
        item.setNextActionDate(LocalDate.now());
        item.setSnoozeCount(0);
        itemMapper.updateById(item);
    }

    // ---------------------------------------------------------------- 记一次跟进

    /**
     * 记一次跟进（design D9）：写跟进记录 → 更新跟进对象 → 推进商机或跟进对象阶段 → 写入勾选的档案字段 → 关闭当前待办。
     * 同一事务，任一步失败整体回滚；幂等键重复时返回第一次的结果。
     */
    @Transactional(rollbackFor = Exception.class)
    public RecordResultVO record(RecordFollowupRequest req) {
        CustomerFollowupDO dup = byIdempotencyKey(req.getIdempotencyKey());
        if (dup != null) {
            return duplicateOf(dup);
        }
        CustomerDO customer = customerService.requireInScope(req.getCustomerId());
        LocalDate today = LocalDate.now();
        String nextAction = req.getNextAction() == null ? "" : req.getNextAction().trim();
        if (nextAction.isEmpty()) {
            throw new BizException("请写下一步要做什么");
        }
        if (req.getNextActionDate() == null || req.getNextActionDate().isBefore(today)) {
            throw new BizException("下一步日期不能早于今天");
        }
        String blocker = req.getBlockerCode() == null ? "" : req.getBlockerCode().trim();
        if (!blocker.isEmpty() && !blockers().containsKey(blocker)) {
            throw new BizException("卡点不存在，请重新选择");
        }
        int channel = req.getChannel() == null ? 0 : req.getChannel();
        if (channel != 0 && !FollowupConstants.CHANNEL_NAMES.containsKey(channel)) {
            throw new BizException("跟进方式不正确");
        }
        int situation = req.getSituation() == null ? 0 : req.getSituation()
                & (FollowupConstants.SITUATION_FEEDBACK | FollowupConstants.SITUATION_NEGOTIATING
                | FollowupConstants.SITUATION_NO_REPLY);
        FollowupItemDO item = resolveItem(customer.getId(), req.getItemId());
        Long me = currentUser.resolve();

        // 1. 跟进记录
        CustomerFollowupDO r = new CustomerFollowupDO();
        r.setTenantId(TenantContext.getTenantId());
        r.setRecordType(FollowupConstants.RECORD_USER);
        r.setCustomerId(customer.getId());
        r.setFollowupItemId(item == null ? 0L : item.getId());
        r.setTargetType(item == null ? 0 : item.getTargetType());
        r.setTargetId(item == null ? 0L : item.getTargetId());
        r.setOpportunityId(item == null ? 0L : nzl(item.getOpportunityId()));
        r.setTitle("");
        r.setSituation(situation);
        r.setBlockerCode(blocker);
        r.setNextAction(nextAction);
        r.setNextActionDate(req.getNextActionDate());
        r.setChannel(channel);
        r.setRawContent(tail(req.getRawContent()));
        r.setAiSummary(req.getSummary() == null ? "" : req.getSummary().trim());
        r.setAiTaskId(req.getAiTaskId() == null ? 0L : req.getAiTaskId());
        String before = item == null ? "" : item.getStageCode();
        r.setStageBefore(before);
        r.setStageAfter(before);
        boolean pi = item != null && item.getTargetType() == FollowupConstants.TARGET_PI;
        r.setExpectedPayDate(pi ? req.getExpectedPayDate() : null);
        r.setOperatorId(me == null ? 0L : me);
        r.setIdempotencyKey(req.getIdempotencyKey().trim());
        try {
            recordMapper.insert(r);
        } catch (DuplicateKeyException e) {
            // 并发重复提交：另一次提交已经写入，本次不再处理
            log.info("记跟进重复提交，idempotencyKey={}", req.getIdempotencyKey());
            RecordResultVO vo = new RecordResultVO();
            vo.setDuplicate(true);
            vo.setOpportunityAdvanced(false);
            return vo;
        }

        boolean advanced = false;
        String after = before;
        if (item != null) {
            // 2. 跟进对象：卡点、下一步、上次跟进；当前待办随之关闭（下一步日期换成业务员定的）
            item.setBlockerCode(blocker);
            item.setNextAction(nextAction);
            item.setNextActionDate(req.getNextActionDate());
            item.setNextActionSource(FollowupConstants.NEXT_USER);
            item.setSnoozeCount(0);
            item.setLastFollowupId(r.getId());
            item.setLastFollowupAt(LocalDateTime.now());
            boolean replied = (situation & (FollowupConstants.SITUATION_FEEDBACK | FollowupConstants.SITUATION_NEGOTIATING)) != 0;
            if (replied) {
                item.setNoReplyStreak(0);
                item.setFeedbackSeen(1);
            } else if ((situation & FollowupConstants.SITUATION_NO_REPLY) != 0) {
                item.setNoReplyStreak(nz(item.getNoReplyStreak()) + 1);
            }
            if (pi && req.getExpectedPayDate() != null) {
                item.setExpectedPayDate(req.getExpectedPayDate());
            }
            // 3. 阶段：报价后勾「给了反馈」→ S5，「在谈条件」→ S6，只进不退
            String target = (situation & FollowupConstants.SITUATION_NEGOTIATING) != 0 ? FollowupConstants.S6
                    : (situation & FollowupConstants.SITUATION_FEEDBACK) != 0 ? FollowupConstants.S5 : null;
            boolean hasOpportunity = nzl(item.getOpportunityId()) > 0;
            if (target != null && Objects.equals(item.getStageGroup(), FollowupConstants.GROUP_AFTER_QUOTE)) {
                if (!hasOpportunity) {
                    OpportunityStageRules.Stages stages = stageRules.load();
                    if (stages.orderOf(target) > stages.orderOf(item.getStageCode())) {
                        item.setStageCode(target);
                        after = target;
                    }
                }
            }
            itemMapper.updateById(item);
            if (target != null && hasOpportunity && Objects.equals(item.getStageGroup(), FollowupConstants.GROUP_AFTER_QUOTE)) {
                advanced = opportunityService.autoAdvance(customer.getId(), target, FollowupConstants.S4,
                        "记跟进，自动推进") != null;
                if (advanced) {
                    // 新客户：跟进对象随商机同步
                    sync.resync(customer.getId(), today, false);
                    FollowupItemDO fresh = itemMapper.selectById(item.getId());
                    after = fresh == null ? target : fresh.getStageCode();
                }
            }
        }

        // 4. 档案：只补空值，来源记为跟进记录
        Map<String, String> applied = profileService.applyFollowup(customer.getId(), req.getProfile(), r.getId());
        r.setStageAfter(after);
        r.setProfileFields(applied.isEmpty() ? null : json(applied));
        recordMapper.updateById(r);

        RecordResultVO vo = new RecordResultVO();
        vo.setRecordId(r.getId());
        vo.setStageBefore(before);
        vo.setStageAfter(after);
        vo.setOpportunityAdvanced(advanced);
        vo.setDuplicate(false);
        log.info("记一次跟进，customerId={}, itemId={}, recordId={}, stage={}→{}", customer.getId(),
                item == null ? null : item.getId(), r.getId(), before, after);
        return vo;
    }

    private CustomerFollowupDO byIdempotencyKey(String key) {
        if (!StringUtils.hasText(key)) {
            throw new BizException("缺少幂等键，请刷新后重试");
        }
        return recordMapper.selectOne(new LambdaQueryWrapper<CustomerFollowupDO>()
                .eq(CustomerFollowupDO::getIdempotencyKey, key.trim())
                .last("LIMIT 1"));
    }

    private static RecordResultVO duplicateOf(CustomerFollowupDO r) {
        RecordResultVO vo = new RecordResultVO();
        vo.setRecordId(r.getId());
        vo.setStageBefore(r.getStageBefore());
        vo.setStageAfter(r.getStageAfter());
        vo.setOpportunityAdvanced(false);
        vo.setDuplicate(true);
        return vo;
    }

    /** 指定了跟进对象就用它（必须属于这个客户、未结束）；没指定取这位客户最紧急的一个；都没有返回 null */
    FollowupItemDO resolveItem(Long customerId, Long itemId) {
        if (itemId != null && itemId > 0) {
            FollowupItemDO item = itemMapper.selectById(itemId);
            if (item == null || item.getDeletedAt() != null || item.getClosedAt() != null
                    || !Objects.equals(item.getCustomerId(), customerId)) {
                throw new BizException("跟进对象已结束，请刷新后重试");
            }
            return item;
        }
        FollowupItemFilter f = filter(null, LocalDate.now());
        f.setCustomerId(customerId);
        f.setBoard(FollowupConstants.BOARD_SALES);
        f.setOffset(0);
        f.setSize(1);
        List<Long> ids = itemMapper.rankedIds(f);
        return ids.isEmpty() ? null : itemMapper.selectById(ids.get(0));
    }

    private static String tail(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.strip();
        return s.length() > FollowupConstants.RAW_MAX ? s.substring(s.length() - FollowupConstants.RAW_MAX) : s;
    }

    private String json(Map<String, String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new BizException("档案字段保存失败", e);
        }
    }

    // ---------------------------------------------------------------- SOP

    public SopCardVO sop(int board, String stage) {
        SopStageDO s = sopStageMapper.selectList(new LambdaQueryWrapper<SopStageDO>()
                        .eq(SopStageDO::getBoard, board)
                        .eq(SopStageDO::getStageCode, stage)
                        .eq(SopStageDO::getStatus, 1)).stream()
                .max(Comparator.comparing(SopStageDO::getTenantId))
                .orElseThrow(() -> new BizException("这个阶段还没有作战卡"));
        SopCardVO vo = new SopCardVO();
        vo.setBoard(board);
        vo.setStageCode(s.getStageCode());
        vo.setName(s.getName());
        vo.setGoal(s.getGoal());
        vo.setChecklist(list(s.getChecklist()));
        vo.setGateText(s.getGateText());
        vo.setAutoHint(s.getAutoHint());
        vo.setExampleDialog(s.getExampleDialog());
        vo.setBlockers(blockerVos());
        return vo;
    }

    public List<SopCardVO.Blocker> blockerVos() {
        return blockers().values().stream().map(b -> {
            SopCardVO.Blocker x = new SopCardVO.Blocker();
            x.setCode(b.getCode());
            x.setName(b.getName());
            x.setLostReason(b.getLostReason());
            x.setPlaybook(list(b.getPlaybook()));
            return x;
        }).toList();
    }

    /** 卡点字典：编码 → 卡点（租户自己的覆盖平台的），按排序 */
    private Map<String, SopBlockerDO> blockers() {
        List<SopBlockerDO> rows = sopBlockerMapper.selectList(new LambdaQueryWrapper<SopBlockerDO>()
                .eq(SopBlockerDO::getStatus, 1));
        Map<String, SopBlockerDO> byCode = new HashMap<>(16);
        rows.stream().sorted(Comparator.comparing(SopBlockerDO::getTenantId)).forEach(b -> byCode.put(b.getCode(), b));
        return byCode.values().stream().sorted(Comparator.comparing(SopBlockerDO::getSortOrder))
                .collect(Collectors.toMap(SopBlockerDO::getCode, Function.identity(), (a, b) -> a, LinkedHashMap::new));
    }

    private List<String> list(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() { });
        } catch (JsonProcessingException e) {
            log.warn("SOP 内容格式不正确，已忽略：{}", e.getOriginalMessage());
            return new ArrayList<>();
        }
    }

    // ---------------------------------------------------------------- 客户详情「跟进」分区

    /**
     * 客户的跟进时间线（按时间倒序）；type：all（默认）、user、system；blockerOnly 只看有卡点的。
     * 可见范围跟随客户。
     */
    public CustomerFollowupVO customerRecords(Long customerId, String type, Boolean blockerOnly, Integer page,
                                              Integer pageSize) {
        customerService.requireInScope(customerId);
        int size = pageSize == null || pageSize <= 0 ? RECORD_PAGE : Math.min(pageSize, RECORD_PAGE_MAX);
        int p = page == null || page <= 0 ? 1 : page;
        LambdaQueryWrapper<CustomerFollowupDO> w = new LambdaQueryWrapper<CustomerFollowupDO>()
                .eq(CustomerFollowupDO::getCustomerId, customerId)
                .isNull(CustomerFollowupDO::getDeletedAt)
                .eq("user".equals(type), CustomerFollowupDO::getRecordType, FollowupConstants.RECORD_USER)
                .eq("system".equals(type), CustomerFollowupDO::getRecordType, FollowupConstants.RECORD_SYSTEM)
                .ne(Boolean.TRUE.equals(blockerOnly), CustomerFollowupDO::getBlockerCode, "")
                .orderByDesc(CustomerFollowupDO::getId);
        IPage<CustomerFollowupDO> rows = recordMapper.selectPage(new Page<>(p, size), w);
        CustomerFollowupVO vo = new CustomerFollowupVO();
        vo.setTotal(rows.getTotal());
        vo.setRecords(recordVos(customerId, rows.getRecords()));
        return vo;
    }

    /** 客户详情「跟进」分区：当前跟进对象与下一步、概况，以及时间线第一页 */
    public CustomerFollowupVO customerSummary(Long customerId) {
        CustomerFollowupVO vo = customerRecords(customerId, null, false, 1, RECORD_PAGE);
        LocalDate today = LocalDate.now();
        FollowupItemFilter f = filter(null, today);
        f.setCustomerId(customerId);
        List<FollowupItemDO> open = byRank(itemMapper.rankedIds(f));
        if (!open.isEmpty()) {
            vo.setCurrent(toVos(List.of(open.get(0)), today, false).get(0));
            vo.setNoReplyStreak(nz(open.get(0).getNoReplyStreak()));
        } else {
            vo.setNoReplyStreak(0);
        }
        vo.setStep(open.isEmpty() ? 0 : FollowupConstants.BOARD_SALES);
        vo.setQuoteCount((int) open.stream().filter(i -> i.getTargetType() == FollowupConstants.TARGET_QUOTATION).count());
        vo.setPiCount((int) open.stream().filter(i -> i.getTargetType() == FollowupConstants.TARGET_PI).count());
        Map<String, BigDecimal> amounts = new TreeMap<>();
        for (FollowupItemDO i : open) {
            if (StringUtils.hasText(i.getCurrencyCode()) && i.getTargetType() != FollowupConstants.TARGET_INQUIRY
                    && i.getAmount() != null && i.getAmount().signum() > 0) {
                amounts.merge(i.getCurrencyCode(), i.getAmount(), BigDecimal::add);
            }
        }
        amounts.forEach((cur, amt) -> {
            CustomerFollowupVO.Amount a = new CustomerFollowupVO.Amount();
            a.setCurrencyCode(cur);
            a.setAmount(amt);
            vo.getOpenAmounts().add(a);
        });
        CustomerFollowupDO last = recordMapper.selectOne(new LambdaQueryWrapper<CustomerFollowupDO>()
                .eq(CustomerFollowupDO::getCustomerId, customerId)
                .eq(CustomerFollowupDO::getRecordType, FollowupConstants.RECORD_USER)
                .isNull(CustomerFollowupDO::getDeletedAt)
                .orderByDesc(CustomerFollowupDO::getId)
                .last("LIMIT 1"));
        if (last != null) {
            vo.setLastContactAt(last.getCreateTime());
            vo.setLastChannelName(FollowupConstants.CHANNEL_NAMES.getOrDefault(last.getChannel(), ""));
        }
        Long recent = recordMapper.selectCount(new LambdaQueryWrapper<CustomerFollowupDO>()
                .eq(CustomerFollowupDO::getCustomerId, customerId)
                .eq(CustomerFollowupDO::getRecordType, FollowupConstants.RECORD_USER)
                .isNull(CustomerFollowupDO::getDeletedAt)
                .ge(CustomerFollowupDO::getCreateTime, today.minusDays(30).atStartOfDay()));
        vo.setRecent30(recent == null ? 0 : recent.intValue());
        return vo;
    }

    private List<FollowupRecordVO> recordVos(Long customerId, List<CustomerFollowupDO> rows) {
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Map<String, SopBlockerDO> blockers = blockers();
        Map<Long, String> names = userNames(rows.stream().map(CustomerFollowupDO::getOperatorId).toList());
        Map<Long, String> docNos = new HashMap<>(rows.size() * 2);
        Set<Long> itemIds = rows.stream().map(CustomerFollowupDO::getFollowupItemId).filter(i -> i != null && i > 0)
                .collect(Collectors.toSet());
        if (!itemIds.isEmpty()) {
            itemMapper.selectBatchIds(itemIds).forEach(i -> docNos.put(i.getId(), i.getDocNo()));
        }
        CustomerFollowupDO latestUser = recordMapper.selectOne(new LambdaQueryWrapper<CustomerFollowupDO>()
                .select(CustomerFollowupDO::getId)
                .eq(CustomerFollowupDO::getCustomerId, customerId)
                .eq(CustomerFollowupDO::getRecordType, FollowupConstants.RECORD_USER)
                .isNull(CustomerFollowupDO::getDeletedAt)
                .orderByDesc(CustomerFollowupDO::getId)
                .last("LIMIT 1"));
        long latestUserId = latestUser == null ? 0 : latestUser.getId();
        List<FollowupRecordVO> out = new ArrayList<>(rows.size());
        for (CustomerFollowupDO r : rows) {
            FollowupRecordVO vo = new FollowupRecordVO();
            vo.setId(r.getId());
            vo.setRecordType(r.getRecordType());
            vo.setTitle(r.getTitle());
            vo.setSituation(r.getSituation());
            int s = nz(r.getSituation());
            if ((s & FollowupConstants.SITUATION_FEEDBACK) != 0) {
                vo.getSituationLabels().add("客户给了反馈");
            }
            if ((s & FollowupConstants.SITUATION_NEGOTIATING) != 0) {
                vo.getSituationLabels().add("客户在谈条件");
            }
            if ((s & FollowupConstants.SITUATION_NO_REPLY) != 0) {
                vo.getSituationLabels().add("没回复");
            }
            vo.setBlockerCode(r.getBlockerCode());
            SopBlockerDO b = blockers.get(r.getBlockerCode());
            vo.setBlockerName(b == null ? "" : b.getName());
            vo.setNextAction(r.getNextAction());
            vo.setNextActionDate(r.getNextActionDate());
            vo.setNextDone(r.getRecordType() == FollowupConstants.RECORD_USER && r.getId() < latestUserId);
            vo.setChannel(r.getChannel());
            vo.setChannelName(FollowupConstants.CHANNEL_NAMES.getOrDefault(r.getChannel(), ""));
            vo.setSummary(r.getAiSummary());
            vo.setRawContent(r.getRawContent());
            vo.setStageBefore(r.getStageBefore());
            vo.setStageAfter(r.getStageAfter());
            vo.setProfileFields(profileFields(r.getProfileFields()));
            vo.setOperatorName(r.getRecordType() == FollowupConstants.RECORD_SYSTEM ? "系统"
                    : names.getOrDefault(r.getOperatorId(), ""));
            vo.setTargetType(r.getTargetType());
            vo.setDocNo(docNos.getOrDefault(r.getFollowupItemId(), ""));
            vo.setCreateTime(r.getCreateTime());
            out.add(vo);
        }
        return out;
    }

    private List<FollowupRecordVO.ProfileField> profileFields(String json) {
        List<FollowupRecordVO.ProfileField> out = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return out;
        }
        try {
            Map<String, String> m = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() { });
            m.forEach((k, v) -> {
                FollowupRecordVO.ProfileField pf = new FollowupRecordVO.ProfileField();
                pf.setField(k);
                pf.setLabel(FollowupConstants.PROFILE_LABELS.getOrDefault(k, k));
                pf.setValue(v);
                out.add(pf);
            });
        } catch (JsonProcessingException e) {
            log.warn("跟进记录的档案字段格式不正确，已忽略：{}", e.getOriginalMessage());
        }
        return out;
    }

    // ---------------------------------------------------------------- 行数据

    private List<FollowupItemVO> toVos(List<FollowupItemDO> rows, LocalDate today, boolean withOwner) {
        if (rows.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, CustomerDO> customers = customerMapper.selectBatchIds(
                        rows.stream().map(FollowupItemDO::getCustomerId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(CustomerDO::getId, Function.identity()));
        Map<Long, QuotationDO> quotes = idsOf(rows, FollowupConstants.TARGET_QUOTATION).isEmpty() ? Map.of()
                : quotationMapper.selectBatchIds(idsOf(rows, FollowupConstants.TARGET_QUOTATION)).stream()
                .collect(Collectors.toMap(QuotationDO::getId, Function.identity()));
        Map<Long, ProformaInvoiceDO> pis = idsOf(rows, FollowupConstants.TARGET_PI).isEmpty() ? Map.of()
                : piMapper.selectBatchIds(idsOf(rows, FollowupConstants.TARGET_PI)).stream()
                .collect(Collectors.toMap(ProformaInvoiceDO::getId, Function.identity()));
        Set<Long> lastIds = rows.stream().map(FollowupItemDO::getLastFollowupId).filter(i -> i != null && i > 0)
                .collect(Collectors.toSet());
        Map<Long, CustomerFollowupDO> lasts = lastIds.isEmpty() ? Map.of()
                : recordMapper.selectBatchIds(lastIds).stream()
                .collect(Collectors.toMap(CustomerFollowupDO::getId, Function.identity()));
        Map<Long, String> owners = withOwner ? userNames(rows.stream().map(FollowupItemDO::getOwnerId).toList()) : Map.of();
        Map<String, SopBlockerDO> blockers = blockers();
        OpportunityStageRules.Stages stages = stageRules.load();
        Map<String, Map<String, String>> actions = sync.defaultActions();
        FollowupSettings.Params params = settings.params(TenantContext.getTenantId());
        List<FollowupItemVO> out = new ArrayList<>(rows.size());
        for (FollowupItemDO i : rows) {
            FollowupItemVO vo = new FollowupItemVO();
            vo.setId(i.getId());
            vo.setBoard(i.getBoard());
            vo.setTargetType(i.getTargetType());
            vo.setTargetId(i.getTargetId());
            vo.setDocNo(i.getDocNo());
            vo.setCustomerId(i.getCustomerId());
            CustomerDO c = customers.get(i.getCustomerId());
            if (c != null) {
                vo.setCustomerName(c.getName());
                vo.setCountry(c.getCountry());
                vo.setBizGrade(gradeName(c.getBizGrade()));
                vo.setPotentialRating(nz(c.getPotentialRating()));
            }
            vo.setStageCode(i.getStageCode());
            vo.setStageName(stages.nameOf(i.getStageCode()));
            vo.setStageGroup(i.getStageGroup());
            vo.setCurrencyCode(i.getCurrencyCode());
            vo.setAmount(i.getAmount());
            vo.setReceivedAmount(i.getReceivedAmount());
            vo.setUnpaidAmount(i.getAmount() == null ? null
                    : i.getAmount().subtract(i.getReceivedAmount() == null ? BigDecimal.ZERO : i.getReceivedAmount()));
            vo.setAmountCny(i.getAmountCny());
            vo.setSentAt(i.getSentAt());
            vo.setSentDays(i.getSentAt() == null ? null
                    : (int) ChronoUnit.DAYS.between(i.getSentAt().toLocalDate(), today));
            if (i.getTargetType() == FollowupConstants.TARGET_QUOTATION && quotes.containsKey(i.getTargetId())) {
                vo.setVersionNo(quotes.get(i.getTargetId()).getCurrentVersionNo());
            }
            if (i.getTargetType() == FollowupConstants.TARGET_PI && pis.containsKey(i.getTargetId())) {
                vo.setVersionNo(pis.get(i.getTargetId()).getCurrentVersionNo());
                vo.setReceiptStatus(pis.get(i.getTargetId()).getReceiptStatus());
            }
            vo.setExpectedPayDate(i.getExpectedPayDate());
            vo.setBlockerCode(i.getBlockerCode());
            SopBlockerDO b = blockers.get(i.getBlockerCode());
            vo.setBlockerName(b == null ? "" : b.getName());
            CustomerFollowupDO last = lasts.get(i.getLastFollowupId());
            vo.setLastFollowupAt(i.getLastFollowupAt());
            if (last != null) {
                vo.setLastChannelName(FollowupConstants.CHANNEL_NAMES.getOrDefault(last.getChannel(), ""));
                vo.setLastSummary(StringUtils.hasText(last.getAiSummary()) ? last.getAiSummary() : last.getNextAction());
            }
            vo.setNextAction(i.getNextAction());
            vo.setNextActionDate(i.getNextActionDate());
            vo.setNextActionSource(i.getNextActionSource());
            vo.setOverdueDays(due(i) && i.getNextActionDate().isBefore(today)
                    ? (int) ChronoUnit.DAYS.between(i.getNextActionDate(), today) : 0);
            vo.setSnoozeCount(nz(i.getSnoozeCount()));
            vo.setManySnoozes(withOwner && nz(i.getSnoozeCount()) >= FollowupConstants.SNOOZE_WARN_TIMES);
            vo.setNoReplyStreak(nz(i.getNoReplyStreak()));
            vo.setNurtureUntil(i.getNurtureUntil());
            vo.setNurtureReason(i.getNurtureReason());
            SopBlockerDO nb = blockers.get(i.getNurtureReason());
            vo.setNurtureReasonName(nb == null ? "" : nb.getName());
            vo.setSuggestNurture(i.getNurtureUntil() == null && Cadence.suggestNurture(nz(i.getStageGroup()),
                    i.getSentAt() == null ? i.getCadenceFrom() : i.getSentAt().toLocalDate(),
                    nz(i.getFeedbackSeen()) > 0, nz(i.getNoReplyStreak()), today, params));
            vo.setSopHint(actions.getOrDefault(i.getStageCode(), Map.of()).getOrDefault("default", ""));
            vo.setOwnerId(i.getOwnerId());
            vo.setOwnerName(owners.getOrDefault(i.getOwnerId(), ""));
            vo.setOpportunityId(i.getOpportunityId());
            out.add(vo);
        }
        return out;
    }

    private static List<Long> idsOf(List<FollowupItemDO> rows, int type) {
        return rows.stream().filter(r -> r.getTargetType() == type).map(FollowupItemDO::getTargetId).toList();
    }

    private static String gradeName(Integer g) {
        if (g == null) {
            return "";
        }
        return switch (g) {
            case 1 -> "S";
            case 2 -> "A";
            case 3 -> "B";
            case 4 -> "C";
            default -> "";
        };
    }

    /** 按排序后的 ID 取出跟进对象，保持顺序 */
    private List<FollowupItemDO> byRank(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, FollowupItemDO> m = itemMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(FollowupItemDO::getId, Function.identity()));
        return ids.stream().map(m::get).filter(Objects::nonNull).toList();
    }

    private FollowupItemFilter filter(Collection<Long> owners, LocalDate today) {
        FollowupSettings.Params p = settings.params(TenantContext.getTenantId());
        List<BigDecimal> t = p.amountTiers();
        FollowupItemFilter f = new FollowupItemFilter();
        f.setOwnerIds(owners);
        f.setToday(today);
        f.setTier1(t.size() > 0 ? t.get(0) : BigDecimal.ZERO);
        f.setTier2(t.size() > 1 ? t.get(1) : f.getTier1());
        f.setTier3(t.size() > 2 ? t.get(2) : f.getTier2());
        return f;
    }

    // ---------------------------------------------------------------- 可见范围

    /**
     * 负责人范围 → 负责人 ID 集合；null 表示不限（数据权限为全部）；空集合表示什么都看不到。
     * 没有「查看团队」权限时只能看自己。
     */
    private Collection<Long> owners(String ownerScope) {
        Long me = currentUser.resolve();
        if (isMe(ownerScope) || !perm.has(FollowupConstants.PERM_TEAM)) {
            return me == null ? List.of() : List.of(me);
        }
        DataScope scope = dataScopeResolver.current();
        if (SCOPE_ALL.equals(ownerScope)) {
            return scope.isAll() ? null : scope.ownerIds();
        }
        long owner;
        try {
            owner = Long.parseLong(ownerScope.trim());
        } catch (NumberFormatException e) {
            throw new BizException("负责人范围不正确", e);
        }
        if (!scope.canSee(owner) && !Objects.equals(owner, me)) {
            throw new BizException("没有权限查看这位业务员的跟进");
        }
        return List.of(owner);
    }

    private static boolean isMe(String ownerScope) {
        return !StringUtils.hasText(ownerScope) || SCOPE_ME.equals(ownerScope);
    }

    /** 未结束、在可见范围内（自己负责的，或有「查看团队」权限且在数据权限内的） */
    private FollowupItemDO requireOpen(Long id) {
        FollowupItemDO item = id == null ? null : itemMapper.selectById(id);
        if (item == null || item.getDeletedAt() != null) {
            throw new BizException("跟进对象不存在");
        }
        Long me = currentUser.resolve();
        boolean visible = Objects.equals(item.getOwnerId(), me)
                || (perm.has(FollowupConstants.PERM_TEAM) && dataScopeResolver.current().canSee(item.getOwnerId()));
        if (!visible) {
            throw new BizException("跟进对象不存在");
        }
        if (item.getClosedAt() != null) {
            throw new BizException("这条跟进已经结束，请刷新后重试");
        }
        return item;
    }

    private Map<Long, String> userNames(Collection<Long> ids) {
        Set<Integer> keys = ids.stream().filter(i -> i != null && i > 0).map(Long::intValue).collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>(keys.size() * 2 + 1);
        if (!keys.isEmpty()) {
            for (UserBasicDO u : userBasicMapper.selectBatchIds(keys)) {
                names.put(u.getId().longValue(), u.getName());
            }
        }
        return names;
    }

    private static boolean due(FollowupItemDO r) {
        return r.getNextActionDate() != null;
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static long nzl(Long v) {
        return v == null ? 0 : v;
    }
}
