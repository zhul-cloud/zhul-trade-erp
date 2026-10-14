package com.zhul.erp.modules.purchase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.dto.BargainStatsQuery;
import com.zhul.erp.modules.purchase.dto.BargainStatsVO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseRequirementMapper;
import com.zhul.erp.modules.purchase.support.Counterparty;
import com.zhul.erp.modules.purchase.support.PurchaseCalc;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.entity.ReceivingDiscrepancyDO;
import com.zhul.erp.modules.warehouse.repository.ReceivingDiscrepancyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 砍价统计（spec purchase/bargain-stats）：已下单、未取消的采购订单行，按下单日期归月；
 * 金额按下单汇率折人民币（砍价考核下单价，不受付款汇率影响）；砍价率按目标金额加权；
 * 质量索赔（不良折价）与开票成本单独列，不计入砍价。只读，数据范围为整个租户。
 */
@Service
@RequiredArgsConstructor
public class BargainStatsService {

    private static final MathContext MC = MathContext.DECIMAL128;

    private final PurchaseOrderMapper orderMapper;
    private final PurchaseOrderItemMapper itemMapper;
    private final PurchaseRequirementMapper requirementMapper;
    private final ReceivingDiscrepancyMapper discrepancyMapper;
    private final SupplierMapper supplierMapper;
    private final InquiryLookups lookups;

    private record Row(PurchaseOrderDO po, PurchaseOrderItemDO item, String category, String supplier, BigDecimal purchase,
                       BigDecimal claim, BigDecimal invoice) {
    }

    public BargainStatsVO stats(BargainStatsQuery q) {
        LocalDate from = q.getDateFrom() != null ? q.getDateFrom() : LocalDate.now().withDayOfYear(1);
        LocalDate to = q.getDateTo() != null ? q.getDateTo() : LocalDate.now();
        LambdaQueryWrapper<PurchaseOrderDO> w = new LambdaQueryWrapper<PurchaseOrderDO>()
                .eq(PurchaseOrderDO::getTenantId, PiStore.tenantId())
                .eq(PurchaseOrderDO::getStatus, PurchaseConstants.PO_ORDERED)
                .isNull(PurchaseOrderDO::getDeletedAt)
                .ge(PurchaseOrderDO::getOrderDate, from)
                .le(PurchaseOrderDO::getOrderDate, to);
        if (q.getPurchaserId() != null) {
            w.eq(PurchaseOrderDO::getPurchaserId, q.getPurchaserId());
        }
        if (q.getSupplierId() != null) {
            w.eq(PurchaseOrderDO::getSupplierId, q.getSupplierId());
        }
        Map<Long, PurchaseOrderDO> pos = orderMapper.selectList(w).stream().collect(Collectors.toMap(PurchaseOrderDO::getId, Function.identity()));
        List<PurchaseOrderItemDO> items = pos.isEmpty() ? List.of() : itemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .in(PurchaseOrderItemDO::getPoId, pos.keySet())
                .isNull(PurchaseOrderItemDO::getDeletedAt)
                .orderByAsc(PurchaseOrderItemDO::getId));
        Map<Long, String> categories = items.isEmpty() ? Map.of() : requirementMapper.selectBatchIds(
                        items.stream().map(PurchaseOrderItemDO::getRequirementId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(PurchaseRequirementDO::getId, r -> Objects.requireNonNullElse(r.getCategory(), "")));
        Map<Long, BigDecimal> discounts = new HashMap<>();
        if (!items.isEmpty()) {
            discrepancyMapper.selectList(new LambdaQueryWrapper<ReceivingDiscrepancyDO>()
                            .in(ReceivingDiscrepancyDO::getPoItemId, items.stream().map(PurchaseOrderItemDO::getId).toList())
                            .eq(ReceivingDiscrepancyDO::getStatus, WarehouseConstants.DIFF_HANDLED)
                            .eq(ReceivingDiscrepancyDO::getResolution, WarehouseConstants.RES_DISCOUNT)
                            .isNull(ReceivingDiscrepancyDO::getDeletedAt))
                    .forEach(d -> discounts.merge(d.getPoItemId(), nz(d.getDiscountAmount()), BigDecimal::add));
        }
        Map<Long, String> suppliers = supplierNames(pos.values());

        List<Row> rows = new ArrayList<>();
        for (PurchaseOrderItemDO i : items) {
            PurchaseOrderDO po = pos.get(i.getPoId());
            String category = categories.getOrDefault(i.getRequirementId(), "");
            if (StringUtils.hasText(q.getBrand()) && !q.getBrand().trim().equalsIgnoreCase(Objects.requireNonNullElse(i.getBrand(), ""))) {
                continue;
            }
            if (StringUtils.hasText(q.getCategory()) && !q.getCategory().trim().equals(category)) {
                continue;
            }
            BigDecimal rate = po.getExchangeRate();
            BigDecimal goods = nz(i.getUnitPrice()).multiply(BigDecimal.valueOf(i.getQuantity()), MC);
            BigDecimal share = nz(po.getItemAmount()).signum() == 0 ? BigDecimal.ZERO : goods.divide(po.getItemAmount(), MC);
            rows.add(new Row(po, i, category, suppliers.get(po.getId()), goods.multiply(rate, MC),
                    nz(discounts.get(i.getId())).multiply(rate, MC), nz(po.getInvoiceCost()).multiply(share, MC).multiply(rate, MC)));
        }
        Map<Long, String> users = lookups.userNames(pos.values().stream().map(PurchaseOrderDO::getPurchaserId).filter(Objects::nonNull).distinct().toList());

        String groupBy = q.getGroupBy() == null ? "PURCHASER" : q.getGroupBy();
        Function<Row, String> keyOf = switch (groupBy) {
            case "SUPPLIER" -> r -> r.supplier();
            case "BRAND" -> r -> StringUtils.hasText(r.item().getBrand()) ? r.item().getBrand() : "未填品牌";
            case "CATEGORY" -> r -> StringUtils.hasText(r.category()) ? r.category() : "未分类";
            case "MONTH" -> r -> r.po().getOrderDate().toString().substring(0, 7);
            default -> r -> String.valueOf(r.po().getPurchaserId());
        };
        Map<String, List<Row>> grouped = rows.stream().collect(Collectors.groupingBy(keyOf, LinkedHashMap::new, Collectors.toList()));
        List<BargainStatsVO.Group> groups = new ArrayList<>();
        grouped.forEach((key, list) -> {
            BargainStatsVO.Group g = new BargainStatsVO.Group();
            g.setKey(key);
            g.setLabel(switch (groupBy) {
                case "PURCHASER" -> users.getOrDefault(list.get(0).po().getPurchaserId(), "未指定");
                case "MONTH" -> list.get(0).po().getOrderDate().getYear() + " 年 " + list.get(0).po().getOrderDate().getMonthValue() + " 月";
                default -> key;
            });
            g.setTotals(totals(list));
            g.setLines(list.stream().map(r -> line(r, users)).toList());
            groups.add(g);
        });
        if ("MONTH".equals(groupBy)) {
            groups.sort(Comparator.comparing(BargainStatsVO.Group::getKey));
        } else {
            groups.sort(Comparator.comparing((BargainStatsVO.Group g) -> g.getTotals().getBargainAmount()).reversed());
        }
        BargainStatsVO vo = new BargainStatsVO();
        vo.setSummary(totals(rows));
        vo.setGroups(groups);
        return vo;
    }

    private static BargainStatsVO.Totals totals(List<Row> rows) {
        BigDecimal purchase = BigDecimal.ZERO;
        BigDecimal target = BigDecimal.ZERO;
        BigDecimal bargain = BigDecimal.ZERO;
        BigDecimal noTarget = BigDecimal.ZERO;
        BigDecimal claim = BigDecimal.ZERO;
        BigDecimal invoice = BigDecimal.ZERO;
        for (Row r : rows) {
            purchase = purchase.add(r.purchase());
            claim = claim.add(r.claim());
            invoice = invoice.add(r.invoice());
            if (r.item().getTargetPrice() == null) {
                noTarget = noTarget.add(r.purchase());
            } else {
                target = target.add(PurchaseCalc.targetAmount(r.item().getTargetPrice(), r.item().getQuantity()));
                bargain = bargain.add(nz(r.item().getBargainAmount()));
            }
        }
        BargainStatsVO.Totals t = new BargainStatsVO.Totals();
        t.setLineCount(rows.size());
        t.setPurchaseAmount(PurchaseCalc.money(purchase));
        t.setTargetAmount(PurchaseCalc.money(target));
        t.setBargainAmount(PurchaseCalc.money(bargain));
        t.setBargainRate(PurchaseCalc.rate(t.getBargainAmount(), t.getTargetAmount()));
        t.setNoTargetAmount(PurchaseCalc.money(noTarget));
        t.setClaimAmount(PurchaseCalc.money(claim));
        t.setInvoiceCost(PurchaseCalc.money(invoice));
        return t;
    }

    private static BargainStatsVO.LineRow line(Row r, Map<Long, String> users) {
        BargainStatsVO.LineRow x = new BargainStatsVO.LineRow();
        x.setPoId(r.po().getId());
        x.setPoNo(r.po().getPoNo());
        x.setOrderDate(r.po().getOrderDate());
        x.setModel(r.item().getModel());
        x.setBrand(r.item().getBrand());
        x.setCategory(r.category());
        x.setSupplierName(r.supplier());
        x.setPurchaserName(users.get(r.po().getPurchaserId()));
        x.setQuantity(r.item().getQuantity());
        x.setTargetPrice(r.item().getTargetPrice());
        x.setNetPriceCny(r.item().getNetPriceCny());
        x.setBargainAmount(r.item().getBargainAmount());
        x.setBargainRate(r.item().getTargetPrice() == null ? null
                : PurchaseCalc.rate(r.item().getBargainAmount(), PurchaseCalc.targetAmount(r.item().getTargetPrice(), r.item().getQuantity())));
        x.setClaimAmount(PurchaseCalc.money(r.claim()));
        return x;
    }

    /** 采购订单 → 供应商名称；线上店铺为「平台 · 店铺名」 */
    private Map<Long, String> supplierNames(java.util.Collection<PurchaseOrderDO> pos) {
        List<Long> ids = pos.stream().map(PurchaseOrderDO::getSupplierId).filter(id -> id != null && id > 0).distinct().toList();
        Map<Long, String> names = ids.isEmpty() ? Map.of()
                : supplierMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(SupplierDO::getId, SupplierDO::getName));
        Map<Long, String> out = new HashMap<>();
        for (PurchaseOrderDO p : pos) {
            Counterparty cp = Counterparty.of(p);
            out.put(p.getId(), cp.isShop() ? cp.shopTitle() : names.getOrDefault(p.getSupplierId(), "未知供应商"));
        }
        return out;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
