package com.zhul.erp.modules.purchase.support;

import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.notification.constants.NotificationNode;
import com.zhul.erp.modules.system.notification.support.Notice;
import com.zhul.erp.modules.system.notification.support.Notifier;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 销售订单与采购相关的通知（spec system/notification「销售订单与采购通知」） */
@Component
@RequiredArgsConstructor
public class PurchaseNotices {

    private static final String REQUIREMENTS = "/purchase/requirements";
    private static final int MAX_MODELS = 5;

    private final Notifier notifier;
    private final SalesOrderMapper salesOrderMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final UserBasicMapper userBasicMapper;

    /** 订单生成了采购需求：按采购员各发一条；没有采购员的发给采购负责人 */
    public void orderCreated(SalesOrderDO o, List<PurchaseRequirementDO> reqs) {
        Map<Long, List<PurchaseRequirementDO>> byBuyer = reqs.stream()
                .collect(Collectors.groupingBy(r -> buyer(r.getPurchaserId()), LinkedHashMap::new, Collectors.toList()));
        byBuyer.forEach((buyer, rows) -> notifier.send(toBuy(o, rows, buyer)));
    }

    /** 改采购员：新采购员收待办（与订单生成的合并），原采购员收知会；改为空时发给采购负责人 */
    public void reassigned(List<PurchaseRequirementDO> reqs, Map<Long, Long> before, Long buyer) {
        Map<Long, SalesOrderDO> orders = orders(reqs.stream().map(PurchaseRequirementDO::getSoId).toList());
        String name = buyer == null ? "" : userName(buyer);
        Map<Long, List<PurchaseRequirementDO>> bySo = reqs.stream()
                .collect(Collectors.groupingBy(PurchaseRequirementDO::getSoId, LinkedHashMap::new, Collectors.toList()));
        bySo.forEach((soId, rows) -> {
            SalesOrderDO o = orders.get(soId);
            if (o == null) {
                return;
            }
            notifier.send(toBuy(o, rows, buyer(buyer)));
            Map<Long, List<PurchaseRequirementDO>> byOld = rows.stream()
                    .filter(r -> before.get(r.getId()) != null && !Objects.equals(before.get(r.getId()), buyer))
                    .collect(Collectors.groupingBy(r -> before.get(r.getId()), LinkedHashMap::new, Collectors.toList()));
            byOld.forEach((old, moved) -> notifier.send(Notice.of(NotificationNode.PURCHASER_CHANGED, o.getSoNo(), REQUIREMENTS)
                    .merge(soId)
                    .ids(ids(moved))
                    .content((buyer == null ? "已改为未指定采购员" : "已转给 " + name) + " · " + models(moved))
                    .to(old)));
        });
    }

    /** 订单取消：已有数量在采购订单上的型号，通知这些采购订单的采购员去和供应商处理 */
    public void orderCancelled(SalesOrderDO o, Map<Long, List<String>> modelsByPo) {
        Map<Long, PurchaseOrderDO> pos = pos(modelsByPo.keySet());
        Map<Long, List<String>> byBuyer = new LinkedHashMap<>();
        Map<Long, Long> firstPo = new LinkedHashMap<>();
        modelsByPo.forEach((poId, models) -> {
            PurchaseOrderDO p = pos.get(poId);
            if (p == null || p.getPurchaserId() == null) {
                return;
            }
            byBuyer.computeIfAbsent(p.getPurchaserId(), k -> new ArrayList<>()).addAll(models);
            firstPo.putIfAbsent(p.getPurchaserId(), poId);
        });
        byBuyer.forEach((buyer, models) -> notifier.send(Notice.of(NotificationNode.SO_CANCELLED, o.getSoNo(),
                        "/purchase/orders/" + firstPo.get(buyer))
                .count((int) models.stream().distinct().count())
                .content("订单已取消，请与供应商决定取消采购或保留货物：" + join(models.stream().distinct().toList()))
                .to(buyer)));
    }

    /** 部分取消的型号已有数量在采购订单上：通知这些采购订单的采购员 */
    public void itemCancelled(SalesOrderDO o, String model, int cancelled, Collection<RequirementReduction.PoQty> ordered) {
        Map<Long, PurchaseOrderDO> pos = pos(ordered.stream().map(RequirementReduction.PoQty::poId).toList());
        Map<Long, List<RequirementReduction.PoQty>> byBuyer = new LinkedHashMap<>();
        for (RequirementReduction.PoQty q : ordered) {
            PurchaseOrderDO p = pos.get(q.poId());
            if (p != null && p.getPurchaserId() != null) {
                byBuyer.computeIfAbsent(p.getPurchaserId(), k -> new ArrayList<>()).add(q);
            }
        }
        byBuyer.forEach((buyer, qs) -> notifier.send(Notice.of(NotificationNode.SO_CANCELLED, o.getSoNo(),
                        "/purchase/orders/" + qs.get(0).poId())
                .count(1)
                .content(model + " 取消 " + cancelled + " 个，涉及已下单 "
                        + qs.stream().map(q -> q.poNo() + " " + q.quantity() + " 个").collect(Collectors.joining("、"))
                        + "，请与供应商处理")
                .to(buyer)));
    }

    private Notice toBuy(SalesOrderDO o, List<PurchaseRequirementDO> rows, Long buyer) {
        NotificationNode node = buyer == 0L ? NotificationNode.SO_NO_PURCHASER : NotificationNode.SO_PURCHASE;
        Notice n = Notice.of(node, o.getSoNo(), REQUIREMENTS)
                .merge(o.getId())
                .ids(ids(rows))
                .content(models(rows));
        return buyer == 0L ? n : n.to(buyer);
    }

    private static Long buyer(Long id) {
        return id == null || id <= 0 ? 0L : id;
    }

    private static List<Long> ids(List<PurchaseRequirementDO> rows) {
        return rows.stream().map(PurchaseRequirementDO::getId).toList();
    }

    private static String models(List<PurchaseRequirementDO> rows) {
        return join(rows.stream().map(r -> r.getModel() + " × " + r.getQuantity()).toList());
    }

    private static String join(List<String> items) {
        String head = items.stream().limit(MAX_MODELS).collect(Collectors.joining(" · "));
        return items.size() > MAX_MODELS ? head + " 等 " + items.size() + " 个型号" : head;
    }

    private Map<Long, SalesOrderDO> orders(Collection<Long> ids) {
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        return keys.isEmpty() ? Map.of() : salesOrderMapper.selectBatchIds(keys).stream()
                .collect(Collectors.toMap(SalesOrderDO::getId, x -> x));
    }

    private Map<Long, PurchaseOrderDO> pos(Collection<Long> ids) {
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        return keys.isEmpty() ? Map.of() : purchaseOrderMapper.selectBatchIds(keys).stream()
                .collect(Collectors.toMap(PurchaseOrderDO::getId, x -> x));
    }

    private String userName(Long id) {
        UserBasicDO u = userBasicMapper.selectById(id.intValue());
        return u == null ? "" : u.getName();
    }
}
