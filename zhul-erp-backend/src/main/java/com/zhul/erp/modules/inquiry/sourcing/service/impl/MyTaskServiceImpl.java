package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.dto.ItemQuotesRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.MyQuoteVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.MyTaskDetailVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.MyTaskItemVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.MyTaskVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.PartTimeBoardVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.PastePreviewVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.QuoteEntryRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.ReturnTaskRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.SaveQuotesRequest;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskAssigneeDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskAssigneeMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskMapper;
import com.zhul.erp.modules.inquiry.sourcing.service.MyTaskService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.SourcingSettings;
import com.zhul.erp.modules.inquiry.support.TaskTimeout;
import com.zhul.erp.modules.inquiry.support.QuoteDictSnapshot;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import com.zhul.erp.modules.inquiry.support.QuoteDicts;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.quotation.support.QuotationLocks;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MyTaskServiceImpl implements MyTaskService {

    private final SourcingTaskMapper taskMapper;
    private final SourcingTaskAssigneeMapper assigneeMapper;
    private final SourcingQuoteMapper quoteMapper;
    private final InquiryItemMapper itemMapper;
    private final CustomerInquiryMapper inquiryMapper;
    private final SourcingProgress progress;
    private final SourcingSettings settings;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;
    private final QuoteDicts quoteDicts;
    private final InquiryLookups lookups;
    private final SupplierMapper supplierMapper;
    private final LogService logService;
    private final QuotationLocks quotationLocks;
    private final com.zhul.erp.modules.product.candidate.service.ProductArchiver productArchiver;

    /** 兼职工作台按提交统计工作量：已提交、待审核、审核时作废的都算，与是否审核通过无关 */
    private static final List<Integer> WORK_STATUSES = List.of(InquiryConstants.QUOTE_SUBMITTED,
            InquiryConstants.QUOTE_PENDING_REVIEW, InquiryConstants.QUOTE_VOIDED);

    @Override
    public List<MyTaskVO> myTasks(boolean done) {
        Long me = me();
        List<SourcingTaskAssigneeDO> mine = activeOf(me);
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<Long, SourcingTaskAssigneeDO> byTask = mine.stream()
                .collect(Collectors.toMap(SourcingTaskAssigneeDO::getTaskId, a -> a, (a, b) -> a));
        List<SourcingTaskDO> tasks = taskMapper.selectList(new LambdaQueryWrapper<SourcingTaskDO>()
                .in(SourcingTaskDO::getId, byTask.keySet())
                .eq(SourcingTaskDO::getStatus, done ? InquiryConstants.TASK_DONE : InquiryConstants.TASK_SOURCING)
                .isNull(SourcingTaskDO::getDeletedAt));
        TaskTimeout limits = TaskTimeout.of(settings, tenantId());
        LocalDateTime now = LocalDateTime.now();
        Map<Long, InquiryLookups.InquiryBrief> briefs = lookups.briefs(tasks.stream().map(SourcingTaskDO::getCustomerInquiryId).toList(),
                lookups.isPartTime(me));
        List<MyTaskVO> list = new ArrayList<>(tasks.size());
        for (SourcingTaskDO t : tasks) {
            MyTaskVO vo = toVo(t, byTask.get(t.getId()), me, limits, now);
            applyBrief(vo, briefs.get(t.getCustomerInquiryId()));
            list.add(vo);
        }
        // 待处理：超时 → 紧急 → 更新时间倒序；已回价：更新时间倒序
        Comparator<MyTaskVO> latest = Comparator.comparing(MyTaskVO::getUpdateTime, Comparator.nullsLast(Comparator.reverseOrder()));
        list.sort(done ? latest : Comparator.comparing((MyTaskVO v) -> !Boolean.TRUE.equals(v.getTimeout()))
                .thenComparing(v -> !Boolean.TRUE.equals(v.getUrgent()))
                .thenComparing(latest));
        return list;
    }

    @Override
    public PartTimeBoardVO partTimeBoard() {
        Long me = me();
        List<MyTaskVO> pending = myTasks(false);
        PartTimeBoardVO vo = new PartTimeBoardVO();
        vo.setPendingTasks(pending.size());
        vo.setTimeoutTasks((int) pending.stream().filter(t -> Boolean.TRUE.equals(t.getTimeout())).count());
        vo.setUrgentTasks((int) pending.stream().filter(t -> Boolean.TRUE.equals(t.getUrgent())).count());
        vo.setTodo(pending.subList(0, Math.min(5, pending.size())));

        LocalDate today = LocalDate.now();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDate trendStart = today.minusDays(29);
        LocalDateTime from = monthStart.isBefore(trendStart.atStartOfDay()) ? monthStart : trendStart.atStartOfDay();
        List<SourcingQuoteDO> quotes = quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                .select(SourcingQuoteDO::getInquiryItemId, SourcingQuoteDO::getNoStock, SourcingQuoteDO::getQuotedAt)
                .eq(SourcingQuoteDO::getTenantId, tenantId())
                .eq(SourcingQuoteDO::getQuotedBy, me)
                .in(SourcingQuoteDO::getStatus, WORK_STATUSES)
                .ge(SourcingQuoteDO::getQuotedAt, from)
                .isNull(SourcingQuoteDO::getDeletedAt));
        Set<Long> monthItems = new HashSet<>();
        int monthQuotes = 0;
        int monthNoStock = 0;
        Map<LocalDate, Set<Long>> byDay = new LinkedHashMap<>(64);
        for (int i = 0; i < 30; i++) {
            byDay.put(trendStart.plusDays(i), new HashSet<>());
        }
        for (SourcingQuoteDO q : quotes) {
            if (!q.getQuotedAt().isBefore(monthStart)) {
                monthItems.add(q.getInquiryItemId());
                if (Objects.equals(q.getNoStock(), 1)) {
                    monthNoStock++;
                } else {
                    monthQuotes++;
                }
            }
            Set<Long> day = byDay.get(q.getQuotedAt().toLocalDate());
            if (day != null) {
                day.add(q.getInquiryItemId());
            }
        }
        vo.setMonthQuotedItems(monthItems.size());
        vo.setMonthQuotes(monthQuotes);
        vo.setMonthNoStock(monthNoStock);
        vo.setTrend(byDay.entrySet().stream().map(e -> {
            PartTimeBoardVO.DayCount d = new PartTimeBoardVO.DayCount();
            d.setDate(e.getKey());
            d.setItems(e.getValue().size());
            return d;
        }).toList());
        vo.setTotalQuotedItems(quoteMapper.selectObjs(new QueryWrapper<SourcingQuoteDO>()
                        .select("COUNT(DISTINCT inquiry_item_id)")
                        .eq("tenant_id", tenantId())
                        .eq("quoted_by", me)
                        .in("status", WORK_STATUSES)
                        .isNull("deleted_at"))
                .stream().findFirst().map(o -> ((Number) o).intValue()).orElse(0));

        List<SourcingTaskAssigneeDO> done = assigneeMapper.selectList(new LambdaQueryWrapper<SourcingTaskAssigneeDO>()
                .eq(SourcingTaskAssigneeDO::getTenantId, tenantId())
                .eq(SourcingTaskAssigneeDO::getAssigneeId, me)
                .ge(SourcingTaskAssigneeDO::getSubmittedAt, monthStart)
                .isNull(SourcingTaskAssigneeDO::getDeletedAt));
        vo.setMonthCompletedTasks(done.size());
        if (!done.isEmpty()) {
            long minutes = done.stream()
                    .mapToLong(a -> Math.max(0, Duration.between(a.getAssignedAt(), a.getSubmittedAt()).toMinutes()))
                    .sum();
            vo.setMonthAvgHours(BigDecimal.valueOf(minutes)
                    .divide(BigDecimal.valueOf(60L * done.size()), 1, RoundingMode.HALF_UP));
        }
        return vo;
    }

    private MyTaskVO toVo(SourcingTaskDO t, SourcingTaskAssigneeDO mine, Long me, TaskTimeout limits, LocalDateTime now) {
        MyTaskVO vo = new MyTaskVO();
        vo.setId(t.getId());
        vo.setTaskCode(t.getTaskCode());
        vo.setBrand(t.getBrand());
        vo.setCategory(t.getCategory());
        vo.setItemCount(t.getItemCount());
        // 保存回价会软删旧记录再写新记录，所以连已删除的一起取最大更新时间
        SourcingQuoteDO last = quoteMapper.selectOne(new LambdaQueryWrapper<SourcingQuoteDO>()
                .select(SourcingQuoteDO::getUpdateTime)
                .eq(SourcingQuoteDO::getTaskId, t.getId())
                .eq(SourcingQuoteDO::getQuotedBy, me)
                .orderByDesc(SourcingQuoteDO::getUpdateTime)
                .last("LIMIT 1"));
        LocalDateTime quoted = last == null ? null : last.getUpdateTime();
        vo.setUpdateTime(quoted != null && (t.getUpdateTime() == null || quoted.isAfter(t.getUpdateTime())) ? quoted : t.getUpdateTime());
        vo.setFilledCount((int) quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                        .select(SourcingQuoteDO::getInquiryItemId)
                        .eq(SourcingQuoteDO::getTaskId, t.getId())
                        .eq(SourcingQuoteDO::getQuotedBy, me)
                        .isNull(SourcingQuoteDO::getDeletedAt))
                .stream().map(SourcingQuoteDO::getInquiryItemId).distinct().count());
        vo.setStatus(t.getStatus());
        vo.setUrgent(Objects.equals(t.getUrgent(), 1));
        vo.setTimeout(limits.isTimeout(t, now));
        vo.setRemainingMinutes(limits.remainingMinutes(t, now));
        vo.setAssignedAt(mine == null ? null : mine.getAssignedAt());
        vo.setShared(assigneeMapper.selectCount(new LambdaQueryWrapper<SourcingTaskAssigneeDO>()
                .eq(SourcingTaskAssigneeDO::getTaskId, t.getId())
                .eq(SourcingTaskAssigneeDO::getActive, 1)) > 1);
        return vo;
    }

    @Override
    public MyTaskDetailVO detail(Long taskId) {
        Long me = me();
        SourcingTaskDO task = myTask(taskId);
        SourcingTaskAssigneeDO mine = activeOf(me).stream().filter(a -> a.getTaskId().equals(taskId)).findFirst().orElse(null);
        List<InquiryItemDO> items = taskItems(taskId);
        Map<Long, List<SourcingQuoteDO>> quotes = quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                        .eq(SourcingQuoteDO::getTaskId, taskId)
                        .eq(SourcingQuoteDO::getQuotedBy, me)
                        .isNull(SourcingQuoteDO::getDeletedAt)
                        .orderByAsc(SourcingQuoteDO::getId))
                .stream().collect(Collectors.groupingBy(SourcingQuoteDO::getInquiryItemId));
        boolean review = needsReview(me);
        Map<Long, String> reviewers = review ? lookups.userNames(quotes.values().stream().flatMap(List::stream)
                .map(SourcingQuoteDO::getReviewedBy).filter(Objects::nonNull).distinct().toList()) : Map.of();
        Set<Long> locked = quotationLocks.lockedItemIds(items.stream().map(InquiryItemDO::getId).toList());
        List<MyTaskItemVO> rows = new ArrayList<>(items.size());
        for (InquiryItemDO i : items) {
            MyTaskItemVO vo = new MyTaskItemVO();
            vo.setLocked(locked.contains(i.getId()));
            vo.setId(i.getId());
            vo.setModel(i.getConfirmedModel());
            vo.setOriginalModel(i.getOriginalModel());
            vo.setQuantity(i.getQuantity());
            vo.setUnit(i.getUnit());
            vo.setDescription(i.getDescription());
            vo.setLifecycle(i.getLifecycle());
            vo.setReplacementModel(i.getReplacementModel());
            vo.setActualModel(i.getActualModel());
            vo.setArchiveStatus(i.getArchiveStatus());
            vo.setArchiveStatusName(com.zhul.erp.modules.product.candidate.constants.CandidateConstants.ARCHIVE_NAMES.get(i.getArchiveStatus()));
            vo.setProductId(i.getProductId());
            vo.setDifficulty(i.getDifficulty());
            vo.setInquiryScript(StringUtils.hasText(i.getInquiryScript()) ? i.getInquiryScript() : defaultScript(i));
            vo.setSearchKeywords(keywords(i));
            List<SourcingQuoteDO> own = quotes.getOrDefault(i.getId(), List.of());
            if (review) {
                own = applyReviewState(vo, own, reviewers);
            }
            vo.setQuotes(own.stream().map(MyTaskServiceImpl::toQuoteVo).toList());
            rows.add(vo);
        }
        MyTaskDetailVO vo = new MyTaskDetailVO();
        vo.setTask(toVo(task, mine, me, TaskTimeout.of(settings, tenantId()), LocalDateTime.now()));
        applyBrief(vo.getTask(), lookups.briefs(List.of(task.getCustomerInquiryId()), lookups.isPartTime(me)).get(task.getCustomerInquiryId()));
        if (!items.isEmpty() && locked.size() == items.size()) {
            vo.getTask().setEditable(false);
        }
        vo.setItems(rows);
        vo.setReviewRequired(review);
        return vo;
    }

    /**
     * 兼职采购的型号审核状态；返回要展示的记录：有草稿或待审核时只展示正在改的这一版，
     * 否则展示上次审核通过的版本（含作废的记录）。
     */
    private static List<SourcingQuoteDO> applyReviewState(MyTaskItemVO vo, List<SourcingQuoteDO> mine, Map<Long, String> reviewers) {
        List<SourcingQuoteDO> working = mine.stream().filter(q -> q.getStatus() == InquiryConstants.QUOTE_DRAFT
                || q.getStatus() == InquiryConstants.QUOTE_PENDING_REVIEW).toList();
        SourcingQuoteDO rejected = working.stream()
                .filter(q -> q.getStatus() == InquiryConstants.QUOTE_DRAFT && StringUtils.hasText(q.getReviewNote()))
                .findFirst().orElse(null);
        SourcingQuoteDO approved = mine.stream().filter(q -> q.getStatus() == InquiryConstants.QUOTE_SUBMITTED).findFirst().orElse(null);
        SourcingQuoteDO basis = null;
        if (working.stream().anyMatch(q -> q.getStatus() == InquiryConstants.QUOTE_PENDING_REVIEW)) {
            vo.setReviewStatus(InquiryConstants.REVIEW_PENDING);
        } else if (rejected != null) {
            vo.setReviewStatus(InquiryConstants.REVIEW_REJECTED);
            vo.setReviewNote(rejected.getReviewNote());
            basis = rejected;
        } else if (approved != null) {
            vo.setReviewStatus(InquiryConstants.REVIEW_APPROVED);
            basis = approved;
        }
        if (basis != null) {
            vo.setReviewedByName(reviewers.get(basis.getReviewedBy()));
            vo.setReviewedAt(basis.getReviewedAt());
        }
        return working.isEmpty() ? mine : working;
    }

    /** 没有 AI 话术时的默认话术，沿用知识库的询价模版 */
    static String defaultScript(InquiryItemDO i) {
        String qty = i.getQuantity() + (StringUtils.hasText(i.getUnit()) ? i.getUnit() : "个");
        return "帮我查一下 " + i.getBrand() + " 的 " + i.getConfirmedModel() + "，要 " + qty + "，报不含税价，有货的话货期也说一下，谢谢。";
    }

    /** 货源搜索关键词；没有时退回到「精确型号」「品牌 + 型号」 */
    List<String> keywords(InquiryItemDO i) {
        List<String> list = new ArrayList<>();
        if (StringUtils.hasText(i.getSearchKeywords())) {
            try {
                list.addAll(objectMapper.readValue(i.getSearchKeywords(), new TypeReference<List<String>>() { }));
            } catch (JsonProcessingException e) {
                list.clear();
            }
        }
        if (list.isEmpty()) {
            list.add(i.getConfirmedModel());
            list.add(i.getBrand() + " " + i.getConfirmedModel());
        }
        return list;
    }

    private static MyQuoteVO toQuoteVo(SourcingQuoteDO q) {
        MyQuoteVO vo = new MyQuoteVO();
        vo.setId(q.getId());
        vo.setChannel(q.getChannel());
        vo.setShopName(q.getShopName());
        vo.setSupplierId(q.getSupplierId());
        vo.setUnitPrice(q.getUnitPrice());
        vo.setTaxIncluded(Objects.equals(q.getTaxIncluded(), 1));
        vo.setTaxRate(q.getTaxRate());
        vo.setItemCondition(q.getItemCondition());
        vo.setLeadTime(q.getLeadTime());
        vo.setNote(q.getNote());
        vo.setNoStock(Objects.equals(q.getNoStock(), 1));
        vo.setRecommended(Objects.equals(q.getRecommended(), 1));
        vo.setStatus(q.getStatus());
        vo.setReviewNote(q.getReviewNote());
        vo.setQuotedAt(q.getQuotedAt());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void saveQuotes(Long taskId, SaveQuotesRequest req) {
        Long me = me();
        SourcingTaskDO task = myTask(taskId);
        // 先锁客户询盘行再写任何数据：多人比价同时提交时按同一顺序加锁，避免死锁
        progress.lock(task.getCustomerInquiryId());
        requireEditable(task, req.getItems().stream().map(ItemQuotesRequest::getItemId).toList());
        boolean submit = Boolean.TRUE.equals(req.getSubmit());
        List<QuoteDraft> drafts = new ArrayList<>();
        for (ItemQuotesRequest item : req.getItems()) {
            if (Boolean.TRUE.equals(item.getNoStock())) {
                drafts.add(new QuoteDraft(item.getItemId(), true, InquiryConstants.CHANNEL_OTHER, "", null, null, false, null, 0, 0,
                        item.getNoStockNote(), false));
                continue;
            }
            for (QuoteEntryRequest q : item.getQuotes() == null ? List.<QuoteEntryRequest>of() : item.getQuotes()) {
                drafts.add(new QuoteDraft(item.getItemId(), false, q.getChannel(), q.getShopName(), q.getSupplierId(), q.getUnitPrice(),
                        Boolean.TRUE.equals(q.getTaxIncluded()), q.getTaxRate(), q.getItemCondition(), q.getLeadTime(), q.getNote(),
                        Boolean.TRUE.equals(q.getRecommended())));
            }
        }
        List<Long> itemIds = req.getItems().stream().map(ItemQuotesRequest::getItemId).distinct().toList();
        boolean review = needsReview(me);
        // 本次涉及的型号，先清掉本人尚未提交的草稿；提交时连本人已提交的一起替换（修改回价），旧版本软删除并留痕。
        // 兼职采购提交时只替换草稿与待审核的记录，上次审核通过的版本保留到新版本审核通过（见 QuoteReviewServiceImpl）
        List<SourcingQuoteDO> replaced = submit && !review ? quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                .eq(SourcingQuoteDO::getTaskId, taskId)
                .eq(SourcingQuoteDO::getQuotedBy, me)
                .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED)
                .in(SourcingQuoteDO::getInquiryItemId, itemIds)
                .isNull(SourcingQuoteDO::getDeletedAt)) : List.of();
        quoteMapper.update(new SourcingQuoteDO(), new LambdaUpdateWrapper<SourcingQuoteDO>()
                .eq(SourcingQuoteDO::getTaskId, taskId)
                .eq(SourcingQuoteDO::getQuotedBy, me)
                .in(SourcingQuoteDO::getStatus, !submit ? List.of(InquiryConstants.QUOTE_DRAFT)
                        : review ? List.of(InquiryConstants.QUOTE_DRAFT, InquiryConstants.QUOTE_PENDING_REVIEW)
                        : List.of(InquiryConstants.QUOTE_DRAFT, InquiryConstants.QUOTE_SUBMITTED))
                .in(SourcingQuoteDO::getInquiryItemId, itemIds)
                .isNull(SourcingQuoteDO::getDeletedAt)
                .set(SourcingQuoteDO::getDeletedAt, LocalDateTime.now()));
        writeQuotes(task, me, drafts, submit, InquiryConstants.ENTRY_ONLINE, null);
        updateLifecycles(task, req.getItems());
        productArchiver.sync(updateActualModels(task, req.getItems()));
        if (submit) {
            afterSubmit(task, me, itemIds);
            if (!replaced.isEmpty()) {
                QuoteDictSnapshot dicts = quoteDicts.snapshot();
                logService.recordOperateLog("我的询价任务", "修改回价",
                        Map.of("taskCode", task.getTaskCode(), "quotes", summaries(replaced, dicts)),
                        Map.of("taskCode", task.getTaskCode(), "quotes", summaries(quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                                .eq(SourcingQuoteDO::getTaskId, taskId)
                                .eq(SourcingQuoteDO::getQuotedBy, me)
                                .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED)
                                .in(SourcingQuoteDO::getInquiryItemId, itemIds)
                                .isNull(SourcingQuoteDO::getDeletedAt)), dicts)));
            }
        }
    }

    private static int taxRateOf(QuoteDraft d) {
        return d.taxRate() == null ? InquiryConstants.DEFAULT_TAX_RATE : d.taxRate();
    }

    /** 不含税价 = 含税价 ÷ (1 + 税率%)，中间按 10 位小数计算，最终两位小数 HALF_UP */
    static BigDecimal excludeTax(BigDecimal taxedPrice, int ratePercent) {
        BigDecimal divisor = BigDecimal.ONE.add(BigDecimal.valueOf(ratePercent).movePointLeft(2));
        return taxedPrice.divide(divisor, 10, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
    }

    /** 客户询盘已取消后整单只读；型号已报给客户（出现在已发送及之后的报价单中）后该型号只读 */
    private void requireEditable(SourcingTaskDO task, List<Long> itemIds) {
        CustomerInquiryDO inquiry = inquiryMapper.selectById(task.getCustomerInquiryId());
        if (inquiry != null && inquiry.getStatus() == InquiryConstants.STATUS_CANCELLED) {
            throw new BizException("客户询盘已取消，回价不能再修改");
        }
        Set<Long> locked = quotationLocks.lockedItemIds(itemIds);
        if (!locked.isEmpty()) {
            String models = itemMapper.selectBatchIds(locked).stream().map(InquiryItemDO::getConfirmedModel).collect(Collectors.joining("、"));
            throw new BizException(models + " 已报给客户，回价不能再修改");
        }
    }

    @Override
    public PastePreviewVO pastePreview(Long taskId, String text) {
        myTask(taskId);
        List<InquiryItemDO> items = taskItems(taskId);
        Set<Long> locked = quotationLocks.lockedItemIds(items.stream().map(InquiryItemDO::getId).toList());
        QuoteTextParser.Result r = QuoteTextParser.parse(text, items.stream()
                .map(i -> new QuoteTextParser.Model(i.getId(), i.getConfirmedModel())).toList(), quoteDicts.snapshot());
        PastePreviewVO vo = new PastePreviewVO();
        vo.setCommon(r.common());
        vo.setRows(r.lines().stream().map(l -> {
            PastePreviewVO.Row row = new PastePreviewVO.Row();
            row.setRaw(l.raw());
            row.setItemId(l.itemId());
            row.setModel(l.model());
            row.setUnitPrice(l.unitPrice());
            row.setTaxIncluded(l.taxIncluded());
            row.setTaxRate(l.taxRate());
            row.setItemCondition(l.condition());
            row.setLeadTime(l.leadTime());
            row.setNote(l.note());
            row.setStatus(l.status().name());
            row.setLocked(l.itemId() != null && locked.contains(l.itemId()));
            return row;
        }).toList());
        return vo;
    }

    /** 留痕用的简要内容：型号、渠道、店铺、单价、货况、货期、是否推荐 / 无货 */
    private static List<String> summaries(List<SourcingQuoteDO> quotes, QuoteDictSnapshot dicts) {
        return quotes.stream().map(q -> Objects.equals(q.getNoStock(), 1)
                ? q.getModel() + " 无货 " + q.getNote()
                : String.join(" ", q.getModel(), InquiryConstants.CHANNELS.getOrDefault(q.getChannel(), ""), q.getShopName(),
                        "¥" + q.getUnitPriceCny() + (Objects.equals(q.getTaxIncluded(), 1) ? "（含税价 ¥" + q.getUnitPrice() + "，税率 " + q.getTaxRate().stripTrailingZeros().toPlainString() + "%）" : ""),
                        dicts.conditions().label(q.getItemCondition()), dicts.leadTimes().label(q.getLeadTime()),
                        Objects.equals(q.getRecommended(), 1) ? "推荐" : "").trim()).toList();
    }

    @Override
    public void writeQuotes(SourcingTaskDO task, Long quotedBy, List<QuoteDraft> drafts, boolean submit, int entryMode, Long importId) {
        Map<Long, InquiryItemDO> items = taskItems(task.getId()).stream().collect(Collectors.toMap(InquiryItemDO::getId, i -> i));
        QuoteDictSnapshot dicts = quoteDicts.snapshot();
        LocalDateTime now = LocalDateTime.now();
        // 兼职采购不标推荐，推荐由审核人选定
        boolean review = needsReview(quotedBy);
        java.util.Set<QuoteDraft> recommended = review ? java.util.Set.of() : recommendedDrafts(drafts, items);
        String batch = java.util.UUID.randomUUID().toString();
        for (QuoteDraft d : drafts) {
            InquiryItemDO item = items.get(d.itemId());
            if (item == null) {
                throw new BizException("型号不属于这个询价任务，请刷新后再试");
            }
            if (!d.noStock()) {
                if (d.unitPrice() == null) {
                    throw new BizException(item.getConfirmedModel() + " 请填写单价，或勾选无货");
                }
                if (d.unitPrice().signum() < 0) {
                    throw new BizException("单价不能为负");
                }
                if (d.channel() == null || !InquiryConstants.CHANNELS.containsKey(d.channel())) {
                    throw new BizException(item.getConfirmedModel() + " 请选择渠道");
                }
                if (!dicts.conditions().acceptable(d.itemCondition())) {
                    throw new BizException(item.getConfirmedModel() + " 的货况不在可选值内，请从下拉中选择");
                }
                if (!dicts.leadTimes().acceptable(d.leadTime())) {
                    throw new BizException(item.getConfirmedModel() + " 的货期不在可选值内，请从下拉中选择");
                }
                if (d.taxIncluded() && !dicts.taxRates().enabled().contains(taxRateOf(d))) {
                    throw new BizException(item.getConfirmedModel() + " 的税率不在可选值内，请从下拉中选择");
                }
            }
            SupplierDO supplier = null;
            if (!d.noStock() && Objects.equals(d.channel(), InquiryConstants.CHANNEL_SUPPLIER)) {
                supplier = d.supplierId() == null ? null : supplierMapper.selectById(d.supplierId());
                if (supplier == null || supplier.getDeletedAt() != null || !Objects.equals(supplier.getTenantId(), task.getTenantId())) {
                    throw new BizException(item.getConfirmedModel() + " 渠道为供应商时，请从供应商列表中选择");
                }
            }
            SourcingQuoteDO q = new SourcingQuoteDO();
            q.setTenantId(task.getTenantId());
            q.setInquiryItemId(item.getId());
            q.setTaskId(task.getId());
            q.setCustomerInquiryId(task.getCustomerInquiryId());
            q.setBrand(item.getBrand());
            q.setBrandKey(item.getBrandKey());
            q.setModel(item.getConfirmedModel());
            q.setModelKey(item.getModelKey());
            q.setChannel(d.channel() == null ? InquiryConstants.CHANNEL_OTHER : d.channel());
            // 供应商渠道以供应商主数据的名称为准，避免手填的名称对不上
            q.setShopName(supplier != null ? text(supplier.getName(), 128) : text(d.shopName(), 128));
            q.setSupplierId(supplier != null ? supplier.getId() : null);
            q.setNoStock(d.noStock() ? 1 : 0);
            q.setCurrencyCode(InquiryConstants.BASE_CURRENCY);
            if (!d.noStock()) {
                BigDecimal price = d.unitPrice().setScale(2, RoundingMode.HALF_UP);
                q.setUnitPrice(price);
                q.setExchangeRate(BigDecimal.ONE.setScale(6, RoundingMode.HALF_UP));
                // 本位币单价一律存不含税价：比价、采购成本价、历史询价最低价都按它
                q.setUnitPriceCny(d.taxIncluded() ? excludeTax(price, taxRateOf(d)) : price);
            }
            boolean taxed = !d.noStock() && d.taxIncluded();
            q.setTaxIncluded(taxed ? 1 : 0);
            q.setTaxRate(taxed ? BigDecimal.valueOf(taxRateOf(d)).setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO.setScale(2));
            q.setItemCondition(d.noStock() || d.itemCondition() == null ? 0 : d.itemCondition());
            q.setLeadTime(d.noStock() || d.leadTime() == null ? 0 : d.leadTime());
            q.setNote(text(d.note(), 300));
            q.setRecommended(recommended.contains(d) ? 1 : 0);
            q.setQuotedBy(quotedBy);
            q.setQuotedAt(submit ? now : null);
            q.setStatus(submit ? submittedStatus(review) : InquiryConstants.QUOTE_DRAFT);
            q.setEntryMode(entryMode);
            q.setImportId(importId);
            q.setSubmitBatch(batch);
            quoteMapper.insert(q);
        }
    }

    /**
     * 每个型号本次的推荐报价：采购标了的用标的（最多一条）；都没标时按全新原装最低、其次最低价自动推荐。
     * 用对象身份比较，同样内容的两条也能区分。
     */
    private static java.util.Set<QuoteDraft> recommendedDrafts(List<QuoteDraft> drafts, Map<Long, InquiryItemDO> items) {
        java.util.Set<QuoteDraft> result = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        Map<Long, List<QuoteDraft>> byItem = drafts.stream().filter(d -> !d.noStock() && d.unitPrice() != null)
                .collect(Collectors.groupingBy(QuoteDraft::itemId, java.util.LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<Long, List<QuoteDraft>> e : byItem.entrySet()) {
            List<QuoteDraft> flagged = e.getValue().stream().filter(QuoteDraft::recommended).toList();
            if (flagged.size() > 1) {
                InquiryItemDO item = items.get(e.getKey());
                throw new BizException((item == null ? "" : item.getConfirmedModel() + " ") + "只能标一条推荐报价");
            }
            // 按不含税价比较，含税报价先换算
            Comparator<QuoteDraft> cheapest = Comparator.comparing(d -> d.taxIncluded() ? excludeTax(d.unitPrice(), taxRateOf(d)) : d.unitPrice());
            QuoteDraft pick = !flagged.isEmpty() ? flagged.get(0) : e.getValue().stream()
                    .filter(d -> Objects.equals(d.itemCondition(), InquiryConstants.CONDITION_NEW)).min(cheapest)
                    .orElseGet(() -> e.getValue().stream().min(cheapest).orElse(null));
            if (pick != null) {
                result.add(pick);
            }
        }
        return result;
    }

    /** 采购询价时顺手核实的生产状态写回型号明细；停产时记录替代型号，其余状态清空替代型号 */
    private void updateLifecycles(SourcingTaskDO task, List<ItemQuotesRequest> reqItems) {
        Map<Long, InquiryItemDO> items = taskItems(task.getId()).stream().collect(Collectors.toMap(InquiryItemDO::getId, i -> i));
        QuoteDictSnapshot.Dict lifecycles = null;
        for (ItemQuotesRequest r : reqItems) {
            InquiryItemDO item = items.get(r.getItemId());
            if (r.getLifecycle() == null || item == null) {
                continue;
            }
            if (lifecycles == null) {
                lifecycles = quoteDicts.snapshot().lifecycles();
            }
            if (!lifecycles.enabled().contains(r.getLifecycle())) {
                throw new BizException(item.getConfirmedModel() + " 的生产状态不在可选值内，请从下拉中选择");
            }
            String replacement = r.getLifecycle() == InquiryConstants.LIFECYCLE_DISCONTINUED
                    ? text(r.getReplacementModel(), 128) : "";
            if (Objects.equals(item.getLifecycle(), r.getLifecycle()) && Objects.equals(item.getReplacementModel(), replacement)) {
                continue;
            }
            // 只改这两列：InquiryItemDO 的所属任务、选定价格按「总是更新」配置，用半空对象 updateById 会把它们写成 NULL
            itemMapper.update(null, new LambdaUpdateWrapper<InquiryItemDO>()
                    .eq(InquiryItemDO::getId, item.getId())
                    .set(InquiryItemDO::getLifecycle, r.getLifecycle())
                    .set(InquiryItemDO::getReplacementModel, replacement)
                    .set(InquiryItemDO::getUpdateTime, LocalDateTime.now()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public MyTaskItemVO saveActualModel(Long taskId, Long itemId, String actualModel) {
        SourcingTaskDO task = myTask(taskId);
        progress.lock(task.getCustomerInquiryId());
        CustomerInquiryDO inquiry = inquiryMapper.selectById(task.getCustomerInquiryId());
        if (inquiry != null && inquiry.getStatus() == InquiryConstants.STATUS_CANCELLED) {
            throw new BizException("客户询盘已取消，不能修改");
        }
        if (taskItems(taskId).stream().noneMatch(i -> i.getId().equals(itemId))) {
            throw new BizException("型号不在这个询价任务里");
        }
        ItemQuotesRequest r = new ItemQuotesRequest();
        r.setItemId(itemId);
        r.setActualModel(actualModel == null ? "" : actualModel);
        productArchiver.sync(updateActualModels(task, List.of(r)));
        InquiryItemDO item = itemMapper.selectById(itemId);
        MyTaskItemVO vo = new MyTaskItemVO();
        vo.setId(item.getId());
        vo.setModel(item.getConfirmedModel());
        vo.setActualModel(item.getActualModel());
        vo.setArchiveStatus(item.getArchiveStatus());
        vo.setArchiveStatusName(com.zhul.erp.modules.product.candidate.constants.CandidateConstants.ARCHIVE_NAMES.get(item.getArchiveStatus()));
        vo.setProductId(item.getProductId());
        return vo;
    }

    /** 采购回填的真实型号写回型号明细，返回有变化的型号（随后重新建档） */
    private List<Long> updateActualModels(SourcingTaskDO task, List<ItemQuotesRequest> reqItems) {
        Map<Long, InquiryItemDO> items = taskItems(task.getId()).stream().collect(Collectors.toMap(InquiryItemDO::getId, i -> i));
        List<Long> changed = new ArrayList<>();
        for (ItemQuotesRequest r : reqItems) {
            InquiryItemDO item = items.get(r.getItemId());
            if (r.getActualModel() == null || item == null) {
                continue;
            }
            String actual = text(r.getActualModel(), 128);
            if (Objects.equals(item.getActualModel(), actual)) {
                continue;
            }
            itemMapper.update(null, new LambdaUpdateWrapper<InquiryItemDO>()
                    .eq(InquiryItemDO::getId, item.getId())
                    .set(InquiryItemDO::getActualModel, actual)
                    .set(InquiryItemDO::getActualModelKey, actual.isEmpty() ? "" : PriceKeys.model(actual))
                    .set(InquiryItemDO::getUpdateTime, LocalDateTime.now()));
            changed.add(item.getId());
        }
        return changed;
    }

    /** 调用方须已在同一事务内 {@code progress.lock} 客户询盘 */
    @Override
    public void afterSubmit(SourcingTaskDO task, Long quotedBy, List<Long> itemIds) {
        // 本人在这些型号上的草稿随提交一并转为已提交
        quoteMapper.update(new SourcingQuoteDO(), new LambdaUpdateWrapper<SourcingQuoteDO>()
                .eq(SourcingQuoteDO::getTaskId, task.getId())
                .eq(SourcingQuoteDO::getQuotedBy, quotedBy)
                .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_DRAFT)
                .in(SourcingQuoteDO::getInquiryItemId, itemIds)
                .isNull(SourcingQuoteDO::getDeletedAt)
                .set(SourcingQuoteDO::getStatus, submittedStatus(needsReview(quotedBy)))
                .set(SourcingQuoteDO::getReviewNote, "")
                .set(SourcingQuoteDO::getQuotedAt, LocalDateTime.now()));
        progress.refreshItems(itemIds);
        // 有货回价提升商品候选的可信度
        productArchiver.sync(itemIds);
        progress.refreshTask(task.getId());
        progress.refreshInquiry(task.getCustomerInquiryId());
        assigneeMapper.update(new SourcingTaskAssigneeDO(), new LambdaUpdateWrapper<SourcingTaskAssigneeDO>()
                .eq(SourcingTaskAssigneeDO::getTaskId, task.getId())
                .eq(SourcingTaskAssigneeDO::getAssigneeId, quotedBy)
                .eq(SourcingTaskAssigneeDO::getActive, 1)
                .set(SourcingTaskAssigneeDO::getSubmittedAt, LocalDateTime.now()));
    }

    /**
     * 退回：只有自己在询价时，任务回到待分配池；多人比价时只退出自己，任务仍由其他人继续。
     * 原因为「型号存疑」时给客户询盘打上待核实标记。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void returnTask(Long taskId, ReturnTaskRequest req) {
        Long me = me();
        SourcingTaskDO task = myTask(taskId);
        if (task.getStatus() != InquiryConstants.TASK_SOURCING) {
            throw new BizException("只有询价中的任务可以退回");
        }
        if (!InquiryConstants.RETURN_REASONS.containsKey(req.getReason())) {
            throw new BizException("请选择退回原因");
        }
        long others = assigneeMapper.selectCount(new LambdaQueryWrapper<SourcingTaskAssigneeDO>()
                .eq(SourcingTaskAssigneeDO::getTaskId, taskId)
                .eq(SourcingTaskAssigneeDO::getActive, 1)
                .ne(SourcingTaskAssigneeDO::getAssigneeId, me));
        assigneeMapper.update(new SourcingTaskAssigneeDO(), new LambdaUpdateWrapper<SourcingTaskAssigneeDO>()
                .eq(SourcingTaskAssigneeDO::getTaskId, taskId)
                .eq(SourcingTaskAssigneeDO::getAssigneeId, me)
                .eq(SourcingTaskAssigneeDO::getActive, 1)
                .set(SourcingTaskAssigneeDO::getActive, 0));
        task.setReturnReason(req.getReason());
        task.setReturnNote(text(req.getNote(), 300));
        task.setReturnedBy(me);
        task.setReturnedAt(LocalDateTime.now());
        if (others == 0) {
            task.setStatus(InquiryConstants.TASK_UNASSIGNED);
        }
        taskMapper.updateById(task);
        if (req.getReason() == InquiryConstants.RETURN_MODEL_DOUBT) {
            CustomerInquiryDO inquiry = new CustomerInquiryDO();
            inquiry.setId(task.getCustomerInquiryId());
            inquiry.setNeedsReview(1);
            inquiryMapper.updateById(inquiry);
        }
    }

    @Override
    public SourcingTaskDO myTask(Long taskId) {
        Long me = me();
        SourcingTaskDO task = taskId == null ? null : taskMapper.selectById(taskId);
        boolean mine = task != null && task.getDeletedAt() == null && Objects.equals(task.getTenantId(), tenantId())
                && activeOf(me).stream().anyMatch(a -> a.getTaskId().equals(taskId));
        if (!mine || task.getStatus() == InquiryConstants.TASK_CANCELLED) {
            throw new BizException("询价任务不存在");
        }
        return task;
    }

    private List<SourcingTaskAssigneeDO> activeOf(Long me) {
        return assigneeMapper.selectList(new LambdaQueryWrapper<SourcingTaskAssigneeDO>()
                .eq(SourcingTaskAssigneeDO::getTenantId, tenantId())
                .eq(SourcingTaskAssigneeDO::getAssigneeId, me)
                .eq(SourcingTaskAssigneeDO::getActive, 1)
                .isNull(SourcingTaskAssigneeDO::getDeletedAt));
    }

    List<InquiryItemDO> taskItems(Long taskId) {
        return itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                .eq(InquiryItemDO::getSourcingTaskId, taskId)
                .isNull(InquiryItemDO::getDeletedAt)
                .orderByAsc(InquiryItemDO::getLineNo));
    }

    /** 兼职采购的回价须经采购负责人审核 */
    private boolean needsReview(Long quotedBy) {
        return lookups.isPartTime(quotedBy);
    }

    private static int submittedStatus(boolean review) {
        return review ? InquiryConstants.QUOTE_PENDING_REVIEW : InquiryConstants.QUOTE_SUBMITTED;
    }

    private Long me() {
        Long me = currentUser.resolve();
        if (me == null) {
            throw new BizException("当前账号没有对应的用户");
        }
        return me;
    }

    private static String text(String s, int max) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }


    private static void applyBrief(MyTaskVO vo, InquiryLookups.InquiryBrief b) {
        if (b == null) {
            return;
        }
        vo.setSalesId(b.salesId());
        vo.setSalesName(b.salesName());
        vo.setLevel(b.level());
        vo.setCustomerType(b.customerType());
        vo.setCustomerName(b.customerName());
        vo.setQuoteDeadline(b.quoteDeadline());
        vo.setEditable(b.inquiryStatus() == null || b.inquiryStatus() != InquiryConstants.STATUS_CANCELLED);
    }
}
