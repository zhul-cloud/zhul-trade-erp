package com.zhul.erp.modules.crm.followup.support;

import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.sales.constants.SalesConstants;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订单履约推导（design D3–D6，spec「订单履约阶段推导」「履约异常」「预计送达」「订单履约节点提醒」）：只做计算、不查库。
 * 整单阶段取所有未全部取消型号里最慢的；「部分」按采购单分组，只用于展开显示。
 */
public final class FulfilDeriver {

    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");
    private static final Set<Integer> OB_ACTIVE = Set.of(1, 2, 3);
    private static final int OB_WITHDRAWN = 4;
    private static final String STOCK_PART = "库存直发";

    private FulfilDeriver() {
    }

    public record Eta(LocalDate date, String note) {
    }

    public record Node(String code, LocalDate date) {
    }

    /** situation：「现在的情况」系统说明；node：最近一个已发生的提醒节点 */
    public record Result(String stage, int group, int exceptionFlags, Eta eta, Node node, String situation, int partCount) {
    }

    /** 展开显示的一部分 */
    public record Part(String label, String goods, String poNo, Long poId, String supplier, String stage,
                       LocalDate expectedShipDate, String arrival, String situation) {
    }

    /**
     * @param hasUserRecord 业务员是否对这张订单的履约跟进对象记过跟进（F1 用）
     * @return 没有未取消型号时为 null（订单不进板块）
     */
    public static Result derive(OrderFacts f, boolean hasUserRecord, FollowupSettings.OrderParams p, LocalDate today) {
        List<OrderFacts.Item> live = f.items().stream().filter(i -> i.quantity() > 0).toList();
        if (live.isEmpty()) {
            return null;
        }
        Set<Long> onActiveOb = activeOutboundItems(f);
        Set<Long> onPo = f.poLines().stream().map(OrderFacts.PoLine::soItemId).collect(Collectors.toSet());
        String stage = live.stream().map(i -> stageOfItem(i, onActiveOb, onPo))
                .min(Comparator.naturalOrder()).orElse("F2");
        if ("F2".equals(stage) && f.poLines().isEmpty() && !hasUserRecord) {
            stage = "F1";
        }
        int flags = exceptions(f, live, today);
        Eta eta = eta(f, p);
        return new Result(stage, groupOf(stage), flags, eta, null, situation(stage, f, live, today), parts(f, today).size());
    }

    /** 加上节点：exceptionSince 由调用方按「异常首次出现的日期」维护后传入 */
    public static Node node(OrderFacts f, LocalDate exceptionSince) {
        List<Node> nodes = new ArrayList<>(5);
        nodes.add(new Node(FollowupConstants.NODE_CREATED, f.createdAt().toLocalDate()));
        f.poLines().stream().map(OrderFacts.PoLine::orderedDate).filter(Objects::nonNull).min(Comparator.naturalOrder())
                .ifPresent(d -> nodes.add(new Node(FollowupConstants.NODE_ORDERED, d)));
        if (f.lastShotDate() != null) {
            nodes.add(new Node(FollowupConstants.NODE_SHOT, f.lastShotDate()));
        }
        f.shipments().stream().map(OrderFacts.Shipment::shippedDate).filter(Objects::nonNull).max(Comparator.naturalOrder())
                .ifPresent(d -> nodes.add(new Node(FollowupConstants.NODE_SHIPPED, d)));
        if (exceptionSince != null) {
            nodes.add(new Node(FollowupConstants.NODE_EXCEPTION, exceptionSince));
        }
        // 同一天按出现顺序取后面的（出运 > 异常 > 实拍 > 下单 > 创建）
        Node best = nodes.get(0);
        for (Node n : nodes) {
            if (!n.date().isBefore(best.date())) {
                best = n;
            }
        }
        return best;
    }

    static String stageOfItem(OrderFacts.Item i, Set<Long> onActiveOb, Set<Long> onPo) {
        String code = i.autoProgress();
        if (SalesConstants.PROGRESS_SHIPPED.equals(code)) {
            return "F6";
        }
        if (SalesConstants.PROGRESS_TO_FORWARDER.equals(code)) {
            return "F5";
        }
        if (SalesConstants.PROGRESS_RECEIVED.equals(code)) {
            return onActiveOb.contains(i.soItemId()) ? "F5" : "F4";
        }
        if (SalesConstants.PROGRESS_ORDERED.equals(code)) {
            return "F3";
        }
        if (!i.requirement() && !onPo.contains(i.soItemId())) {
            // 不经过采购（库存直发、系统外采购）：跳过 F2、F3
            return onActiveOb.contains(i.soItemId()) ? "F5" : "F4";
        }
        return "F2";
    }

    public static int groupOf(String stage) {
        return switch (stage) {
            case "F1", "F2", "F3" -> FollowupConstants.GROUP_PURCHASING;
            case "F4", "F5" -> FollowupConstants.GROUP_SHIPPING;
            default -> FollowupConstants.GROUP_IN_TRANSIT;
        };
    }

    static int exceptions(OrderFacts f, List<OrderFacts.Item> live, LocalDate today) {
        int flags = 0;
        Map<Long, OrderFacts.Item> byId = live.stream().collect(Collectors.toMap(OrderFacts.Item::soItemId, i -> i, (a, b) -> a));
        boolean overdue = f.poLines().stream().anyMatch(l -> l.expectedShipDate() != null && l.expectedShipDate().isBefore(today)
                && byId.containsKey(l.soItemId()) && notReceived(byId.get(l.soItemId())));
        if (overdue) {
            flags |= FollowupConstants.EX_PURCHASE_OVERDUE;
        }
        if (f.pendingDiscrepancy()) {
            flags |= FollowupConstants.EX_DISCREPANCY;
        }
        boolean withdrawn = f.outbounds().stream().anyMatch(o -> o.status() == OB_WITHDRAWN);
        boolean active = f.outbounds().stream().anyMatch(o -> OB_ACTIVE.contains(o.status()));
        boolean waiting = live.stream().anyMatch(i -> SalesConstants.PROGRESS_RECEIVED.equals(i.autoProgress()));
        if (withdrawn && !active && waiting) {
            flags |= FollowupConstants.EX_OUTBOUND_WITHDRAWN;
        }
        return flags;
    }

    private static boolean notReceived(OrderFacts.Item i) {
        return SalesConstants.PROGRESS_PENDING.equals(i.autoProgress()) || SalesConstants.PROGRESS_ORDERED.equals(i.autoProgress());
    }

    /** 每张已出运的出运单单独估算，整单取最晚的一批 */
    static Eta eta(OrderFacts f, FollowupSettings.OrderParams p) {
        Eta best = null;
        for (OrderFacts.Shipment s : f.shipments()) {
            if (s.shippedDate() == null) {
                continue;
            }
            int days = p.transitDays(s.carrier());
            LocalDate d = s.shippedDate().plusDays(days);
            if (best == null || d.isAfter(best.date())) {
                String carrier = s.carrier() == null || s.carrier().isBlank() ? "承运商未填" : s.carrier().trim();
                best = new Eta(d, cut(carrier, 40) + " · 按 " + days + " 天估算");
            }
        }
        return best;
    }

    static String situation(String stage, OrderFacts f, List<OrderFacts.Item> live, LocalDate today) {
        switch (stage) {
            case "F1":
                return "订单已建，还没向供应商下单";
            case "F2": {
                Set<Long> onPo = f.poLines().stream().map(OrderFacts.PoLine::soItemId).collect(Collectors.toSet());
                long n = live.stream().filter(i -> i.requirement() && !onPo.contains(i.soItemId())).count();
                return n > 0 ? "还有 " + n + " 个型号没下采购单" : "等采购下单";
            }
            case "F3": {
                Map<Long, OrderFacts.Item> byId = live.stream()
                        .collect(Collectors.toMap(OrderFacts.Item::soItemId, i -> i, (a, b) -> a));
                LocalDate due = f.poLines().stream().filter(l -> byId.containsKey(l.soItemId()) && notReceived(byId.get(l.soItemId())))
                        .map(OrderFacts.PoLine::expectedShipDate).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
                if (due == null) {
                    return "已下采购单，等供应商发货";
                }
                if (due.isBefore(today)) {
                    return "采购单预计 " + MD.format(due) + " 发货，已过 " + ChronoUnit.DAYS.between(due, today) + " 天未到仓";
                }
                return "已下采购单，供应商预计 " + MD.format(due) + " 发货";
            }
            case "F4":
                return f.lastShotDate() != null ? "已入库，实拍已完成（" + MD.format(f.lastShotDate()) + "）" : "已入库，等仓库验收和出库";
            case "F5": {
                int min = f.outbounds().stream().map(OrderFacts.Outbound::status).filter(OB_ACTIVE::contains)
                        .min(Integer::compare).orElse(0);
                return switch (min) {
                    case 1 -> "出库单待打包";
                    case 2 -> "出库单已打包，待交货代";
                    case 3 -> "已交货代，等出运";
                    default -> "等出库";
                };
            }
            default: {
                OrderFacts.Shipment s = f.shipments().stream().filter(x -> x.shippedDate() != null)
                        .max(Comparator.comparing(OrderFacts.Shipment::shippedDate)).orElse(null);
                if (s == null) {
                    return "已出运";
                }
                String carrier = s.carrier() == null ? "" : s.carrier().trim();
                String wb = s.waybillNo() == null ? "" : s.waybillNo().trim();
                return MD.format(s.shippedDate()) + " 已出运" + (carrier.isEmpty() && wb.isEmpty() ? ""
                        : "，" + (carrier + " 运单 " + wb).trim());
            }
        }
    }

    /** 部分：每张采购单一部分，不在采购单上的型号合为「库存直发」 */
    public static List<Part> parts(OrderFacts f, LocalDate today) {
        List<OrderFacts.Item> live = f.items().stream().filter(i -> i.quantity() > 0).toList();
        Set<Long> onActiveOb = activeOutboundItems(f);
        Set<Long> onPo = f.poLines().stream().map(OrderFacts.PoLine::soItemId).collect(Collectors.toSet());
        Map<Long, OrderFacts.Item> byId = live.stream().collect(Collectors.toMap(OrderFacts.Item::soItemId, i -> i, (a, b) -> a));
        Map<Long, List<OrderFacts.PoLine>> byPo = new LinkedHashMap<>(8);
        f.poLines().stream().sorted(Comparator.comparing(OrderFacts.PoLine::poId))
                .forEach(l -> byPo.computeIfAbsent(l.poId(), k -> new ArrayList<>(4)).add(l));
        List<Part> out = new ArrayList<>(byPo.size() + 1);
        for (List<OrderFacts.PoLine> lines : byPo.values()) {
            List<OrderFacts.Item> items = new ArrayList<>(lines.size());
            Set<Long> seen = new HashSet<>();
            for (OrderFacts.PoLine l : lines) {
                OrderFacts.Item i = byId.get(l.soItemId());
                if (i != null && seen.add(i.soItemId())) {
                    items.add(i);
                }
            }
            if (items.isEmpty()) {
                continue;
            }
            OrderFacts.PoLine head = lines.get(0);
            String stage = items.stream().map(i -> stageOfItem(i, onActiveOb, onPo)).min(Comparator.naturalOrder()).orElse("F3");
            LocalDate due = lines.stream().map(OrderFacts.PoLine::expectedShipDate).filter(Objects::nonNull)
                    .max(Comparator.naturalOrder()).orElse(null);
            boolean arrived = items.stream().noneMatch(FulfilDeriver::notReceived);
            String situation = "F3".equals(stage) && due != null && due.isBefore(today) ? "采购超期未到仓"
                    : situationOfPart(stage, f);
            out.add(new Part("", goods(items), head.poNo(), head.poId(), head.supplierName(), stage, due,
                    arrived ? "已到仓" : "—", situation));
        }
        List<OrderFacts.Item> stock = live.stream().filter(i -> !onPo.contains(i.soItemId())).toList();
        if (!stock.isEmpty()) {
            String stage = stock.stream().map(i -> stageOfItem(i, onActiveOb, onPo)).min(Comparator.naturalOrder()).orElse("F4");
            boolean needPurchase = stock.stream().anyMatch(OrderFacts.Item::requirement)
                    && "F2".equals(stage);
            out.add(new Part("", goods(stock), "", null, needPurchase ? "" : STOCK_PART, stage, null, "—",
                    needPurchase ? "还没下采购单" : situationOfPart(stage, f)));
        }
        List<Part> labeled = new ArrayList<>(out.size());
        for (int i = 0; i < out.size(); i++) {
            Part x = out.get(i);
            labeled.add(new Part("第 " + (i + 1) + " 部分", x.goods(), x.poNo(), x.poId(), x.supplier(), x.stage(),
                    x.expectedShipDate(), x.arrival(), x.situation()));
        }
        return labeled;
    }

    private static String situationOfPart(String stage, OrderFacts f) {
        return switch (stage) {
            case "F2" -> "还没下采购单";
            case "F3" -> "已下单，等供应商发货";
            case "F4" -> f.lastShotDate() != null ? "已入库，实拍已完成" : "已入库";
            case "F5" -> "在出库单上，等出运";
            default -> "已出运";
        };
    }

    private static String goods(List<OrderFacts.Item> items) {
        String s = items.stream().map(i -> i.model() + " ×" + i.quantity()).collect(Collectors.joining("、"));
        return cut(s, 200);
    }

    private static Set<Long> activeOutboundItems(OrderFacts f) {
        Set<Long> out = new HashSet<>();
        f.outbounds().stream().filter(o -> OB_ACTIVE.contains(o.status())).forEach(o -> out.addAll(o.soItemIds()));
        return out;
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
