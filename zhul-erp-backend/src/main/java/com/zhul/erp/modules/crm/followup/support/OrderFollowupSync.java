package com.zhul.erp.modules.crm.followup.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.aftersales.repository.AfterSalesMapper;
import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.entity.CustomerFollowupDO;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.entity.SopStageDO;
import com.zhul.erp.modules.crm.followup.repository.CustomerFollowupMapper;
import com.zhul.erp.modules.crm.followup.repository.FollowupItemMapper;
import com.zhul.erp.modules.crm.followup.repository.SopStageMapper;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.entity.LogisticsShipmentDO;
import com.zhul.erp.modules.logistics.entity.OutboundOrderDO;
import com.zhul.erp.modules.logistics.entity.OutboundOrderItemDO;
import com.zhul.erp.modules.logistics.repository.LogisticsShipmentMapper;
import com.zhul.erp.modules.logistics.repository.OutboundOrderItemMapper;
import com.zhul.erp.modules.logistics.repository.OutboundOrderMapper;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseRequirementMapper;
import com.zhul.erp.modules.purchase.support.OrderPurchaseProgress;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.OrderProgress;
import com.zhul.erp.modules.warehouse.entity.ReceivingDiscrepancyDO;
import com.zhul.erp.modules.warehouse.entity.ShootTaskDO;
import com.zhul.erp.modules.warehouse.repository.ReceivingDiscrepancyMapper;
import com.zhul.erp.modules.warehouse.repository.ShootTaskMapper;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单履约（board 3）、售后评价（board 4）跟进对象的同步（design D1–D7）：按客户把进行中的销售订单整体重新推导，
 * 一张订单同一时刻最多一个未结束的跟进对象——先在订单履约，到预计送达日或客户已收货后转入售后评价。
 * 由 {@link FollowupSync#resync} 调用，事务边界跟随调用方，本类不吞异常。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderFollowupSync {

    private static final int OB_WITHDRAWN = LogisticsConstants.OB_WITHDRAWN;

    private final FollowupItemMapper itemMapper;
    private final CustomerFollowupMapper recordMapper;
    private final SopStageMapper sopStageMapper;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final PurchaseRequirementMapper requirementMapper;
    private final PurchaseOrderMapper poMapper;
    private final PurchaseOrderItemMapper poItemMapper;
    private final SupplierMapper supplierMapper;
    private final ReceivingDiscrepancyMapper discrepancyMapper;
    private final ShootTaskMapper shootTaskMapper;
    private final OutboundOrderMapper outboundMapper;
    private final OutboundOrderItemMapper outboundItemMapper;
    private final LogisticsShipmentMapper shipmentMapper;
    private final AfterSalesMapper afterSalesMapper;
    private final OrderProgress orderProgress;
    private final FollowupSettings settings;
    private final ObjectMapper objectMapper;

    /** @return 本次新建、转入、重新打开或移出的对象数 */
    public int resync(Long customerId, LocalDate today) {
        Integer tenantId = TenantContext.getTenantId();
        FollowupSettings.OrderParams p = settings.orderParams(tenantId);
        Map<String, FollowupItemDO> rows = new HashMap<>(16);
        for (FollowupItemDO r : itemMapper.selectList(new LambdaQueryWrapper<FollowupItemDO>()
                .eq(FollowupItemDO::getCustomerId, customerId)
                .in(FollowupItemDO::getBoard, FollowupConstants.BOARD_FULFIL, FollowupConstants.BOARD_AFTER_SALES)
                .eq(FollowupItemDO::getTargetType, FollowupConstants.TARGET_ORDER)
                .isNull(FollowupItemDO::getDeletedAt))) {
            rows.put(r.getBoard() + ":" + r.getTargetId(), r);
        }
        List<SalesOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getCustomerId, customerId)
                .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                .and(w -> w.isNull(SalesOrderDO::getMigrated).or().eq(SalesOrderDO::getMigrated, 0))
                .and(w -> w.isNull(SalesOrderDO::getAfterSalesId).or().eq(SalesOrderDO::getAfterSalesId, 0))
                // 费用订单没有型号，不进履约看板（否则会被当成「待采购」）
                .ne(SalesOrderDO::getSource, SalesConstants.SO_FEE)
                .isNull(SalesOrderDO::getDeletedAt));
        Map<Long, OrderFacts> facts = load(orders);
        Map<String, Map<String, String>> actions = defaultActions();
        Set<Long> live = new HashSet<>();
        int changes = 0;
        for (SalesOrderDO o : orders) {
            OrderFacts f = facts.get(o.getId());
            live.add(o.getId());
            changes += syncOrder(tenantId, f, rows.get(FollowupConstants.BOARD_FULFIL + ":" + o.getId()),
                    rows.get(FollowupConstants.BOARD_AFTER_SALES + ":" + o.getId()), p, actions, today);
        }
        for (FollowupItemDO r : rows.values()) {
            if (r.getClosedAt() == null && !live.contains(r.getTargetId())) {
                close(r, FollowupConstants.CLOSE_DOC_GONE);
                changes++;
            }
        }
        return changes;
    }

    private int syncOrder(Integer tenantId, OrderFacts f, FollowupItemDO fulfil, FollowupItemDO after,
                          FollowupSettings.OrderParams p, Map<String, Map<String, String>> actions, LocalDate today) {
        boolean openCase = AfterSalesDeriver.hasOpenCase(f);
        if (after != null) {
            if (after.getClosedAt() != null) {
                // 售后结束后又登记了售后单：重新打开，阶段按售后单推导
                boolean newCase = f.afterSales().stream().anyMatch(a -> (a.status() == 1 || a.status() == 2)
                        && a.createdAt() != null && a.createdAt().isAfter(after.getClosedAt()));
                if (!newCase) {
                    return 0;
                }
                reopenAfterSales(after);
                updateAfterSales(f, after, p, actions, today);
                return 1;
            }
            return updateAfterSales(f, after, p, actions, today) ? 1 : 0;
        }
        if (fulfil != null && fulfil.getClosedAt() == null) {
            FulfilDeriver.Result r = FulfilDeriver.derive(f, hasUserRecord(fulfil.getId()), p, today);
            if (r == null) {
                close(fulfil, FollowupConstants.CLOSE_DOC_DONE);
                return 1;
            }
            applyFulfil(f, fulfil, r, actions, today);
            if (f.completed() || (fulfil.getEtaDate() != null && !fulfil.getEtaDate().isAfter(today))) {
                close(fulfil, FollowupConstants.CLOSE_DOC_DONE);
                FollowupItemDO a = newRow(tenantId, f, FollowupConstants.BOARD_AFTER_SALES, today);
                a.setEtaDate(fulfil.getEtaDate());
                a.setEtaFirst(fulfil.getEtaFirst());
                a.setEtaSource(fulfil.getEtaSource());
                a.setEtaNote(fulfil.getEtaNote());
                a.setLastFollowupId(fulfil.getLastFollowupId());
                a.setLastFollowupAt(fulfil.getLastFollowupAt());
                insertAfterSales(f, a, p, actions, today);
                writeSystemRecord(f, a, f.completed() ? "订单 " + f.soNo() + " 客户已收货，转入售后评价"
                        : "订单 " + f.soNo() + " 到预计送达日，转入售后评价");
                return 1;
            }
            itemMapper.updateById(fulfil);
            return 0;
        }
        if (fulfil != null) {
            return 0;
        }
        // 还没有跟进对象：上线前已完成的不生成（之后登记售后单的除外）；预计送达已过的直接进 A1，过了 30 天的不生成
        if (f.completed()) {
            if (!openCase) {
                return 0;
            }
            FollowupItemDO a = newRow(tenantId, f, FollowupConstants.BOARD_AFTER_SALES, today);
            insertAfterSales(f, a, p, actions, today);
            return 1;
        }
        FulfilDeriver.Result r = FulfilDeriver.derive(f, false, p, today);
        if (r == null) {
            return 0;
        }
        if (r.eta() != null && !r.eta().date().isAfter(today)) {
            if (r.eta().date().isBefore(today.minusDays(FollowupConstants.BOOTSTRAP_A1_DAYS)) && !openCase) {
                return 0;
            }
            FollowupItemDO a = newRow(tenantId, f, FollowupConstants.BOARD_AFTER_SALES, today);
            a.setEtaDate(r.eta().date());
            a.setEtaFirst(r.eta().date());
            a.setEtaSource(FollowupConstants.ETA_ESTIMATED);
            a.setEtaNote(r.eta().note());
            insertAfterSales(f, a, p, actions, today);
            return 1;
        }
        FollowupItemDO row = newRow(tenantId, f, FollowupConstants.BOARD_FULFIL, today);
        applyFulfil(f, row, r, actions, today);
        itemMapper.insert(row);
        return 1;
    }

    // ---------------------------------------------------------------- 订单履约

    private void applyFulfil(OrderFacts f, FollowupItemDO row, FulfilDeriver.Result r, Map<String, Map<String, String>> actions,
                             LocalDate today) {
        snapshot(row, f);
        setStage(row, r.stage(), r.group());
        row.setSituationText(cut(r.situation(), 200));
        row.setPartCount(r.partCount());
        if (r.exceptionFlags() == 0) {
            row.setExceptionFlags(0);
            row.setExceptionSince(null);
        } else {
            if (row.getExceptionSince() == null || nz(row.getExceptionFlags()) == 0) {
                row.setExceptionSince(today);
            }
            row.setExceptionFlags(r.exceptionFlags());
        }
        if (r.eta() == null) {
            row.setEtaDate(null);
            row.setEtaSource(FollowupConstants.ETA_NONE);
            row.setEtaNote("");
        } else {
            if (row.getEtaFirst() == null) {
                row.setEtaFirst(r.eta().date());
            }
            if (nz(row.getEtaSource()) != FollowupConstants.ETA_USER) {
                row.setEtaDate(r.eta().date());
                row.setEtaSource(FollowupConstants.ETA_ESTIMATED);
                row.setEtaNote(r.eta().note());
            }
        }
        FulfilDeriver.Node node = FulfilDeriver.node(f, row.getExceptionSince());
        boolean shippedNow = FollowupConstants.NODE_SHIPPED.equals(node.code())
                && !(FollowupConstants.NODE_SHIPPED.equals(row.getNodeCode()) && node.date().equals(row.getNodeDate()));
        applyNode(row, node, fulfilText(actions, row.getStageCode(), node.code()), today);
        if (shippedNow && row.getId() != null) {
            writeSystemRecord(f, row, "订单 " + f.soNo() + " 已出运 · " + row.getSituationText());
        }
    }

    private static String fulfilText(Map<String, Map<String, String>> actions, String stage, String node) {
        String t = actions.getOrDefault(stage, Map.of()).get(node);
        if (t != null) {
            return t;
        }
        for (String s : FollowupConstants.FULFIL_STAGES) {
            t = actions.getOrDefault(s, Map.of()).get(node);
            if (t != null) {
                return t;
            }
        }
        return "联系客户，告诉他订单最新进度";
    }

    /**
     * 节点替换下一步（design D4）：节点到了（不晚于今天）且是新节点时，
     * 节点晚于上次跟进、或当前下一步本来就是系统生成的，换成节点的默认动作；否则保留业务员定的下一步。
     *
     * @return 是否替换了下一步
     */
    public static boolean applyNode(FollowupItemDO row, FulfilDeriver.Node node, String text, LocalDate today) {
        if (node == null || node.date() == null || node.date().isAfter(today)) {
            return false;
        }
        if (node.code().equals(row.getNodeCode()) && node.date().equals(row.getNodeDate())) {
            return false;
        }
        row.setNodeCode(node.code());
        row.setNodeDate(node.date());
        LocalDate last = row.getLastFollowupAt() == null ? null : row.getLastFollowupAt().toLocalDate();
        boolean system = !Objects.equals(row.getNextActionSource(), FollowupConstants.NEXT_USER);
        if (system || last == null || node.date().isAfter(last)) {
            row.setNextAction(cut(text, FollowupConstants.NEXT_ACTION_MAX));
            row.setNextActionDate(node.date());
            row.setNextActionSource(FollowupConstants.NEXT_SYSTEM);
            row.setSnoozeCount(0);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- 售后评价

    private void insertAfterSales(OrderFacts f, FollowupItemDO a, FollowupSettings.OrderParams p,
                                  Map<String, Map<String, String>> actions, LocalDate today) {
        deriveAfterSales(f, a, p, actions, today);
        itemMapper.insert(a);
    }

    /** @return 是否结束 */
    private boolean updateAfterSales(OrderFacts f, FollowupItemDO a, FollowupSettings.OrderParams p,
                                     Map<String, Map<String, String>> actions, LocalDate today) {
        boolean ended = deriveAfterSales(f, a, p, actions, today);
        itemMapper.updateById(a);
        if (ended) {
            writeSystemRecord(f, a, "订单 " + f.soNo() + " 售后结束 · " + endText(a));
        }
        return ended;
    }

    private boolean deriveAfterSales(OrderFacts f, FollowupItemDO a, FollowupSettings.OrderParams p,
                                     Map<String, Map<String, String>> actions, LocalDate today) {
        snapshot(a, f);
        AfterSalesDeriver.State s = state(a);
        AfterSalesDeriver.Result r = AfterSalesDeriver.derive(f, s, p, today);
        String before = a.getStageCode();
        setStage(a, r.stage(), r.group());
        a.setSignedDate(r.signedDate());
        a.setSituationText(cut(r.situation(), 200));
        a.setAfterSalesId(r.doc() == null ? 0L : r.doc().id());
        if (r.end()) {
            if (r.missed()) {
                a.setReviewStatus(FollowupConstants.REVIEW_MISSED);
                a.setSituationText("提醒 " + p.reviewTimes() + " 次没评价");
            }
            a.setClosedAt(LocalDateTime.now());
            a.setCloseReason(FollowupConstants.CLOSE_AFTER_SALES);
            a.setNextActionDate(null);
            return true;
        }
        if (r.doc() != null) {
            String next = r.doc().nextAction() == null || r.doc().nextAction().isBlank()
                    ? "处理售后单 " + r.doc().asNo() : r.doc().nextAction().trim();
            a.setNextAction(cut(next, FollowupConstants.NEXT_ACTION_MAX));
            a.setNextActionDate(r.doc().nextUpdateAt() == null ? null : r.doc().nextUpdateAt().toLocalDate());
            a.setNextActionSource(FollowupConstants.NEXT_AFTER_SALES);
            a.setNodeCode(r.stage());
            a.setNodeDate(null);
            return false;
        }
        if (Objects.equals(a.getNextActionSource(), FollowupConstants.NEXT_AFTER_SALES) || !r.stage().equals(before)) {
            // 离开 A3 / A4，或换了阶段：让新阶段的节点重新生效
            if (Objects.equals(a.getNextActionSource(), FollowupConstants.NEXT_AFTER_SALES)) {
                a.setNextActionSource(FollowupConstants.NEXT_SYSTEM);
            }
            a.setNodeCode("");
            a.setNodeDate(null);
        }
        LocalDate since = a.getStageSince() == null ? today : a.getStageSince().toLocalDate();
        FulfilDeriver.Node node = AfterSalesDeriver.node(r, s, since, p, today);
        String text = actions.getOrDefault(r.stage(), Map.of()).getOrDefault("default", "联系客户");
        if (applyNode(a, node, text, today) && node != null) {
            int k = AfterSalesDeriver.remindCountOf(node.code());
            if (k > 0) {
                a.setReviewRemindCount(k);
            }
        }
        return false;
    }

    private static AfterSalesDeriver.State state(FollowupItemDO a) {
        return new AfterSalesDeriver.State(a.getSignedDate(), nz(a.getUsedOk()) == 1, nz(a.getSatisfaction()),
                nz(a.getReviewStatus()), a.getReviewPlatform(), a.getReviewInvitedAt(), a.getEtaDate());
    }

    private static void reopenAfterSales(FollowupItemDO a) {
        a.setClosedAt(null);
        a.setCloseReason(FollowupConstants.CLOSE_NONE);
        a.setUsedOk(0);
        a.setSatisfaction(0);
        a.setDissatisfiedReason("");
        a.setReviewStatus(FollowupConstants.REVIEW_NONE);
        a.setReviewInvitedAt(null);
        a.setReviewRemindCount(0);
    }

    private static String endText(FollowupItemDO a) {
        StringBuilder sb = new StringBuilder();
        if (nz(a.getSatisfaction()) > 0) {
            sb.append("满意度：").append(FollowupConstants.SATISFACTION_NAMES.get(a.getSatisfaction()));
            String reason = FollowupConstants.DISSATISFIED_REASONS.get(a.getDissatisfiedReason());
            if (reason != null) {
                sb.append(" · 原因：").append(reason);
            }
        }
        String review = FollowupConstants.REVIEW_NAMES.get(nz(a.getReviewStatus()));
        if (review != null) {
            sb.append(sb.isEmpty() ? "" : " · ").append("评价：").append(review);
        }
        return sb.isEmpty() ? "售后完成" : sb.toString();
    }

    // ---------------------------------------------------------------- 公共

    private static void setStage(FollowupItemDO row, String stage, int group) {
        if (!stage.equals(row.getStageCode()) || row.getStageSince() == null) {
            row.setStageSince(LocalDateTime.now());
        }
        row.setStageCode(stage);
        row.setStageGroup(group);
    }

    private static void snapshot(FollowupItemDO row, OrderFacts f) {
        row.setDocNo(f.soNo());
        if (row.getOwnerId() == null || row.getOwnerId() == 0L) {
            row.setOwnerId(f.ownerId() == null ? 0L : f.ownerId());
        }
        row.setCurrencyCode(f.currencyCode() == null ? "" : f.currencyCode());
        row.setAmount(nzb(f.totalAmount()));
        row.setReceivedAmount(BigDecimal.ZERO);
        row.setAmountCny(nzb(f.totalAmountCny()));
        row.setSentAt(f.createdAt());
    }

    private FollowupItemDO newRow(Integer tenantId, OrderFacts f, int board, LocalDate today) {
        FollowupItemDO row = new FollowupItemDO();
        row.setTenantId(tenantId);
        row.setBoard(board);
        row.setTargetType(FollowupConstants.TARGET_ORDER);
        row.setTargetId(f.soId());
        row.setCustomerId(f.customerId());
        row.setSourceKey("");
        row.setOpportunityId(0L);
        row.setCadenceFrom(board == FollowupConstants.BOARD_FULFIL ? f.createdAt().toLocalDate() : today);
        row.setBlockerCode("");
        row.setNextAction("");
        row.setNextActionSource(FollowupConstants.NEXT_SYSTEM);
        row.setSituationText("");
        row.setPartCount(0);
        row.setNodeCode("");
        row.setExceptionFlags(0);
        row.setEtaSource(FollowupConstants.ETA_NONE);
        row.setEtaNote("");
        row.setUsedOk(0);
        row.setSatisfaction(0);
        row.setDissatisfiedReason("");
        row.setReviewStatus(FollowupConstants.REVIEW_NONE);
        row.setReviewPlatform("");
        row.setReviewRemindCount(0);
        row.setAfterSalesId(0L);
        row.setSnoozeCount(0);
        row.setLastFollowupId(0L);
        row.setNoReplyStreak(0);
        row.setFeedbackSeen(0);
        row.setNurtureReason("");
        row.setNurtureNote("");
        row.setCloseReason(FollowupConstants.CLOSE_NONE);
        return row;
    }

    private void close(FollowupItemDO row, int reason) {
        row.setClosedAt(LocalDateTime.now());
        row.setCloseReason(reason);
        row.setNextActionDate(null);
        itemMapper.updateById(row);
    }

    private boolean hasUserRecord(Long itemId) {
        return recordMapper.selectCount(new LambdaQueryWrapper<CustomerFollowupDO>()
                .eq(CustomerFollowupDO::getFollowupItemId, itemId)
                .eq(CustomerFollowupDO::getRecordType, FollowupConstants.RECORD_USER)
                .isNull(CustomerFollowupDO::getDeletedAt)) > 0;
    }

    private void writeSystemRecord(OrderFacts f, FollowupItemDO item, String title) {
        CustomerFollowupDO r = new CustomerFollowupDO();
        r.setTenantId(TenantContext.getTenantId());
        r.setRecordType(FollowupConstants.RECORD_SYSTEM);
        r.setCustomerId(f.customerId());
        r.setFollowupItemId(item.getId() == null ? 0L : item.getId());
        r.setTargetType(FollowupConstants.TARGET_ORDER);
        r.setTargetId(f.soId());
        r.setOpportunityId(0L);
        r.setTitle(cut(title, 200));
        r.setSituation(0);
        r.setBlockerCode("");
        r.setNextAction("");
        r.setChannel(0);
        r.setAiSummary("");
        r.setAiTaskId(0L);
        r.setStageBefore(item.getStageCode() == null ? "" : item.getStageCode());
        r.setStageAfter(item.getStageCode() == null ? "" : item.getStageCode());
        r.setOperatorId(0L);
        recordMapper.insert(r);
    }

    /** SOP 默认动作（订单履约、售后评价）：阶段 → 节点键 → 文字；租户自己的覆盖平台的 */
    /** 订单履约、售后评价各阶段的默认动作：阶段编码 → 场景 → 动作 */
    public Map<String, Map<String, String>> defaultActions() {
        List<SopStageDO> rows = sopStageMapper.selectList(new LambdaQueryWrapper<SopStageDO>()
                .in(SopStageDO::getBoard, FollowupConstants.BOARD_FULFIL, FollowupConstants.BOARD_AFTER_SALES,
                        FollowupConstants.BOARD_ACCOUNT, FollowupConstants.BOARD_DEVELOP)
                .eq(SopStageDO::getStatus, 1));
        rows.sort(Comparator.comparing(SopStageDO::getTenantId));
        Map<String, Map<String, String>> out = new HashMap<>(16);
        for (SopStageDO s : rows) {
            if (s.getDefaultActions() == null || s.getDefaultActions().isBlank()) {
                continue;
            }
            try {
                out.put(s.getStageCode(), objectMapper.readValue(s.getDefaultActions(), new TypeReference<Map<String, String>>() { }));
            } catch (JsonProcessingException e) {
                log.warn("SOP 默认动作格式不正确，已忽略：{}", e.getOriginalMessage());
            }
        }
        return out;
    }

    // ---------------------------------------------------------------- 装载单据事实

    /** 一次装好这些订单的型号、采购、差异、实拍、出库、出运与售后 */
    /** 一张订单的单据事实；订单不存在时为 null */
    public OrderFacts facts(Long soId) {
        SalesOrderDO o = soId == null ? null : orderMapper.selectById(soId);
        return o == null ? null : load(List.of(o)).get(soId);
    }

    public Map<Long, OrderFacts> load(Collection<SalesOrderDO> orders) {
        Map<Long, OrderFacts> out = new HashMap<>(orders.size() * 2 + 1);
        if (orders.isEmpty()) {
            return out;
        }
        List<Long> soIds = orders.stream().map(SalesOrderDO::getId).toList();
        Map<String, String> autoOf = autoSteps();
        Map<Long, List<SalesOrderItemDO>> items = orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                        .in(SalesOrderItemDO::getSoId, soIds).isNull(SalesOrderItemDO::getDeletedAt)).stream()
                .collect(Collectors.groupingBy(SalesOrderItemDO::getSoId));
        Set<Long> requirementItems = requirementMapper.selectList(new LambdaQueryWrapper<PurchaseRequirementDO>()
                        .in(PurchaseRequirementDO::getSoId, soIds)
                        .eq(PurchaseRequirementDO::getStatus, PurchaseConstants.REQ_ACTIVE)
                        .isNull(PurchaseRequirementDO::getDeletedAt)).stream()
                .map(PurchaseRequirementDO::getSoItemId).collect(Collectors.toSet());
        List<PurchaseOrderItemDO> poItems = poItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .in(PurchaseOrderItemDO::getSoId, soIds).isNull(PurchaseOrderItemDO::getDeletedAt));
        Map<Long, PurchaseOrderDO> pos = poItems.isEmpty() ? Map.of()
                : poMapper.selectList(new LambdaQueryWrapper<PurchaseOrderDO>()
                        .in(PurchaseOrderDO::getId, poItems.stream().map(PurchaseOrderItemDO::getPoId).distinct().toList())
                        .eq(PurchaseOrderDO::getStatus, PurchaseConstants.PO_ORDERED)
                        .isNull(PurchaseOrderDO::getDeletedAt)).stream()
                .collect(Collectors.toMap(PurchaseOrderDO::getId, x -> x));
        Map<Long, String> suppliers = supplierNames(pos.values());
        Set<Long> pendingPoItems = new HashSet<>();
        List<Long> livePoItemIds = poItems.stream().filter(i -> pos.containsKey(i.getPoId())).map(PurchaseOrderItemDO::getId).toList();
        if (!livePoItemIds.isEmpty()) {
            discrepancyMapper.selectList(new LambdaQueryWrapper<ReceivingDiscrepancyDO>()
                            .in(ReceivingDiscrepancyDO::getPoItemId, livePoItemIds)
                            .eq(ReceivingDiscrepancyDO::getStatus, 1)
                            .isNull(ReceivingDiscrepancyDO::getDeletedAt))
                    .forEach(d -> pendingPoItems.add(d.getPoItemId()));
        }
        Map<Long, LocalDate> shot = new HashMap<>(8);
        shootTaskMapper.selectList(new LambdaQueryWrapper<ShootTaskDO>()
                        .in(ShootTaskDO::getSoId, soIds).eq(ShootTaskDO::getStatus, 2)
                        .isNotNull(ShootTaskDO::getCompletedAt).isNull(ShootTaskDO::getDeletedAt))
                .forEach(t -> shot.merge(t.getSoId(), t.getCompletedAt().toLocalDate(), (a, b) -> a.isAfter(b) ? a : b));
        List<OutboundOrderDO> obs = outboundMapper.selectList(new LambdaQueryWrapper<OutboundOrderDO>()
                .in(OutboundOrderDO::getSoId, soIds).isNull(OutboundOrderDO::getDeletedAt));
        Map<Long, List<Long>> obItems = obs.isEmpty() ? Map.of()
                : outboundItemMapper.selectList(new LambdaQueryWrapper<OutboundOrderItemDO>()
                        .in(OutboundOrderItemDO::getOutboundId, obs.stream().map(OutboundOrderDO::getId).toList())
                        .isNull(OutboundOrderItemDO::getDeletedAt)).stream()
                .collect(Collectors.groupingBy(OutboundOrderItemDO::getOutboundId,
                        Collectors.mapping(OutboundOrderItemDO::getSoItemId, Collectors.toList())));
        List<Long> shipmentIds = obs.stream().filter(o -> o.getStatus() != OB_WITHDRAWN).map(OutboundOrderDO::getLogisticsId)
                .filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        Map<Long, LogisticsShipmentDO> shipments = shipmentIds.isEmpty() ? Map.of()
                : shipmentMapper.selectList(new LambdaQueryWrapper<LogisticsShipmentDO>()
                        .in(LogisticsShipmentDO::getId, shipmentIds)
                        .eq(LogisticsShipmentDO::getStatus, LogisticsConstants.SH_SHIPPED)
                        .isNull(LogisticsShipmentDO::getDeletedAt)).stream()
                .collect(Collectors.toMap(LogisticsShipmentDO::getId, x -> x));
        Map<Long, List<AfterSalesDO>> cases = afterSalesMapper.selectList(new LambdaQueryWrapper<AfterSalesDO>()
                        .in(AfterSalesDO::getSoId, soIds).isNull(AfterSalesDO::getDeletedAt)).stream()
                .collect(Collectors.groupingBy(AfterSalesDO::getSoId));

        for (SalesOrderDO o : orders) {
            List<OrderFacts.Item> its = items.getOrDefault(o.getId(), List.of()).stream()
                    .map(i -> new OrderFacts.Item(i.getId(), i.getModel(), nz(i.getQuantity()),
                            autoOf.getOrDefault(i.getProgressCode(), SalesConstants.PROGRESS_PENDING),
                            requirementItems.contains(i.getId())))
                    .toList();
            List<OrderFacts.PoLine> lines = new ArrayList<>(8);
            boolean discrepancy = false;
            for (PurchaseOrderItemDO pi : poItems) {
                PurchaseOrderDO po = pos.get(pi.getPoId());
                if (po == null || !Objects.equals(pi.getSoId(), o.getId())) {
                    continue;
                }
                lines.add(new OrderFacts.PoLine(po.getId(), po.getPoNo(), supplierOf(po, suppliers), pi.getSoItemId(),
                        po.getOrderedAt() != null ? po.getOrderedAt().toLocalDate() : po.getOrderDate(), po.getExpectedShipDate()));
                discrepancy |= pendingPoItems.contains(pi.getId());
            }
            List<OrderFacts.Outbound> outs = obs.stream().filter(x -> Objects.equals(x.getSoId(), o.getId()))
                    .map(x -> new OrderFacts.Outbound(x.getId(), nz(x.getStatus()), obItems.getOrDefault(x.getId(), List.of()),
                            x.getLogisticsId()))
                    .toList();
            List<OrderFacts.Shipment> shs = outs.stream().filter(x -> x.status() != OB_WITHDRAWN)
                    .map(OrderFacts.Outbound::logisticsId).filter(Objects::nonNull).distinct()
                    .map(shipments::get).filter(Objects::nonNull)
                    .map(s -> new OrderFacts.Shipment(s.getId(), s.getShNo(), s.getCarrier(), s.getWaybillNo(), s.getShippedDate()))
                    .toList();
            List<OrderFacts.AfterSales> as = cases.getOrDefault(o.getId(), List.of()).stream()
                    .map(a -> new OrderFacts.AfterSales(a.getId(), a.getAsNo(), nz(a.getStatus()), a.getNextAction(),
                            a.getNextUpdateAt(), a.getCreateTime(), a.getClosedAt()))
                    .toList();
            boolean completed = SalesConstants.PROGRESS_COMPLETED.equals(o.getProgressCode());
            out.put(o.getId(), new OrderFacts(o.getId(), o.getSoNo(), o.getCustomerId(), o.getOwnerId(), o.getCurrencyCode(),
                    o.getTotalAmount(), o.getTotalAmountCny(), o.getCreateTime() == null ? LocalDateTime.now() : o.getCreateTime(),
                    completed, completed && o.getCompletedAt() != null ? o.getCompletedAt().toLocalDate() : null,
                    its, lines, discrepancy, shot.get(o.getId()), outs, shs, as));
        }
        return out;
    }

    /** 型号进度码 → 自动步骤：手动步骤归入它前面最近的自动步骤；已完成算已出运 */
    private Map<String, String> autoSteps() {
        List<OrderProgress.Step> ordered = orderProgress.ordered();
        Map<String, String> out = new HashMap<>(ordered.size() * 2 + 2);
        String last = SalesConstants.PROGRESS_PENDING;
        for (OrderProgress.Step s : ordered) {
            if (OrderPurchaseProgress.AUTO.contains(s.code())) {
                last = s.code();
            }
            out.put(s.code(), last);
        }
        out.put(SalesConstants.PROGRESS_COMPLETED, SalesConstants.PROGRESS_SHIPPED);
        return out;
    }

    private Map<Long, String> supplierNames(Collection<PurchaseOrderDO> pos) {
        List<Long> ids = pos.stream().map(PurchaseOrderDO::getSupplierId).filter(Objects::nonNull).filter(id -> id > 0)
                .distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return supplierMapper.selectList(new LambdaQueryWrapper<SupplierDO>().in(SupplierDO::getId, ids)).stream()
                .collect(Collectors.toMap(SupplierDO::getId,
                        s -> s.getShortName() != null && !s.getShortName().isBlank() ? s.getShortName() : s.getName(), (a, b) -> a));
    }

    private static String supplierOf(PurchaseOrderDO po, Map<Long, String> suppliers) {
        String name = po.getSupplierId() == null ? null : suppliers.get(po.getSupplierId());
        if (name != null) {
            return name;
        }
        String channel = PurchaseConstants.CHANNEL_NAMES.getOrDefault(po.getChannel(), "");
        String shop = po.getShopName() == null ? "" : po.getShopName().trim();
        return (channel + " " + shop).trim();
    }

    private static int nz(Integer v) {
        return v == null ? 0 : v;
    }

    private static BigDecimal nzb(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
