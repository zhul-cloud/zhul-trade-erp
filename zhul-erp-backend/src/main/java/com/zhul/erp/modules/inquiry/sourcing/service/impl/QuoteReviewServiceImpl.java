package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceRecordVO;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.dto.BoardTaskVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.ReviewApproveRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.ReviewDetailVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.ReviewItemVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.ReviewRejectRequest;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskMapper;
import com.zhul.erp.modules.inquiry.sourcing.service.PriceHistoryService;
import com.zhul.erp.modules.inquiry.sourcing.service.QuoteReviewService;
import com.zhul.erp.modules.inquiry.sourcing.service.SourcingTaskService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuoteReviewServiceImpl implements QuoteReviewService {

    private static final String MENU = "分配工作台";

    private final SourcingTaskMapper taskMapper;
    private final SourcingQuoteMapper quoteMapper;
    private final InquiryItemMapper itemMapper;
    private final CustomerInquiryMapper inquiryMapper;
    private final SourcingTaskService taskService;
    private final PriceHistoryService priceHistoryService;
    private final SourcingProgress progress;
    private final InquiryLookups lookups;
    private final CurrentUserResolver currentUser;
    private final LogService logService;

    @Override
    public ReviewDetailVO detail(Long taskId) {
        SourcingTaskDO task = task(taskId);
        List<InquiryItemDO> items = itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                .eq(InquiryItemDO::getSourcingTaskId, taskId)
                .isNull(InquiryItemDO::getDeletedAt)
                .orderByAsc(InquiryItemDO::getLineNo));
        Map<Long, InquiryItemDO> itemById = items.stream().collect(Collectors.toMap(InquiryItemDO::getId, i -> i));
        List<SourcingQuoteDO> pending = quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                .eq(SourcingQuoteDO::getTaskId, taskId)
                .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_PENDING_REVIEW)
                .isNull(SourcingQuoteDO::getDeletedAt)
                .orderByAsc(SourcingQuoteDO::getId));
        Map<Long, String> names = lookups.userNames(pending.stream().map(SourcingQuoteDO::getQuotedBy).distinct().toList());
        // 型号顺序沿用任务明细的行号，同一型号多位兼职各自一组
        Map<String, List<SourcingQuoteDO>> groups = pending.stream()
                .sorted(Comparator.comparing((SourcingQuoteDO q) -> lineNo(itemById.get(q.getInquiryItemId())))
                        .thenComparing(SourcingQuoteDO::getQuotedBy).thenComparing(SourcingQuoteDO::getId))
                .collect(Collectors.groupingBy(q -> q.getInquiryItemId() + "|" + q.getQuotedBy(), LinkedHashMap::new, Collectors.toList()));
        Map<Long, PriceRecordVO> lowest = new HashMap<>(groups.size() * 2);
        List<ReviewItemVO> rows = new ArrayList<>(groups.size());
        for (List<SourcingQuoteDO> g : groups.values()) {
            SourcingQuoteDO first = g.get(0);
            InquiryItemDO item = itemById.get(first.getInquiryItemId());
            if (item == null) {
                continue;
            }
            ReviewItemVO vo = new ReviewItemVO();
            vo.setItemId(item.getId());
            vo.setModel(item.getConfirmedModel());
            vo.setOriginalModel(item.getOriginalModel());
            vo.setQuantity(item.getQuantity());
            vo.setUnit(item.getUnit());
            vo.setDescription(item.getDescription());
            vo.setQuotedBy(first.getQuotedBy());
            vo.setQuotedByName(names.get(first.getQuotedBy()));
            vo.setSubmittedAt(g.stream().map(SourcingQuoteDO::getQuotedAt).filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null));
            vo.setQuotes(priceHistoryService.toVos(g));
            vo.setHistoryLowest(lowest.computeIfAbsent(item.getId(), id -> historyLowest(item)));
            rows.add(vo);
        }
        Set<Long> inReview = pending.stream().map(SourcingQuoteDO::getInquiryItemId).collect(Collectors.toSet());
        ReviewDetailVO vo = new ReviewDetailVO();
        BoardTaskVO header = taskService.taskDetail(taskId).getTask();
        LocalDateTime first = pending.stream().map(SourcingQuoteDO::getQuotedAt).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
        header.setReviewItemCount(inReview.size());
        header.setReviewSubmittedAt(first);
        header.setReviewWaitingMinutes(first == null ? null : Math.max(0, Duration.between(first, LocalDateTime.now()).toMinutes()));
        header.setReviewBuyerNames(List.copyOf(new java.util.LinkedHashSet<>(names.values())));
        vo.setTask(header);
        vo.setItems(rows);
        vo.setUnsubmittedCount((int) items.stream()
                .filter(i -> Objects.equals(i.getQuoteStatus(), InquiryConstants.ITEM_PENDING) && !inReview.contains(i.getId()))
                .count());
        return vo;
    }

    /** 同品牌同型号历史询价中最低的一条有价记录（待审核的不在历史询价里） */
    private PriceRecordVO historyLowest(InquiryItemDO item) {
        return priceHistoryService.match(item.getBrand(), item.getConfirmedModel()).getSameBrand().stream()
                .filter(r -> r.getUnitPriceCny() != null)
                .min(Comparator.comparing(PriceRecordVO::getUnitPriceCny))
                .orElse(null);
    }

    /**
     * 审核通过：未作废的记录转为已提交、写入审核人选的推荐；该兼职此型号上次通过的版本此时才软删除。
     * 与提交回价同一事务边界：先锁客户询盘，再改回价、重算型号 / 任务 / 客户询盘的回价进度。
     */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void approve(Long taskId, ReviewApproveRequest req) {
        SourcingTaskDO task = lockedTask(taskId);
        Long me = currentUser.resolve();
        LocalDateTime now = LocalDateTime.now();
        String operator = operatorName(me);
        List<Long> itemIds = new ArrayList<>(req.getItems().size());
        for (ReviewApproveRequest.Item r : req.getItems()) {
            InquiryItemDO item = item(task, r.getItemId());
            List<SourcingQuoteDO> pending = pending(taskId, r.getItemId(), r.getQuotedBy(), item);
            Map<Long, String> voids = new HashMap<>();
            for (ReviewApproveRequest.VoidQuote v : r.getVoids() == null ? List.<ReviewApproveRequest.VoidQuote>of() : r.getVoids()) {
                if (pending.stream().noneMatch(q -> q.getId().equals(v.getQuoteId()))) {
                    throw new BizException(item.getConfirmedModel() + " 要作废的记录不在待审核中，请刷新后再试");
                }
                voids.put(v.getQuoteId(), v.getReason() == null ? "" : v.getReason().trim());
            }
            List<SourcingQuoteDO> kept = pending.stream().filter(q -> !voids.containsKey(q.getId())).toList();
            if (kept.isEmpty()) {
                throw new BizException(item.getConfirmedModel() + " 的记录都作废了，不能通过，请改为退回");
            }
            List<SourcingQuoteDO> priced = kept.stream().filter(q -> !Objects.equals(q.getNoStock(), 1)).toList();
            Long recommended = r.getRecommendedQuoteId();
            if (!priced.isEmpty() && (recommended == null || priced.stream().noneMatch(q -> q.getId().equals(recommended)))) {
                throw new BizException("请为 " + item.getConfirmedModel() + " 选一条推荐报价");
            }
            List<SourcingQuoteDO> previous = quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                    .eq(SourcingQuoteDO::getTaskId, taskId)
                    .eq(SourcingQuoteDO::getInquiryItemId, r.getItemId())
                    .eq(SourcingQuoteDO::getQuotedBy, r.getQuotedBy())
                    .in(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED, InquiryConstants.QUOTE_VOIDED)
                    .isNull(SourcingQuoteDO::getDeletedAt));
            if (!previous.isEmpty()) {
                quoteMapper.update(null, new LambdaUpdateWrapper<SourcingQuoteDO>()
                        .in(SourcingQuoteDO::getId, previous.stream().map(SourcingQuoteDO::getId).toList())
                        .set(SourcingQuoteDO::getDeletedAt, now)
                        .set(SourcingQuoteDO::getUpdateTime, now));
            }
            for (Map.Entry<Long, String> v : voids.entrySet()) {
                quoteMapper.update(null, reviewed(v.getKey(), me, now)
                        .set(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_VOIDED)
                        .set(SourcingQuoteDO::getReviewNote, v.getValue().length() > 200 ? v.getValue().substring(0, 200) : v.getValue())
                        .set(SourcingQuoteDO::getRecommended, 0));
            }
            for (SourcingQuoteDO q : kept) {
                // 价格、货况、货期等内容一律不动，只改状态与推荐
                quoteMapper.update(null, reviewed(q.getId(), me, now)
                        .set(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED)
                        .set(SourcingQuoteDO::getReviewNote, "")
                        .set(SourcingQuoteDO::getRecommended, q.getId().equals(recommended) ? 1 : 0));
            }
            SourcingQuoteDO pick = priced.stream().filter(q -> q.getId().equals(recommended)).findFirst().orElse(null);
            String note = (pick == null ? "无货" : "推荐 ¥" + pick.getUnitPriceCny())
                    + (voids.isEmpty() ? "" : "，作废 " + voids.size() + " 条")
                    + "（" + operatorName(r.getQuotedBy()) + " 的回价）";
            logService.recordOperateLog(MENU, InquiryConstants.LOG_REVIEW_APPROVE,
                    Map.of("itemId", item.getId(), "model", item.getConfirmedModel(), "quoteIds", pending.stream().map(SourcingQuoteDO::getId).toList()),
                    Map.of("itemId", item.getId(), "model", item.getConfirmedModel(), "recommendedQuoteId", recommended == null ? 0L : recommended,
                            "voidedQuoteIds", List.copyOf(voids.keySet()), "note", note, "operator", operator));
            itemIds.add(item.getId());
        }
        progress.refreshItems(itemIds);
        progress.refreshTask(taskId);
        progress.refreshInquiry(task.getCustomerInquiryId());
    }

    /** 退回：待审核的记录回到兼职的草稿并带上退回原因；上次通过的版本不动，继续对业务员有效 */
    @Override
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void reject(Long taskId, ReviewRejectRequest req) {
        String reason = req.getReason() == null ? "" : req.getReason().trim();
        if (reason.isEmpty()) {
            throw new BizException("请填写退回原因");
        }
        SourcingTaskDO task = lockedTask(taskId);
        Long me = currentUser.resolve();
        LocalDateTime now = LocalDateTime.now();
        String operator = operatorName(me);
        for (ReviewRejectRequest.Item r : req.getItems()) {
            InquiryItemDO item = item(task, r.getItemId());
            List<SourcingQuoteDO> pending = pending(taskId, r.getItemId(), r.getQuotedBy(), item);
            quoteMapper.update(null, new LambdaUpdateWrapper<SourcingQuoteDO>()
                    .in(SourcingQuoteDO::getId, pending.stream().map(SourcingQuoteDO::getId).toList())
                    .set(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_DRAFT)
                    .set(SourcingQuoteDO::getReviewNote, reason)
                    .set(SourcingQuoteDO::getReviewedBy, me)
                    .set(SourcingQuoteDO::getReviewedAt, now)
                    .set(SourcingQuoteDO::getUpdateTime, now));
            logService.recordOperateLog(MENU, InquiryConstants.LOG_REVIEW_REJECT,
                    Map.of("itemId", item.getId(), "model", item.getConfirmedModel(), "quoteIds", pending.stream().map(SourcingQuoteDO::getId).toList()),
                    Map.of("itemId", item.getId(), "model", item.getConfirmedModel(),
                            "note", "退回给 " + operatorName(r.getQuotedBy()) + "：" + reason, "operator", operator));
        }
    }

    private SourcingTaskDO task(Long taskId) {
        SourcingTaskDO t = taskId == null ? null : taskMapper.selectById(taskId);
        if (t == null || t.getDeletedAt() != null || !Objects.equals(t.getTenantId(), tenantId())
                || t.getStatus() == InquiryConstants.TASK_CANCELLED) {
            throw new BizException("询价任务不存在");
        }
        return t;
    }

    /** 锁客户询盘后再校验：报价后、取消后回价只读，不能再审核 */
    private SourcingTaskDO lockedTask(Long taskId) {
        SourcingTaskDO task = task(taskId);
        progress.lock(task.getCustomerInquiryId());
        task = task(taskId);
        CustomerInquiryDO inquiry = inquiryMapper.selectById(task.getCustomerInquiryId());
        if (inquiry != null && InquiryConstants.QUOTE_LOCKED_STATUSES.contains(inquiry.getStatus())) {
            throw new BizException(inquiry.getStatus() == InquiryConstants.STATUS_CANCELLED
                    ? "客户询盘已取消，不能再审核" : "业务员已经报价，不能再审核");
        }
        return task;
    }

    private InquiryItemDO item(SourcingTaskDO task, Long itemId) {
        InquiryItemDO item = itemMapper.selectById(itemId);
        if (item == null || item.getDeletedAt() != null || !Objects.equals(item.getSourcingTaskId(), task.getId())) {
            throw new BizException("型号不属于这个询价任务，请刷新后再试");
        }
        return item;
    }

    /** 某位兼职在某型号上待审核的记录；已被别人处理过时提示刷新，避免重复审核 */
    private List<SourcingQuoteDO> pending(Long taskId, Long itemId, Long quotedBy, InquiryItemDO item) {
        List<SourcingQuoteDO> pending = quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                .eq(SourcingQuoteDO::getTaskId, taskId)
                .eq(SourcingQuoteDO::getInquiryItemId, itemId)
                .eq(SourcingQuoteDO::getQuotedBy, quotedBy)
                .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_PENDING_REVIEW)
                .isNull(SourcingQuoteDO::getDeletedAt));
        if (pending.isEmpty()) {
            throw new BizException(item.getConfirmedModel() + " 已经审核过或被修改了，请刷新后再试");
        }
        return pending;
    }

    private static LambdaUpdateWrapper<SourcingQuoteDO> reviewed(Long quoteId, Long me, LocalDateTime now) {
        return new LambdaUpdateWrapper<SourcingQuoteDO>()
                .eq(SourcingQuoteDO::getId, quoteId)
                .set(SourcingQuoteDO::getReviewedBy, me)
                .set(SourcingQuoteDO::getReviewedAt, now)
                .set(SourcingQuoteDO::getUpdateTime, now);
    }

    private String operatorName(Long userId) {
        return userId == null ? "" : lookups.userNames(List.of(userId)).getOrDefault(userId, "");
    }

    private static int lineNo(InquiryItemDO item) {
        return item == null || item.getLineNo() == null ? Integer.MAX_VALUE : item.getLineNo();
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}
