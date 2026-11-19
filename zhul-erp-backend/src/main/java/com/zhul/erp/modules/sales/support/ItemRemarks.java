package com.zhul.erp.modules.sales.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.purchase.constants.PurchaseConstants;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderDO;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderMapper;
import com.zhul.erp.modules.purchase.support.PurchaseLogs;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 型号备注（openspec item-remark）：业务员写在型号上的规格要求，只在系统内显示。
 * 有询盘的型号只存在询盘型号上（唯一来源，各单据显示时实时读取）；没有询盘的手动订单型号存在订单型号上。
 * 采购单已下单后备注有更新时，给对应的采购单行打标记，采购核对后在采购单上清除。
 */
@Component
@RequiredArgsConstructor
public class ItemRemarks {

    public static final int MAX_LENGTH = 200;

    private final InquiryItemMapper inquiryItemMapper;
    private final SalesOrderItemMapper soItemMapper;
    private final PurchaseOrderItemMapper poItemMapper;
    private final PurchaseOrderMapper poMapper;
    private final PurchaseLogs purchaseLogs;

    /** 一条型号备注：内容、最后修改人与时间 */
    public record Remark(String text, String by, LocalDateTime at) {
    }

    /** 询盘型号 ID → 备注（没有备注的不在结果里） */
    public Map<Long, Remark> ofInquiryItems(Collection<Long> inquiryItemIds) {
        List<Long> ids = inquiryItemIds == null ? List.of() : inquiryItemIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Remark> out = new HashMap<>();
        for (InquiryItemDO i : inquiryItemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                .select(InquiryItemDO::getId, InquiryItemDO::getRemark, InquiryItemDO::getRemarkBy, InquiryItemDO::getRemarkAt)
                .in(InquiryItemDO::getId, ids))) {
            if (i.getRemark() != null && !i.getRemark().isBlank()) {
                out.put(i.getId(), new Remark(i.getRemark(), i.getRemarkBy(), i.getRemarkAt()));
            }
        }
        return out;
    }

    /** 订单型号 ID → 备注：有询盘的取询盘型号的备注，没有的取订单型号自己的 */
    public Map<Long, Remark> ofSoItems(Collection<Long> soItemIds) {
        List<Long> ids = soItemIds == null ? List.of() : soItemIds.stream().filter(x -> x != null && x > 0).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<SalesOrderItemDO> lines = soItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                .select(SalesOrderItemDO::getId, SalesOrderItemDO::getInquiryItemId, SalesOrderItemDO::getRemark,
                        SalesOrderItemDO::getRemarkBy, SalesOrderItemDO::getRemarkAt)
                .in(SalesOrderItemDO::getId, ids));
        Map<Long, Remark> fromInquiry = ofInquiryItems(lines.stream().map(SalesOrderItemDO::getInquiryItemId).toList());
        Map<Long, Remark> out = new HashMap<>();
        for (SalesOrderItemDO l : lines) {
            Remark r = l.getInquiryItemId() != null && l.getInquiryItemId() > 0 ? fromInquiry.get(l.getInquiryItemId())
                    : l.getRemark() != null && !l.getRemark().isBlank() ? new Remark(l.getRemark(), l.getRemarkBy(), l.getRemarkAt()) : null;
            if (r != null) {
                out.put(l.getId(), r);
            }
        }
        return out;
    }

    /**
     * 写询盘型号的备注（调用方负责权限、询盘未取消与询盘行锁，并在同一事务内调用）。返回是否有变化。
     * 已下单的采购单里这个型号的行标红，提醒采购与供应商核对。
     */
    public boolean saveInquiryItem(InquiryItemDO item, String raw, Long operatorId) {
        String text = clean(raw);
        if (Objects.equals(Objects.requireNonNullElse(item.getRemark(), ""), text)) {
            return false;
        }
        String operator = SecurityUtils.getCurrentUsername();
        LocalDateTime now = LocalDateTime.now();
        inquiryItemMapper.update(null, new LambdaUpdateWrapper<InquiryItemDO>()
                .eq(InquiryItemDO::getId, item.getId())
                .set(InquiryItemDO::getRemark, text)
                .set(InquiryItemDO::getRemarkBy, text.isEmpty() ? "" : operator)
                .set(InquiryItemDO::getRemarkAt, text.isEmpty() ? null : now)
                .set(InquiryItemDO::getUpdateTime, now));
        item.setRemark(text);
        item.setRemarkBy(text.isEmpty() ? "" : operator);
        item.setRemarkAt(text.isEmpty() ? null : now);
        alertOrderedPurchases(soItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                        .select(SalesOrderItemDO::getId)
                        .eq(SalesOrderItemDO::getInquiryItemId, item.getId())
                        .isNull(SalesOrderItemDO::getDeletedAt))
                .stream().map(SalesOrderItemDO::getId).toList(), text, operatorId);
        return true;
    }

    /** 写没有询盘的订单型号的备注（有询盘的型号要改询盘型号的备注）。返回是否有变化 */
    public boolean saveSoItem(SalesOrderItemDO line, String raw, Long operatorId) {
        if (line.getInquiryItemId() != null && line.getInquiryItemId() > 0) {
            InquiryItemDO item = inquiryItemMapper.selectById(line.getInquiryItemId());
            if (item == null || item.getDeletedAt() != null) {
                throw new BizException("询盘型号不存在，请刷新后再试");
            }
            return saveInquiryItem(item, raw, operatorId);
        }
        String text = clean(raw);
        if (Objects.equals(Objects.requireNonNullElse(line.getRemark(), ""), text)) {
            return false;
        }
        String operator = SecurityUtils.getCurrentUsername();
        LocalDateTime now = LocalDateTime.now();
        soItemMapper.update(null, new LambdaUpdateWrapper<SalesOrderItemDO>()
                .eq(SalesOrderItemDO::getId, line.getId())
                .set(SalesOrderItemDO::getRemark, text)
                .set(SalesOrderItemDO::getRemarkBy, text.isEmpty() ? "" : operator)
                .set(SalesOrderItemDO::getRemarkAt, text.isEmpty() ? null : now)
                .set(SalesOrderItemDO::getUpdateTime, now));
        alertOrderedPurchases(List.of(line.getId()), text, operatorId);
        return true;
    }

    /** 已下单（未取消）的采购单里这些订单型号的行标红并写采购单日志；草稿采购单直接显示最新备注，不标红 */
    private void alertOrderedPurchases(List<Long> soItemIds, String text, Long operatorId) {
        if (soItemIds.isEmpty()) {
            return;
        }
        List<PurchaseOrderItemDO> lines = poItemMapper.selectList(new LambdaQueryWrapper<PurchaseOrderItemDO>()
                .select(PurchaseOrderItemDO::getId, PurchaseOrderItemDO::getPoId, PurchaseOrderItemDO::getModel)
                .in(PurchaseOrderItemDO::getSoItemId, soItemIds)
                .isNull(PurchaseOrderItemDO::getDeletedAt));
        if (lines.isEmpty()) {
            return;
        }
        Set<Long> ordered = poMapper.selectBatchIds(lines.stream().map(PurchaseOrderItemDO::getPoId).distinct().toList()).stream()
                .filter(p -> p.getDeletedAt() == null && Objects.equals(p.getStatus(), PurchaseConstants.PO_ORDERED))
                .map(PurchaseOrderDO::getId).collect(Collectors.toSet());
        LocalDateTime now = LocalDateTime.now();
        for (PurchaseOrderItemDO l : lines) {
            if (!ordered.contains(l.getPoId())) {
                continue;
            }
            poItemMapper.update(null, new LambdaUpdateWrapper<PurchaseOrderItemDO>()
                    .eq(PurchaseOrderItemDO::getId, l.getId())
                    .set(PurchaseOrderItemDO::getRemarkAlert, 1)
                    .set(PurchaseOrderItemDO::getUpdateTime, now));
            purchaseLogs.add(l.getPoId(), "型号备注更新", l.getModel() + "：" + (text.isEmpty() ? "（已清空）" : text)
                    + "（已下单，请与供应商核对）", operatorId);
        }
    }

    /** 去首尾空格；超过 200 字提示 */
    public static String clean(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.length() > MAX_LENGTH) {
            throw new BizException("型号备注不能超过 " + MAX_LENGTH + " 个字");
        }
        return t;
    }
}
