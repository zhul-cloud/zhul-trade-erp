package com.zhul.erp.modules.crm.followup;

import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.support.AfterSalesDeriver;
import com.zhul.erp.modules.crm.followup.support.FollowupSettings;
import com.zhul.erp.modules.crm.followup.support.FulfilDeriver;
import com.zhul.erp.modules.crm.followup.support.OrderFacts;
import com.zhul.erp.modules.crm.followup.support.OrderFollowupSync;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 订单履约、售后评价推导（tasks 2.1–2.4、3.1–3.2）：纯函数，不读库 */
class OrderDeriveTest {

    private static final FollowupSettings.OrderParams P = FollowupSettings.OrderParams.defaults();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 4);
    private static final LocalDateTime CREATED = LocalDateTime.of(2026, 10, 25, 10, 0);

    private static OrderFacts.Item item(long id, String progress, boolean requirement) {
        return new OrderFacts.Item(id, "M" + id, 2, progress, requirement);
    }

    private static OrderFacts.PoLine po(long poId, long soItemId, LocalDate expected) {
        return new OrderFacts.PoLine(poId, "PO" + poId, "供应商" + poId, soItemId, LocalDate.of(2026, 10, 26), expected);
    }

    private static OrderFacts facts(List<OrderFacts.Item> items, List<OrderFacts.PoLine> lines, List<OrderFacts.Outbound> obs,
                                    List<OrderFacts.Shipment> shs, boolean discrepancy, LocalDate shot) {
        return new OrderFacts(1L, "SO1", 9L, 7L, "USD", new BigDecimal("100"), new BigDecimal("700"), CREATED, false, null,
                items, lines, discrepancy, shot, obs, shs, List.of());
    }

    private static OrderFacts withCases(OrderFacts f, List<OrderFacts.AfterSales> cases, boolean completed) {
        return new OrderFacts(f.soId(), f.soNo(), f.customerId(), f.ownerId(), f.currencyCode(), f.totalAmount(),
                f.totalAmountCny(), f.createdAt(), completed, completed ? TODAY.minusDays(1) : null, f.items(), f.poLines(),
                f.pendingDiscrepancy(), f.lastShotDate(), f.outbounds(), f.shipments(), cases);
    }

    private static OrderFacts.Shipment ship(long id, String carrier, LocalDate date) {
        return new OrderFacts.Shipment(id, "SH" + id, carrier, "WB" + id, date);
    }

    // ---------------------------------------------------------------- 2.1 阶段

    @Test
    void twoParts_takesSlowest() {
        OrderFacts f = facts(List.of(item(1, "RECEIVED", true), item(2, "ORDERED", true)),
                List.of(po(11, 1, TODAY.minusDays(5)), po(12, 2, TODAY.plusDays(2))), List.of(), List.of(), false, null);
        FulfilDeriver.Result r = FulfilDeriver.derive(f, false, P, TODAY);
        assertThat(r.stage()).isEqualTo("F3");
        assertThat(r.group()).isEqualTo(FollowupConstants.GROUP_PURCHASING);
        assertThat(r.partCount()).isEqualTo(2);
        List<FulfilDeriver.Part> parts = FulfilDeriver.parts(f, TODAY);
        assertThat(parts).extracting(FulfilDeriver.Part::stage).containsExactly("F4", "F3");
        assertThat(parts.get(0).label()).isEqualTo("第 1 部分");
    }

    @Test
    void stockItems_skipPurchase() {
        OrderFacts f = facts(List.of(item(1, "PENDING_PURCHASE", false)), List.of(), List.of(), List.of(), false, null);
        FulfilDeriver.Result r = FulfilDeriver.derive(f, true, P, TODAY);
        assertThat(r.stage()).isEqualTo("F4");
        assertThat(FulfilDeriver.parts(f, TODAY).get(0).supplier()).isEqualTo("库存直发");
    }

    @Test
    void stockOnActiveOutbound_isPacking() {
        OrderFacts f = facts(List.of(item(1, "PENDING_PURCHASE", false)), List.of(),
                List.of(new OrderFacts.Outbound(5L, 1, List.of(1L), null)), List.of(), false, null);
        assertThat(FulfilDeriver.derive(f, true, P, TODAY).stage()).isEqualTo("F5");
    }

    @Test
    void noPurchaseYet_isF1UntilFirstRecord() {
        OrderFacts f = facts(List.of(item(1, "PENDING_PURCHASE", true)), List.of(), List.of(), List.of(), false, null);
        assertThat(FulfilDeriver.derive(f, false, P, TODAY).stage()).isEqualTo("F1");
        assertThat(FulfilDeriver.derive(f, true, P, TODAY).stage()).isEqualTo("F2");
    }

    @Test
    void cancelledItemsIgnored_andAllCancelledLeavesBoard() {
        OrderFacts.Item gone = new OrderFacts.Item(2L, "X", 0, "PENDING_PURCHASE", true);
        OrderFacts f = facts(List.of(item(1, "SHIPPED", true), gone), List.of(po(11, 1, null)), List.of(),
                List.of(ship(3, "DHL", TODAY.minusDays(1))), false, null);
        assertThat(FulfilDeriver.derive(f, true, P, TODAY).stage()).isEqualTo("F6");
        OrderFacts none = facts(List.of(gone), List.of(), List.of(), List.of(), false, null);
        assertThat(FulfilDeriver.derive(none, true, P, TODAY)).isNull();
    }

    // ---------------------------------------------------------------- 2.2 异常

    @Test
    void purchaseOverdue_flaggedAndClearedOnArrival() {
        List<OrderFacts.PoLine> lines = List.of(po(11, 1, LocalDate.of(2026, 11, 2)));
        OrderFacts late = facts(List.of(item(1, "ORDERED", true)), lines, List.of(), List.of(), false, null);
        FulfilDeriver.Result r = FulfilDeriver.derive(late, true, P, TODAY);
        assertThat(r.exceptionFlags()).isEqualTo(FollowupConstants.EX_PURCHASE_OVERDUE);
        assertThat(r.situation()).isEqualTo("采购单预计 11-02 发货，已过 2 天未到仓");
        OrderFacts arrived = facts(List.of(item(1, "RECEIVED", true)), lines, List.of(), List.of(), false, null);
        assertThat(FulfilDeriver.derive(arrived, true, P, TODAY).exceptionFlags()).isZero();
    }

    @Test
    void discrepancyAndWithdrawnOutbound_flagged() {
        OrderFacts f = facts(List.of(item(1, "RECEIVED", true)), List.of(po(11, 1, null)),
                List.of(new OrderFacts.Outbound(5L, 4, List.of(1L), null)), List.of(), true, null);
        assertThat(FulfilDeriver.derive(f, true, P, TODAY).exceptionFlags())
                .isEqualTo(FollowupConstants.EX_DISCREPANCY | FollowupConstants.EX_OUTBOUND_WITHDRAWN);
        OrderFacts rebuilt = facts(List.of(item(1, "RECEIVED", true)), List.of(po(11, 1, null)),
                List.of(new OrderFacts.Outbound(5L, 4, List.of(1L), null), new OrderFacts.Outbound(6L, 1, List.of(1L), null)),
                List.of(), false, null);
        assertThat(FulfilDeriver.derive(rebuilt, true, P, TODAY).exceptionFlags()).isZero();
    }

    // ---------------------------------------------------------------- 2.3 预计送达

    @Test
    void eta_expressAndOther_latestBatch() {
        OrderFacts dhl = facts(List.of(item(1, "SHIPPED", true)), List.of(), List.of(),
                List.of(ship(1, "DHL Express", LocalDate.of(2026, 11, 1))), false, null);
        FulfilDeriver.Result r = FulfilDeriver.derive(dhl, true, P, TODAY);
        assertThat(r.eta().date()).isEqualTo(LocalDate.of(2026, 11, 6));
        assertThat(r.eta().note()).isEqualTo("DHL Express · 按 5 天估算");
        assertThat(r.situation()).isEqualTo("11-01 已出运，DHL Express 运单 WB1");
        OrderFacts two = facts(List.of(item(1, "SHIPPED", true)), List.of(), List.of(),
                List.of(ship(1, "dhl", LocalDate.of(2026, 11, 1)), ship(2, "中远海运", LocalDate.of(2026, 10, 30))),
                false, null);
        assertThat(FulfilDeriver.derive(two, true, P, TODAY).eta().date()).isEqualTo(LocalDate.of(2026, 11, 9));
    }

    // ---------------------------------------------------------------- 2.4 节点提醒

    @Test
    void node_latestEventWins() {
        OrderFacts f = facts(List.of(item(1, "SHIPPED", true)), List.of(po(11, 1, null)), List.of(),
                List.of(ship(1, "DHL", LocalDate.of(2026, 11, 1))), false, LocalDate.of(2026, 10, 30));
        assertThat(FulfilDeriver.node(f, null)).isEqualTo(new FulfilDeriver.Node("shipped", LocalDate.of(2026, 11, 1)));
        OrderFacts fresh = facts(List.of(item(1, "PENDING_PURCHASE", true)), List.of(), List.of(), List.of(), false, null);
        assertThat(FulfilDeriver.node(fresh, null).code()).isEqualTo("created");
        assertThat(FulfilDeriver.node(fresh, TODAY).code()).isEqualTo("exception");
    }

    @Test
    void applyNode_userRecordKeptUntilNewerNode() {
        FollowupItemDO row = new FollowupItemDO();
        row.setNextActionSource(FollowupConstants.NEXT_SYSTEM);
        FulfilDeriver.Node ordered = new FulfilDeriver.Node("ordered", LocalDate.of(2026, 10, 26));
        assertThat(OrderFollowupSync.applyNode(row, ordered, "拿到供应商交期后告诉客户", TODAY)).isTrue();
        assertThat(row.getNextActionDate()).isEqualTo(LocalDate.of(2026, 10, 26));
        // 业务员 10-27 记跟进，自己定了下一步
        row.setNextActionSource(FollowupConstants.NEXT_USER);
        row.setNextAction("周五再问交期");
        row.setLastFollowupAt(LocalDateTime.of(2026, 10, 27, 9, 0));
        assertThat(OrderFollowupSync.applyNode(row, ordered, "x", TODAY)).isFalse();
        FulfilDeriver.Node shot = new FulfilDeriver.Node("shot", LocalDate.of(2026, 10, 27));
        assertThat(OrderFollowupSync.applyNode(row, shot, "x", TODAY)).isFalse();
        assertThat(row.getNextAction()).isEqualTo("周五再问交期");
        // 出运在跟进之后：替换
        FulfilDeriver.Node shipped = new FulfilDeriver.Node("shipped", LocalDate.of(2026, 11, 1));
        assertThat(OrderFollowupSync.applyNode(row, shipped, "把运单号和发货照片发给客户", TODAY)).isTrue();
        assertThat(row.getNextAction()).isEqualTo("把运单号和发货照片发给客户");
        assertThat(row.getNextActionSource()).isEqualTo(FollowupConstants.NEXT_SYSTEM);
        // 未来的节点还没到，不替换
        assertThat(OrderFollowupSync.applyNode(row, new FulfilDeriver.Node("A2", TODAY.plusDays(3)), "x", TODAY)).isFalse();
    }

    // ---------------------------------------------------------------- 3.1 售后阶段

    private static AfterSalesDeriver.State st(LocalDate signed, boolean used, int sat, int review, LocalDate invited) {
        return new AfterSalesDeriver.State(signed, used, sat, review, "阿里国际站", invited, LocalDate.of(2026, 11, 3));
    }

    private static OrderFacts.AfterSales as(long id, int status, LocalDateTime closed) {
        return new OrderFacts.AfterSales(id, "AS" + id, status, "补发出运后告诉客户运单号", LocalDateTime.of(2026, 11, 7, 9, 0),
                LocalDateTime.of(2026, 10, 28, 9, 0).plusMinutes(id), closed);
    }

    private static final OrderFacts BASE = facts(List.of(item(1, "SHIPPED", true)), List.of(), List.of(), List.of(), false, null);

    @Test
    void afterSales_stagesFollowCases() {
        assertThat(AfterSalesDeriver.derive(BASE, st(null, false, 0, 0, null), P, TODAY).stage()).isEqualTo("A1");
        assertThat(AfterSalesDeriver.derive(withCases(BASE, List.of(), true), st(null, false, 0, 0, null), P, TODAY).stage())
                .isEqualTo("A2");
        LocalDate signed = LocalDate.of(2026, 10, 30);
        AfterSalesDeriver.Result open = AfterSalesDeriver.derive(withCases(BASE, List.of(as(1, 1, null)), false),
                st(signed, false, 0, 0, null), P, TODAY);
        assertThat(open.stage()).isEqualTo("A3");
        assertThat(open.situation()).isEqualTo("售后单 AS1 处理中");
        assertThat(AfterSalesDeriver.derive(withCases(BASE, List.of(as(1, 2, null)), false), st(signed, false, 0, 0, null), P, TODAY)
                .stage()).isEqualTo("A4");
        // 两张售后单：一张处理中、一张待客户确认 → 取处理中
        assertThat(AfterSalesDeriver.derive(withCases(BASE, List.of(as(1, 2, null), as(2, 1, null)), false),
                st(signed, false, 0, 0, null), P, TODAY).stage()).isEqualTo("A3");
        assertThat(AfterSalesDeriver.derive(withCases(BASE, List.of(as(1, 3, LocalDateTime.of(2026, 10, 30, 9, 0))), false),
                st(signed, false, 0, 0, null), P, TODAY).stage()).isEqualTo("A5");
        // 撤销回到签收后的 A2，未签收的回到 A1
        assertThat(AfterSalesDeriver.derive(withCases(BASE, List.of(as(1, 4, null)), false), st(signed, false, 0, 0, null), P, TODAY)
                .stage()).isEqualTo("A2");
        assertThat(AfterSalesDeriver.derive(withCases(BASE, List.of(as(1, 4, null)), false), st(null, false, 0, 0, null), P, TODAY)
                .stage()).isEqualTo("A1");
        assertThat(AfterSalesDeriver.derive(BASE, st(signed, true, 0, 0, null), P, TODAY).stage()).isEqualTo("A5");
        assertThat(AfterSalesDeriver.derive(BASE, st(signed, true, 1, FollowupConstants.REVIEW_INVITED, TODAY), P, TODAY).stage())
                .isEqualTo("A6");
    }

    // ---------------------------------------------------------------- 3.2 售后结束

    @Test
    void afterSales_endConditions() {
        LocalDate signed = LocalDate.of(2026, 10, 1);
        assertThat(AfterSalesDeriver.derive(BASE, st(signed, true, 3, 0, null), P, TODAY).end()).isTrue();
        assertThat(AfterSalesDeriver.derive(BASE, st(signed, true, 1, FollowupConstants.REVIEW_DONE, null), P, TODAY).end()).isTrue();
        // 已评价从 A6 结束，一般 / 不满意在 A5 结束
        assertThat(AfterSalesDeriver.derive(BASE, st(signed, true, 1, FollowupConstants.REVIEW_DONE, null), P, TODAY).stage())
                .isEqualTo("A6");
        assertThat(AfterSalesDeriver.derive(BASE, st(signed, true, 3, 0, null), P, TODAY).stage()).isEqualTo("A5");
        LocalDate invited = LocalDate.of(2026, 10, 10);
        // 7 天、14 天各提醒一次，第 21 天仍没评 → 结束，未评价
        assertThat(AfterSalesDeriver.derive(BASE, st(signed, true, 1, FollowupConstants.REVIEW_INVITED, invited), P,
                invited.plusDays(20)).end()).isFalse();
        AfterSalesDeriver.Result missed = AfterSalesDeriver.derive(BASE, st(signed, true, 1, FollowupConstants.REVIEW_INVITED,
                invited), P, invited.plusDays(21));
        assertThat(missed.end()).isTrue();
        assertThat(missed.missed()).isTrue();
    }

    @Test
    void afterSales_reviewReminderNodes() {
        LocalDate invited = LocalDate.of(2026, 10, 10);
        AfterSalesDeriver.State s = st(LocalDate.of(2026, 10, 1), true, 1, FollowupConstants.REVIEW_INVITED, invited);
        List<String> codes = new ArrayList<>();
        for (int d : new int[] {3, 7, 15, 20}) {
            LocalDate day = invited.plusDays(d);
            AfterSalesDeriver.Result r = AfterSalesDeriver.derive(BASE, s, P, day);
            codes.add(AfterSalesDeriver.node(r, s, invited, P, day).code());
        }
        assertThat(codes).containsExactly("A6", "A6#1", "A6#2", "A6#2");
        assertThat(AfterSalesDeriver.remindCountOf("A6#2")).isEqualTo(2);
        AfterSalesDeriver.Result a2 = AfterSalesDeriver.derive(BASE, st(LocalDate.of(2026, 11, 1), false, 0, 0, null), P, TODAY);
        assertThat(AfterSalesDeriver.node(a2, st(LocalDate.of(2026, 11, 1), false, 0, 0, null), TODAY, P, TODAY).date())
                .isEqualTo(LocalDate.of(2026, 11, 8));
    }
}
