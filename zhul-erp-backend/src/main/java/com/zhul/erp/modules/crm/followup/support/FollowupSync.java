package com.zhul.erp.modules.crm.followup.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.crm.entity.OpportunityDO;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.entity.CustomerFollowupDO;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.entity.SopBlockerDO;
import com.zhul.erp.modules.crm.followup.entity.SopStageDO;
import com.zhul.erp.modules.crm.followup.repository.CustomerFollowupMapper;
import com.zhul.erp.modules.crm.followup.repository.FollowupItemMapper;
import com.zhul.erp.modules.crm.followup.repository.SopBlockerMapper;
import com.zhul.erp.modules.crm.followup.repository.SopStageMapper;
import com.zhul.erp.modules.crm.constants.OpportunityConstants;
import com.zhul.erp.modules.crm.repository.OpportunityMapper;
import com.zhul.erp.modules.crm.support.OpportunityStageRules;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import com.zhul.erp.modules.quotation.repository.QuotationItemMapper;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PiItemDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.repository.PiItemMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 跟进对象同步（design D1–D5）：按客户把商机、询盘、报价单、PI 整体重新推导一遍，和现有跟进对象比对，
 * 补上缺的、移出已结束的、更新单据快照与阶段，再按系统节奏刷新下一步。单据事件和每晚核对共用这一套规则。
 * <p>
 * 事务边界：本类不声明事务，调用方在哪个事务里就在哪个事务里执行。
 * 单据事件走 {@link #customerChanged} / {@link #event}：吞掉跟进侧的运行时异常只记 WARN，不影响报价、PI、收款本身，
 * 漏掉的由每晚核对补上；记一次跟进与核对任务直接调用 {@link #resync}，出错即抛出。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FollowupSync {

    private static final Set<Integer> QUOTE_OPEN = Set.of(QuotationConstants.STATUS_SENT, QuotationConstants.STATUS_PARTIAL);
    private static final Set<Integer> PI_SENT = Set.of(SalesConstants.PI_SENT, SalesConstants.PI_CONVERTED,
            SalesConstants.PI_CLOSED);
    private static final Set<Integer> PI_OPEN = Set.of(SalesConstants.PI_SENT, SalesConstants.PI_CONVERTED);
    private static final Set<Integer> PI_SETTLED = Set.of(SalesConstants.RECEIPT_PAID, SalesConstants.RECEIPT_FREE);

    private final FollowupItemMapper itemMapper;
    private final CustomerFollowupMapper recordMapper;
    private final SopStageMapper sopStageMapper;
    private final SopBlockerMapper sopBlockerMapper;
    private final OpportunityMapper opportunityMapper;
    private final OpportunityStageRules stageRules;
    private final CustomerInquiryMapper inquiryMapper;
    private final QuotationMapper quotationMapper;
    private final QuotationItemMapper quotationItemMapper;
    private final ProformaInvoiceMapper piMapper;
    private final PiItemMapper piItemMapper;
    private final FollowupSettings settings;
    private final ObjectMapper objectMapper;
    private final OrderFollowupSync orderSync;

    // ---------------------------------------------------------------- 单据事件入口（不影响原单据）

    /** 客户的单据有变化：重新推导该客户的跟进对象；出错只记 WARN */
    public void customerChanged(Long customerId) {
        if (customerId == null) {
            return;
        }
        try {
            resync(customerId, LocalDate.now(), false);
        } catch (RuntimeException e) {
            // 跟进是单据的附带结果：这里出错不能让报价、PI、收款回滚，由每晚核对兜底
            log.warn("跟进对象同步失败，customerId={}", customerId, e);
        }
    }

    /** 单据事件：写一条系统事件到客户的跟进时间线，再重新推导；出错只记 WARN */
    public void event(Long customerId, int targetType, Long targetId, String title) {
        if (customerId == null) {
            return;
        }
        try {
            resync(customerId, LocalDate.now(), false);
            writeSystemRecord(customerId, targetType, targetId, title);
        } catch (RuntimeException e) {
            log.warn("跟进对象同步失败，customerId={}, event={}", customerId, title, e);
        }
    }

    /** 写一条系统事件（创建询盘、发送报价、发送 PI、收款到账、自动赢单） */
    public void writeSystemRecord(Long customerId, int targetType, Long targetId, String title) {
        writeSystemRecord(customerId, targetType, targetId, title, null);
    }

    /** @param content 附带的正文（如售后结案的案例沉淀），在时间线节点下显示；null 表示没有 */
    public void writeSystemRecord(Long customerId, int targetType, Long targetId, String title, String content) {
        // 同一张销售订单在订单履约、售后评价各有一行：取进行中的，都结束了取最新的
        FollowupItemDO item = targetId == null ? null : itemMapper.selectList(new LambdaQueryWrapper<FollowupItemDO>()
                        .eq(FollowupItemDO::getTargetType, targetType)
                        .eq(FollowupItemDO::getTargetId, targetId)
                        .isNull(FollowupItemDO::getDeletedAt)).stream()
                .max(Comparator.comparing((FollowupItemDO r) -> r.getClosedAt() == null).thenComparing(FollowupItemDO::getId))
                .orElse(null);
        CustomerFollowupDO r = new CustomerFollowupDO();
        r.setTenantId(TenantContext.getTenantId());
        r.setRecordType(FollowupConstants.RECORD_SYSTEM);
        r.setCustomerId(customerId);
        r.setFollowupItemId(item == null ? 0L : item.getId());
        r.setTargetType(targetType);
        r.setTargetId(targetId == null ? 0L : targetId);
        r.setOpportunityId(item == null ? 0L : item.getOpportunityId());
        r.setTitle(title.length() > 200 ? title.substring(0, 200) : title);
        r.setSituation(0);
        r.setBlockerCode("");
        r.setNextAction("");
        r.setChannel(0);
        r.setAiSummary("");
        r.setAiTaskId(0L);
        String stage = item == null ? "" : item.getStageCode();
        r.setStageBefore(stage);
        r.setStageAfter(stage);
        r.setOperatorId(0L);
        if (content != null && !content.isBlank()) {
            r.setRawContent(content);
        }
        recordMapper.insert(r);
    }

    /** 客户转给新负责人：该客户未结束的跟进对象随之变更（spec「跟进可见范围与权限」）；与转移在同一事务内 */
    public void ownerChanged(Long customerId, Long ownerId) {
        if (customerId == null || ownerId == null) {
            return;
        }
        for (FollowupItemDO row : itemMapper.selectList(new LambdaQueryWrapper<FollowupItemDO>()
                .eq(FollowupItemDO::getCustomerId, customerId)
                .isNull(FollowupItemDO::getClosedAt)
                .isNull(FollowupItemDO::getDeletedAt))) {
            if (!Objects.equals(row.getOwnerId(), ownerId)) {
                row.setOwnerId(ownerId);
                itemMapper.updateById(row);
            }
        }
    }

    /**
     * 商机结束（输单、无效）后调用，与结束在同一事务内，出错即抛出：
     * 先重新推导（商机本身的跟进对象移出），再按选择处理该客户商机成交板块仍在跟进的对象：
     * 转培育的全部转为培育中（原因取输单原因对应的卡点，到期日默认按系统参数）；不转培育的全部移出，之后不会自动回到板块。
     * 只有商机本身一个跟进对象时，转培育保留它，到期后提醒重新联系。
     */
    public void onOpportunityClosed(Long customerId, boolean nurture, int lostReason, LocalDate until, String note) {
        LocalDate today = LocalDate.now();
        Integer tenantId = TenantContext.getTenantId();
        List<FollowupItemDO> open = itemMapper.selectList(new LambdaQueryWrapper<FollowupItemDO>()
                .eq(FollowupItemDO::getCustomerId, customerId)
                .eq(FollowupItemDO::getBoard, FollowupConstants.BOARD_SALES)
                .isNull(FollowupItemDO::getClosedAt)
                .isNull(FollowupItemDO::getDeletedAt));
        LocalDate due = until != null && until.isAfter(today) ? until
                : today.plusDays(settings.params(tenantId).nurtureDefaultDays());
        String reason = blockerOfLostReason(lostReason);
        for (FollowupItemDO row : open) {
            if (nurture) {
                row.setNurtureUntil(due);
                row.setNurtureReason(reason);
                row.setNurtureNote(note == null ? "" : note.length() > 200 ? note.substring(0, 200) : note);
                row.setNextActionDate(null);
            } else {
                row.setClosedAt(LocalDateTime.now());
                row.setCloseReason(FollowupConstants.CLOSE_OPPORTUNITY);
                row.setNextActionDate(null);
            }
            itemMapper.updateById(row);
        }
        resync(customerId, today, false);
    }

    /** 输单原因码 → SOP 卡点编码；没有对应卡点（无货源、客户取消、其他）时为空 */
    private String blockerOfLostReason(int lostReason) {
        if (lostReason <= 0) {
            return "";
        }
        return sopBlockerMapper.selectList(new LambdaQueryWrapper<SopBlockerDO>()
                        .eq(SopBlockerDO::getLostReason, lostReason)
                        .eq(SopBlockerDO::getStatus, 1)).stream()
                .max(Comparator.comparing(SopBlockerDO::getTenantId))
                .map(SopBlockerDO::getCode).orElse("");
    }

    // ---------------------------------------------------------------- 推导

    /** 推导出的一个跟进对象 */
    private record Target(int type, Long id, String docNo, String sourceKey, Long ownerId, String stage, int group,
                          String currency, BigDecimal amount, BigDecimal received, BigDecimal amountCny,
                          LocalDateTime sentAt, LocalDate cadenceFrom, Set<Long> quotationIds, Set<Long> inquiryIds) {

        String key() {
            return type + ":" + id;
        }
    }

    /**
     * 重新推导一个客户的跟进对象。
     *
     * @param bootstrap 核对任务补生成时为 true：新建对象的提醒节奏从今天起算（上线时存量单据从上线当天起算）
     * @return 本次新建、重新打开或移出的对象数（核对任务记日志用）
     */
    public int resync(Long customerId, LocalDate today, boolean bootstrap) {
        Integer tenantId = TenantContext.getTenantId();
        OpportunityStageRules.Stages stages = stageRules.load();
        OpportunityDO opp = activeOpportunity(customerId, stages);
        List<Target> desired = derive(customerId, opp, stages);
        Map<String, FollowupItemDO> existing = new LinkedHashMap<>(16);
        for (FollowupItemDO row : itemMapper.selectList(new LambdaQueryWrapper<FollowupItemDO>()
                .eq(FollowupItemDO::getCustomerId, customerId)
                .eq(FollowupItemDO::getBoard, FollowupConstants.BOARD_SALES)
                .isNull(FollowupItemDO::getDeletedAt))) {
            existing.put(row.getTargetType() + ":" + row.getTargetId(), row);
        }
        Set<String> desiredKeys = desired.stream().map(Target::key).collect(Collectors.toSet());
        FollowupSettings.Params params = settings.params(tenantId);
        Map<String, Map<String, String>> actions = defaultActions();
        int changes = 0;
        Set<Long> replaced = new TreeSet<>();
        for (Target t : desired) {
            FollowupItemDO row = existing.get(t.key());
            boolean fresh = row == null;
            if (fresh) {
                row = newItem(tenantId, customerId, t, bootstrap ? today : t.cadenceFrom());
                FollowupItemDO pred = predecessor(t, existing.values(), desiredKeys);
                if (pred != null) {
                    inherit(row, pred);
                    replaced.add(pred.getId());
                }
                changes++;
            } else if (row.getClosedAt() != null) {
                if (Objects.equals(row.getCloseReason(), FollowupConstants.CLOSE_OPPORTUNITY)
                        && t.type() != FollowupConstants.TARGET_OPPORTUNITY) {
                    // 输单时选择不转培育而移出的，不再自动回到板块
                    continue;
                }
                row.setClosedAt(null);
                row.setCloseReason(FollowupConstants.CLOSE_NONE);
                changes++;
            }
            snapshot(row, t, opp);
            String stage = stageOf(t, row, opp, stages);
            if (!stage.equals(row.getStageCode()) || row.getStageSince() == null) {
                row.setStageSince(LocalDateTime.now());
            }
            row.setStageCode(stage);
            refreshCadence(row, today, params, actions);
            if (fresh) {
                itemMapper.insert(row);
            } else {
                itemMapper.updateById(row);
            }
        }
        for (FollowupItemDO row : existing.values()) {
            if (row.getClosedAt() == null && keepNurturingLost(row)) {
                refreshCadence(row, today, params, actions);
                itemMapper.updateById(row);
                continue;
            }
            if (row.getClosedAt() == null && !desiredKeys.contains(row.getTargetType() + ":" + row.getTargetId())) {
                row.setClosedAt(LocalDateTime.now());
                row.setCloseReason(replaced.contains(row.getId()) ? FollowupConstants.CLOSE_REPLACED
                        : row.getTargetType() == FollowupConstants.TARGET_OPPORTUNITY && opp == null
                        ? FollowupConstants.CLOSE_OPPORTUNITY : FollowupConstants.CLOSE_DOC_DONE);
                row.setNextActionDate(null);
                itemMapper.updateById(row);
                changes++;
            }
        }
        // 订单履约、售后评价（第二期）
        changes += orderSync.resync(customerId, today);
        return changes;
    }

    /** 输单后转培育的商机：商机本身的跟进对象保留在板块（培育中或已到期待联系），直到商机重新打开、改无效或删除 */
    private boolean keepNurturingLost(FollowupItemDO row) {
        if (row.getTargetType() != FollowupConstants.TARGET_OPPORTUNITY) {
            return false;
        }
        OpportunityDO o = opportunityMapper.selectById(row.getTargetId());
        return o != null && o.getDeletedAt() == null && OpportunityConstants.STAGE_LOST.equals(o.getStageCode());
    }

    private OpportunityDO activeOpportunity(Long customerId, OpportunityStageRules.Stages stages) {
        return opportunityMapper.selectList(new LambdaQueryWrapper<OpportunityDO>()
                        .eq(OpportunityDO::getCustomerId, customerId)
                        .isNull(OpportunityDO::getDeletedAt)).stream()
                .filter(o -> stages.isActive(o.getStageCode()))
                .max(Comparator.comparing(OpportunityDO::getId)).orElse(null);
    }

    private List<Target> derive(Long customerId, OpportunityDO opp, OpportunityStageRules.Stages stages) {
        List<Target> out = new ArrayList<>(8);
        // PI：已发送（含已转订单、已关闭，用来判断接替）；未收齐、没关闭作废的才是跟进对象
        List<ProformaInvoiceDO> pis = piMapper.selectList(new LambdaQueryWrapper<ProformaInvoiceDO>()
                .eq(ProformaInvoiceDO::getCustomerId, customerId)
                .in(ProformaInvoiceDO::getStatus, PI_SENT)
                .isNull(ProformaInvoiceDO::getDeletedAt));
        Map<Long, Set<Long>> piQuotations = piQuotations(pis);
        Set<Long> supersededQuotes = piQuotations.values().stream().flatMap(Set::stream).collect(Collectors.toSet());
        for (ProformaInvoiceDO pi : pis) {
            if (!PI_OPEN.contains(pi.getStatus()) || PI_SETTLED.contains(pi.getReceiptStatus())) {
                continue;
            }
            out.add(new Target(FollowupConstants.TARGET_PI, pi.getId(), pi.getPiNo(), "", pi.getOwnerId(), FollowupConstants.S7,
                    FollowupConstants.GROUP_AFTER_PI, pi.getCurrencyCode(), nz(pi.getTotalAmount()), nz(pi.getReceivedAmount()),
                    nz(pi.getTotalAmountCny()), pi.getSentAt(), dateOf(pi.getSentAt()),
                    piQuotations.getOrDefault(pi.getId(), Set.of()), Set.of()));
        }
        // 报价单：已发送 / 部分成交、没被 PI 接替；同一来源询盘只留最新发送的一张
        List<QuotationDO> quotes = quotationMapper.selectList(new LambdaQueryWrapper<QuotationDO>()
                .eq(QuotationDO::getCustomerId, customerId)
                .in(QuotationDO::getStatus, QUOTE_OPEN)
                .isNotNull(QuotationDO::getSentAt)
                .isNull(QuotationDO::getDeletedAt));
        Map<Long, Set<Long>> quoteInquiries = quoteInquiries(quotes);
        Map<String, QuotationDO> latest = new LinkedHashMap<>(8);
        for (QuotationDO q : quotes) {
            if (supersededQuotes.contains(q.getId())) {
                continue;
            }
            String key = sourceKey(quoteInquiries.get(q.getId()), q.getId());
            QuotationDO cur = latest.get(key);
            if (cur == null || q.getSentAt().isAfter(cur.getSentAt())
                    || (q.getSentAt().isEqual(cur.getSentAt()) && q.getId() > cur.getId())) {
                latest.put(key, q);
            }
        }
        for (Map.Entry<String, QuotationDO> e : latest.entrySet()) {
            QuotationDO q = e.getValue();
            Set<Long> inquiries = quoteInquiries.getOrDefault(q.getId(), Set.of());
            out.add(new Target(FollowupConstants.TARGET_QUOTATION, q.getId(), q.getQuotationNo(),
                    inquiries.isEmpty() ? "" : e.getKey(), q.getOwnerId(), FollowupConstants.S4, FollowupConstants.GROUP_AFTER_QUOTE,
                    q.getCurrencyCode(), nz(q.getTotalAmount()), BigDecimal.ZERO, nz(q.getTotalAmountCny()), q.getSentAt(),
                    dateOf(q.getSentAt()), Set.of(), inquiries));
        }
        if (opp != null) {
            // 新客户：报价前只看商机本身（S1–S3）
            OpportunityStageRulesView view = new OpportunityStageRulesView(stages);
            if (view.before(opp.getStageCode(), FollowupConstants.S4)) {
                out.add(new Target(FollowupConstants.TARGET_OPPORTUNITY, opp.getId(), opp.getOpportunityCode(), "",
                        opp.getOwnerId(), opp.getStageCode(), FollowupConstants.GROUP_BEFORE_QUOTE, "", BigDecimal.ZERO,
                        BigDecimal.ZERO, BigDecimal.ZERO, null,
                        opp.getFirstContactDate() != null ? opp.getFirstContactDate() : dateOf(opp.getCreateTime()),
                        Set.of(), Set.of()));
            }
        } else {
            // 老客户：已登记、还没生成报价单、不是从商机创建的询盘
            for (CustomerInquiryDO inq : inquiryMapper.selectList(new LambdaQueryWrapper<CustomerInquiryDO>()
                    .eq(CustomerInquiryDO::getCustomerId, customerId)
                    .lt(CustomerInquiryDO::getStatus, InquiryConstants.STATUS_QUOTED)
                    // 从商机创建的询盘属于那条商机（新客户），不单独成为跟进对象
                    .and(w -> w.isNull(CustomerInquiryDO::getOpportunityId).or().eq(CustomerInquiryDO::getOpportunityId, 0))
                    .isNull(CustomerInquiryDO::getDeletedAt))) {
                out.add(new Target(FollowupConstants.TARGET_INQUIRY, inq.getId(), inq.getInquiryCode(), "", inq.getOwnerId(),
                        FollowupConstants.S3, FollowupConstants.GROUP_BEFORE_QUOTE, "CNY", BigDecimal.ZERO, BigDecimal.ZERO,
                        nz(itemMapper.inquiryEstimateCny(inq.getId())), null,
                        inq.getInquiryDate() != null ? inq.getInquiryDate() : dateOf(inq.getCreateTime()), Set.of(), Set.of()));
            }
        }
        return out;
    }

    /** 已发送 PI → 明细关联的报价单 */
    private Map<Long, Set<Long>> piQuotations(List<ProformaInvoiceDO> pis) {
        if (pis.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<Long>> out = new HashMap<>(pis.size() * 2);
        for (PiItemDO i : piItemMapper.selectList(new LambdaQueryWrapper<PiItemDO>()
                .in(PiItemDO::getPiId, pis.stream().map(ProformaInvoiceDO::getId).toList())
                .isNotNull(PiItemDO::getQuotationId)
                .isNull(PiItemDO::getDeletedAt))) {
            out.computeIfAbsent(i.getPiId(), k -> new TreeSet<>()).add(i.getQuotationId());
        }
        return out;
    }

    /** 报价单 → 当前版本明细关联的询盘 */
    private Map<Long, Set<Long>> quoteInquiries(List<QuotationDO> quotes) {
        if (quotes.isEmpty()) {
            return Map.of();
        }
        Map<Long, Set<Long>> out = new HashMap<>(quotes.size() * 2);
        for (QuotationItemDO i : quotationItemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                .in(QuotationItemDO::getQuotationId, quotes.stream().map(QuotationDO::getId).toList())
                .eq(QuotationItemDO::getIsCurrent, 1)
                .isNotNull(QuotationItemDO::getCustomerInquiryId)
                .isNull(QuotationItemDO::getDeletedAt))) {
            out.computeIfAbsent(i.getQuotationId(), k -> new TreeSet<>()).add(i.getCustomerInquiryId());
        }
        return out;
    }

    /** 同一来源判断：来源询盘 ID 升序逗号分隔；没有关联询盘的报价单各自独立 */
    static String sourceKey(Set<Long> inquiryIds, Long quotationId) {
        if (inquiryIds == null || inquiryIds.isEmpty()) {
            return "q" + quotationId;
        }
        return inquiryIds.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** 新建的跟进对象接替谁：PI 接替它关联的报价单，报价单接替同一来源的旧报价单、来源询盘或商机 */
    private static FollowupItemDO predecessor(Target t, Collection<FollowupItemDO> rows, Set<String> desiredKeys) {
        List<FollowupItemDO> open = rows.stream()
                .filter(r -> r.getClosedAt() == null && !desiredKeys.contains(r.getTargetType() + ":" + r.getTargetId()))
                .toList();
        List<FollowupItemDO> hits;
        if (t.type() == FollowupConstants.TARGET_PI) {
            hits = open.stream().filter(r -> r.getTargetType() == FollowupConstants.TARGET_QUOTATION
                    && t.quotationIds().contains(r.getTargetId())).toList();
        } else if (t.type() == FollowupConstants.TARGET_QUOTATION) {
            hits = open.stream().filter(r -> r.getTargetType() == FollowupConstants.TARGET_QUOTATION
                    && !t.sourceKey().isEmpty() && t.sourceKey().equals(r.getSourceKey())).toList();
            if (hits.isEmpty()) {
                hits = open.stream().filter(r -> (r.getTargetType() == FollowupConstants.TARGET_INQUIRY
                        && t.inquiryIds().contains(r.getTargetId()))
                        || r.getTargetType() == FollowupConstants.TARGET_OPPORTUNITY).toList();
            }
        } else {
            hits = List.of();
        }
        return hits.stream().max(Comparator.comparing(FollowupItemDO::getId)).orElse(null);
    }

    /** 接替时继承：卡点、业务员定的下一步、老客户已推进到的阶段、无回复计数、上次跟进（design D4、D5） */
    private static void inherit(FollowupItemDO row, FollowupItemDO pred) {
        row.setBlockerCode(pred.getBlockerCode());
        if (!Objects.equals(pred.getNextActionSource(), FollowupConstants.NEXT_SYSTEM)) {
            row.setNextAction(pred.getNextAction());
            row.setNextActionDate(pred.getNextActionDate());
            row.setNextActionSource(pred.getNextActionSource());
        }
        row.setStageCode(pred.getStageCode());
        row.setNoReplyStreak(pred.getNoReplyStreak());
        row.setFeedbackSeen(pred.getFeedbackSeen());
        row.setLastFollowupId(pred.getLastFollowupId());
        row.setLastFollowupAt(pred.getLastFollowupAt());
        row.setExpectedPayDate(pred.getExpectedPayDate());
    }

    private static FollowupItemDO newItem(Integer tenantId, Long customerId, Target t, LocalDate cadenceFrom) {
        FollowupItemDO row = new FollowupItemDO();
        row.setTenantId(tenantId);
        row.setBoard(FollowupConstants.BOARD_SALES);
        row.setTargetType(t.type());
        row.setTargetId(t.id());
        row.setCustomerId(customerId);
        row.setCadenceFrom(cadenceFrom == null ? LocalDate.now() : cadenceFrom);
        row.setStageCode(t.stage());
        row.setBlockerCode("");
        row.setNextAction("");
        row.setNextActionSource(FollowupConstants.NEXT_SYSTEM);
        row.setSnoozeCount(0);
        row.setLastFollowupId(0L);
        row.setNoReplyStreak(0);
        row.setFeedbackSeen(0);
        row.setNurtureReason("");
        row.setNurtureNote("");
        row.setCloseReason(FollowupConstants.CLOSE_NONE);
        return row;
    }

    private static void snapshot(FollowupItemDO row, Target t, OpportunityDO opp) {
        row.setDocNo(t.docNo() == null ? "" : t.docNo());
        row.setSourceKey(t.sourceKey());
        if (row.getOwnerId() == null || row.getOwnerId() == 0L) {
            // 负责人在生成时取单据负责人，之后随客户转移变更（见 ownerChanged），不再被单据覆盖
            row.setOwnerId(t.ownerId() == null ? 0L : t.ownerId());
        }
        row.setOpportunityId(opp == null ? 0L : opp.getId());
        row.setStageGroup(t.group());
        row.setCurrencyCode(t.currency() == null ? "" : t.currency());
        row.setAmount(t.amount());
        row.setReceivedAmount(t.received());
        row.setAmountCny(t.amountCny());
        row.setSentAt(t.sentAt());
    }

    /**
     * 阶段（design D2）：有进行中商机的跟商机走，但限定在本分组的阶段范围内；
     * 老客户按单据推导，报价单上记跟进推进到的 S5 / S6 保留，只进不退。
     */
    private static String stageOf(Target t, FollowupItemDO row, OpportunityDO opp, OpportunityStageRules.Stages stages) {
        OpportunityStageRulesView view = new OpportunityStageRulesView(stages);
        if (t.group() == FollowupConstants.GROUP_AFTER_PI) {
            return FollowupConstants.S7;
        }
        if (t.group() == FollowupConstants.GROUP_BEFORE_QUOTE) {
            return t.stage();
        }
        String base = opp != null ? opp.getStageCode() : row.getStageCode();
        return view.clamp(base, FollowupConstants.S4, FollowupConstants.S6);
    }

    /** 系统节奏：来源为「系统」时按节奏刷新；培育到期的重新激活并生成一条系统提醒 */
    private static void refreshCadence(FollowupItemDO row, LocalDate today, FollowupSettings.Params p,
                                       Map<String, Map<String, String>> actions) {
        if (row.getNurtureUntil() != null) {
            if (row.getNurtureUntil().isAfter(today)) {
                row.setNextActionDate(null);
                return;
            }
            row.setNurtureUntil(null);
            row.setNurtureReason("");
            row.setNurtureNote("");
            row.setNextActionSource(FollowupConstants.NEXT_SYSTEM_ONCE);
            row.setNextAction("培育到期，重新联系客户");
            row.setNextActionDate(today);
            return;
        }
        if (!Objects.equals(row.getNextActionSource(), FollowupConstants.NEXT_SYSTEM)) {
            return;
        }
        Cadence.Reminder r = Cadence.of(row.getStageGroup(), row.getStageCode(), row.getCadenceFrom(), row.getExpectedPayDate(),
                today, p, actions);
        row.setNextActionDate(r.date());
        row.setNextAction(r.text().length() > FollowupConstants.NEXT_ACTION_MAX
                ? r.text().substring(0, FollowupConstants.NEXT_ACTION_MAX) : r.text());
    }

    /** SOP 默认动作：阶段 → 节点键 → 文字；租户自己的覆盖平台的 */
    public Map<String, Map<String, String>> defaultActions() {
        List<SopStageDO> rows = sopStageMapper.selectList(new LambdaQueryWrapper<SopStageDO>()
                .eq(SopStageDO::getBoard, FollowupConstants.BOARD_SALES)
                .eq(SopStageDO::getStatus, 1));
        rows.sort(Comparator.comparing(SopStageDO::getTenantId));
        Map<String, Map<String, String>> out = new HashMap<>(16);
        for (SopStageDO s : rows) {
            out.put(s.getStageCode(), parseActions(s.getDefaultActions()));
        }
        return out;
    }

    private Map<String, String> parseActions(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (JsonProcessingException e) {
            log.warn("SOP 默认动作格式不正确，已忽略：{}", e.getOriginalMessage());
            return Map.of();
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static LocalDate dateOf(LocalDateTime t) {
        return t == null ? null : t.toLocalDate();
    }

    /** 商机阶段的先后比较（按阶段配置的排序，不写死编码） */
    private record OpportunityStageRulesView(OpportunityStageRules.Stages stages) {

        private int order(String code) {
            return stages.isActive(code) ? stages.get(code).getSortOrder() : Integer.MAX_VALUE;
        }

        boolean before(String code, String than) {
            return order(code) < order(than);
        }

        String clamp(String code, String lo, String hi) {
            if (code == null || !stages.isActive(code) || order(code) < order(lo)) {
                return lo;
            }
            return order(code) > order(hi) ? hi : code;
        }
    }
}
