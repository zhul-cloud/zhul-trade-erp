package com.zhul.erp.modules.aftersales.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.aftersales.dto.AfterSalesVO;
import com.zhul.erp.modules.aftersales.dto.ReplacementRequest;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.purchase.support.RequirementLifecycle;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.OrderProgress;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 售后补发订单（spec sales/sales-order「售后补发订单」）：0 元销售订单，来源「售后补发」，记录售后单与原订单；
 * 型号行单价、金额为 0，另存申报单价（原币，HALF_UP 两位）。照常生成采购需求。调用方负责事务与售后单行锁。
 */
@Component
@RequiredArgsConstructor
public class ReplacementOrders {

    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper itemMapper;
    private final UserBasicMapper userMapper;
    private final DocumentNumberService documentNumberService;
    private final RequirementLifecycle requirementLifecycle;
    private final OrderProgress progress;

    /** 生成补发订单，返回订单与型号行 */
    public SalesOrderDO create(AfterSalesDO a, ReplacementRequest req) {
        SalesOrderDO order = a.getSoId() == null || a.getSoId() == 0 ? null : orderMapper.selectById(a.getSoId());
        if (order == null || order.getDeletedAt() != null) {
            throw new BizException("没有订单的售后单不能补发");
        }
        // 对补发订单再售后时，成本仍计入最初的订单
        SalesOrderDO original = Objects.equals(order.getSource(), SalesConstants.SO_REPLACEMENT)
                ? orderMapper.selectById(order.getOriginalSoId()) : order;
        List<ReplacementRequest.Line> lines = req.getItems() == null ? List.of()
                : req.getItems().stream().filter(Objects::nonNull).toList();
        if (lines.isEmpty()) {
            throw new BizException("请至少选择一个补发型号");
        }
        Map<Long, SalesOrderItemDO> orderItems = itemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                        .eq(SalesOrderItemDO::getSoId, order.getId())
                        .isNull(SalesOrderItemDO::getDeletedAt))
                .stream().collect(Collectors.toMap(SalesOrderItemDO::getId, i -> i));
        requirePurchasers(lines.stream().map(ReplacementRequest.Line::getPurchaserId).filter(Objects::nonNull).collect(Collectors.toSet()));

        List<SalesOrderItemDO> rows = new ArrayList<>(lines.size());
        int lineNo = 1;
        for (ReplacementRequest.Line l : lines) {
            String at = "第 " + lineNo + " 个型号";
            if (l.getQuantity() == null || l.getQuantity() <= 0) {
                throw new BizException(at + "的数量需要大于 0");
            }
            SalesOrderItemDO x = new SalesOrderItemDO();
            BigDecimal declared = l.getDeclaredPrice();
            if (l.getSoItemId() != null) {
                SalesOrderItemDO src = orderItems.get(l.getSoItemId());
                if (src == null) {
                    throw new BizException(at + "不是这张订单的型号");
                }
                if (l.getQuantity() > Objects.requireNonNullElse(src.getQuantity(), 0)) {
                    throw new BizException(src.getModel() + " 的补发数量不能超过订单数量 " + src.getQuantity());
                }
                if (declared == null) {
                    declared = src.getDeclaredPrice() != null ? src.getDeclaredPrice() : src.getUnitPrice();
                }
                x.setQuotationId(Objects.requireNonNullElse(src.getQuotationId(), 0L));
                x.setQuotationItemId(Objects.requireNonNullElse(src.getQuotationItemId(), 0L));
                x.setModel(src.getModel());
                x.setBrand(src.getBrand());
                x.setCategory(Objects.requireNonNullElse(src.getCategory(), ""));
                x.setDescription(Objects.requireNonNullElse(src.getDescription(), ""));
                x.setDescriptionEn(Objects.requireNonNullElse(src.getDescriptionEn(), ""));
                x.setItemCondition(Objects.requireNonNullElse(src.getItemCondition(), 0));
                x.setWarranty(Objects.requireNonNullElse(src.getWarranty(), ""));
                x.setCostPrice(src.getCostPrice());
                x.setHsCode(Objects.requireNonNullElse(src.getHsCode(), ""));
                x.setOriginCountry(Objects.requireNonNullElse(src.getOriginCountry(), ""));
                x.setPurchaserId(l.getPurchaserId() != null ? l.getPurchaserId() : src.getPurchaserId());
            } else {
                if (!StringUtils.hasText(l.getModel())) {
                    throw new BizException("请填写" + at + "的型号");
                }
                if (declared == null) {
                    throw new BizException("请填写 " + l.getModel().trim() + " 的申报单价");
                }
                x.setQuotationId(0L);
                x.setQuotationItemId(0L);
                x.setModel(l.getModel().trim());
                x.setBrand(l.getBrand() == null ? "" : l.getBrand().trim());
                x.setCategory("");
                x.setDescription("");
                x.setDescriptionEn("");
                x.setItemCondition(0);
                x.setWarranty("");
                x.setHsCode("");
                x.setOriginCountry("");
                x.setPurchaserId(l.getPurchaserId());
            }
            if (declared == null || declared.signum() <= 0) {
                throw new BizException(x.getModel() + " 的申报单价需要大于 0");
            }
            x.setTenantId(a.getTenantId());
            x.setLineNo(lineNo++);
            x.setPiItemId(0L);
            x.setCustomerInquiryId(0L);
            x.setInquiryItemId(0L);
            x.setLeadTime(0);
            x.setQuantity(l.getQuantity());
            x.setOriginalQuantity(l.getQuantity());
            x.setCancelledQuantity(0);
            x.setUnitPrice(BigDecimal.ZERO.setScale(2));
            x.setDeclaredPrice(declared.setScale(2, RoundingMode.HALF_UP));
            x.setAmount(BigDecimal.ZERO.setScale(2));
            x.setAmountCny(BigDecimal.ZERO.setScale(2));
            x.setRemark("");
            x.setStockType(SalesConstants.STOCK_SPOT);
            x.setProgressCode(SalesConstants.PROGRESS_PENDING);
            rows.add(x);
        }

        SalesOrderDO o = new SalesOrderDO();
        o.setTenantId(a.getTenantId());
        o.setSoNo(documentNumberService.next(DocumentType.SO));
        o.setSource(SalesConstants.SO_REPLACEMENT);
        o.setAfterSalesId(a.getId());
        o.setOriginalSoId(original.getId());
        o.setSalesDate(LocalDate.now());
        o.setPiId(0L);
        o.setPiVersionNo(0);
        o.setCustomerId(order.getCustomerId());
        o.setCustomerType(order.getCustomerType());
        o.setOwnerId(order.getOwnerId());
        o.setBusinessUnitId(order.getBusinessUnitId());
        o.setCurrencyCode(order.getCurrencyCode());
        o.setExchangeRate(order.getExchangeRate());
        o.setBuyerJson(order.getBuyerJson());
        o.setConsigneeJson(order.getConsigneeJson());
        o.setDeliveryTime("");
        o.setPaymentTerm("");
        o.setIncoterm(Objects.requireNonNullElse(order.getIncoterm(), ""));
        o.setIncotermPlace(Objects.requireNonNullElse(order.getIncotermPlace(), ""));
        o.setPortOfShipment(Objects.requireNonNullElse(order.getPortOfShipment(), ""));
        String remark = req.getRemark() == null ? "" : req.getRemark().trim();
        o.setRemark(("售后补发（" + a.getAsNo() + "）" + (remark.isEmpty() ? "" : "：" + remark)));
        o.setDiscountAmount(BigDecimal.ZERO);
        o.setDiscountAmountCny(BigDecimal.ZERO);
        o.setItemAmount(BigDecimal.ZERO);
        o.setFeeAmount(BigDecimal.ZERO);
        o.setTotalAmount(BigDecimal.ZERO);
        o.setTotalAmountCny(BigDecimal.ZERO);
        o.setStatus(SalesConstants.SO_ACTIVE);
        o.setProgressCode(SalesConstants.PROGRESS_PENDING);
        o.setStockType(SalesConstants.STOCK_SPOT);
        o.setReceiptStatus(SalesConstants.RECEIPT_FREE);
        o.setReceivedAmount(BigDecimal.ZERO);
        o.setFeeDiffAmount(BigDecimal.ZERO);
        o.setCancelReason("");
        orderMapper.insert(o);
        for (SalesOrderItemDO x : rows) {
            x.setSoId(o.getId());
            itemMapper.insert(x);
        }
        requirementLifecycle.onOrderCreated(o, rows);
        return o;
    }

    /** 售后单的补发订单（新的在前） */
    public List<SalesOrderDO> of(Long afterSalesId) {
        return orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getAfterSalesId, afterSalesId)
                .eq(SalesOrderDO::getSource, SalesConstants.SO_REPLACEMENT)
                .isNull(SalesOrderDO::getDeletedAt)
                .orderByDesc(SalesOrderDO::getId));
    }

    /** 已出运（订单进度到「已出运」或之后，含已完成） */
    public boolean shipped(SalesOrderDO o, List<OrderProgress.Step> steps) {
        if (SalesConstants.PROGRESS_COMPLETED.equals(o.getProgressCode())) {
            return true;
        }
        int shippedRank = OrderProgress.rank(steps, SalesConstants.PROGRESS_SHIPPED);
        return shippedRank < steps.size() && OrderProgress.rank(steps, o.getProgressCode()) >= shippedRank
                && OrderProgress.rank(steps, o.getProgressCode()) < steps.size();
    }

    /** 还没出运也没取消的补发订单号（结案用） */
    public List<String> unshipped(Long afterSalesId) {
        List<OrderProgress.Step> steps = progress.ordered();
        return of(afterSalesId).stream()
                .filter(o -> o.getStatus() == SalesConstants.SO_ACTIVE && !shipped(o, steps))
                .map(SalesOrderDO::getSoNo).toList();
    }

    /** 售后单详情里的补发订单 */
    public List<AfterSalesVO.ReplacementBrief> briefs(Long afterSalesId) {
        List<SalesOrderDO> orders = of(afterSalesId);
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<Long, List<SalesOrderItemDO>> items = itemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                        .in(SalesOrderItemDO::getSoId, orders.stream().map(SalesOrderDO::getId).toList())
                        .isNull(SalesOrderItemDO::getDeletedAt)
                        .orderByAsc(SalesOrderItemDO::getLineNo))
                .stream().collect(Collectors.groupingBy(SalesOrderItemDO::getSoId));
        List<OrderProgress.Step> steps = progress.ordered();
        Map<String, String> names = progress.names();
        return orders.stream().map(o -> {
            List<SalesOrderItemDO> mine = items.getOrDefault(o.getId(), List.of());
            AfterSalesVO.ReplacementBrief b = new AfterSalesVO.ReplacementBrief();
            b.setSoId(o.getId());
            b.setSoNo(o.getSoNo());
            b.setSummary(mine.stream().filter(i -> i.getQuantity() != null && i.getQuantity() > 0)
                    .map(i -> i.getModel() + " × " + i.getQuantity()).collect(Collectors.joining("、")));
            b.setCurrencyCode(o.getCurrencyCode());
            b.setDeclaredAmount(mine.stream().filter(i -> i.getDeclaredPrice() != null)
                    .map(i -> i.getDeclaredPrice().multiply(BigDecimal.valueOf(Objects.requireNonNullElse(i.getQuantity(), 0))))
                    .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP));
            b.setStatus(o.getStatus());
            String code = o.getStatus() == SalesConstants.SO_ACTIVE ? o.getProgressCode() : SalesConstants.PROGRESS_CANCELLED;
            b.setProgressCode(code);
            b.setProgressName(names.getOrDefault(code, code));
            b.setShipped(o.getStatus() == SalesConstants.SO_ACTIVE && shipped(o, steps));
            b.setCreateBy(o.getCreateBy());
            b.setCreateTime(o.getCreateTime());
            return b;
        }).toList();
    }

    private void requirePurchasers(Set<Long> ids) {
        if (ids.isEmpty()) {
            return;
        }
        long found = userMapper.selectCount(new LambdaQueryWrapper<UserBasicDO>()
                .in(UserBasicDO::getId, ids.stream().map(Long::intValue).toList())
                .eq(UserBasicDO::getTenantId, com.zhul.erp.modules.sales.support.PiStore.tenantId()));
        if (found < ids.size()) {
            throw new BizException("采购员不存在");
        }
    }
}
