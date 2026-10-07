package com.zhul.erp.modules.quotation.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import com.zhul.erp.modules.quotation.repository.QuotationItemMapper;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * 报价单成交由销售订单推进：订单创建 / 取消时标记报价行是否成交（won），再按行重算报价单状态
 * （全部成交 → 已成交，部分 → 部分成交，没有 → 已发送），最后推进客户询盘。
 * 调用方须已锁住 PI；本类按「报价单 ID 升序 → 询盘 ID 升序」加锁，与 design.md 的加锁顺序一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuotationDeals {

    private static final Set<Integer> RECOMPUTABLE = Set.of(QuotationConstants.STATUS_SENT, QuotationConstants.STATUS_WON,
            QuotationConstants.STATUS_PARTIAL);

    private final QuotationMapper quotationMapper;
    private final QuotationItemMapper itemMapper;
    private final InquiryStatusSync statusSync;

    /** 设置报价行成交标记，并重算涉及的报价单与客户询盘 */
    public void apply(Collection<Long> wonItemIds, Collection<Long> lostItemIds) {
        Set<Long> itemIds = new TreeSet<>(wonItemIds);
        itemIds.addAll(lostItemIds);
        if (itemIds.isEmpty()) {
            return;
        }
        Set<Long> quotationIds = new TreeSet<>();
        itemMapper.selectBatchIds(itemIds).forEach(i -> quotationIds.add(i.getQuotationId()));
        for (Long id : quotationIds) {
            quotationMapper.lockById(id);
        }
        Set<Long> inquiryIds = new TreeSet<>();
        quotationIds.forEach(id -> inquiryIds.addAll(statusSync.inquiryIdsOf(id)));
        statusSync.lock(inquiryIds);

        if (!wonItemIds.isEmpty()) {
            itemMapper.update(null, new LambdaUpdateWrapper<QuotationItemDO>()
                    .set(QuotationItemDO::getWon, 1).set(QuotationItemDO::getUpdateTime, LocalDateTime.now())
                    .in(QuotationItemDO::getId, wonItemIds));
        }
        if (!lostItemIds.isEmpty()) {
            itemMapper.update(null, new LambdaUpdateWrapper<QuotationItemDO>()
                    .set(QuotationItemDO::getWon, 0).set(QuotationItemDO::getUpdateTime, LocalDateTime.now())
                    .in(QuotationItemDO::getId, lostItemIds));
        }
        for (Long id : quotationIds) {
            recompute(id);
        }
        statusSync.sync(inquiryIds);
    }

    private void recompute(Long quotationId) {
        QuotationDO q = quotationMapper.selectById(quotationId);
        if (q == null || q.getDeletedAt() != null || !RECOMPUTABLE.contains(q.getStatus())) {
            return;
        }
        List<QuotationItemDO> items = itemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                .select(QuotationItemDO::getWon)
                .eq(QuotationItemDO::getQuotationId, quotationId)
                .isNull(QuotationItemDO::getDeletedAt));
        long won = items.stream().filter(i -> i.getWon() != null && i.getWon() == 1).count();
        int target = won == 0 ? QuotationConstants.STATUS_SENT
                : won == items.size() ? QuotationConstants.STATUS_WON : QuotationConstants.STATUS_PARTIAL;
        if (target == q.getStatus()) {
            return;
        }
        log.info("销售订单推进报价单状态，quotationId={}, {} -> {}", quotationId, q.getStatus(), target);
        quotationMapper.update(null, new LambdaUpdateWrapper<QuotationDO>()
                .set(QuotationDO::getStatus, target)
                .set(QuotationDO::getClosedAt, target == QuotationConstants.STATUS_SENT ? null : LocalDateTime.now())
                .set(QuotationDO::getUpdateTime, LocalDateTime.now())
                .eq(QuotationDO::getId, quotationId));
    }
}
