package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 回价进度联动：明细 → 询价任务 → 客户询盘。调用方须在「读已提交」事务内、先 {@link #lock} 客户询盘再调用：
 * 行锁让多人同时提交串行执行，读已提交让拿到锁之后的统计能看到前一个事务刚提交的结果。
 */
@Component
@RequiredArgsConstructor
public class SourcingProgress {

    private final CustomerInquiryMapper inquiryMapper;
    private final InquiryItemMapper itemMapper;
    private final SourcingTaskMapper taskMapper;
    private final SourcingQuoteMapper quoteMapper;

    public void lock(Long inquiryId) {
        inquiryMapper.lockById(inquiryId);
    }

    /** 按已提交的询价记录重算明细的回价状态；还没有选定价格时按默认规则选一条 */
    /**
     * 采购成本价：采购负责人手动指定且那条记录仍有效时用它；否则在各采购的推荐报价里取全新原装最低、其次最低价，
     * 没有推荐报价时在全部有价记录里取。手动指定的记录被修改掉后自动回到按推荐取。
     */
    static Long costQuoteId(InquiryItemDO item, List<SourcingQuoteDO> submitted) {
        if (Objects.equals(item.getCostManual(), 1) && item.getSelectedQuoteId() != null) {
            boolean valid = submitted.stream().anyMatch(q -> q.getId().equals(item.getSelectedQuoteId()) && !Objects.equals(q.getNoStock(), 1));
            if (valid) {
                return item.getSelectedQuoteId();
            }
        }
        item.setCostManual(0);
        List<SourcingQuoteDO> recommended = submitted.stream().filter(q -> Objects.equals(q.getRecommended(), 1)).toList();
        SourcingQuoteDO pick = PriceHistoryServiceImpl.pickDefault(recommended.isEmpty() ? submitted : recommended);
        if (pick == null && !recommended.isEmpty()) {
            pick = PriceHistoryServiceImpl.pickDefault(submitted);
        }
        return pick == null ? null : pick.getId();
    }

    public void refreshItems(Collection<Long> itemIds) {
        if (itemIds.isEmpty()) {
            return;
        }
        for (InquiryItemDO item : itemMapper.selectBatchIds(itemIds)) {
            if (Objects.equals(item.getPriceSource(), InquiryConstants.PRICE_SOURCE_HISTORY)) {
                continue;
            }
            List<SourcingQuoteDO> submitted = quoteMapper.selectList(new LambdaQueryWrapper<SourcingQuoteDO>()
                    .eq(SourcingQuoteDO::getInquiryItemId, item.getId())
                    .eq(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED)
                    .isNull(SourcingQuoteDO::getDeletedAt));
            item.setSelectedQuoteId(costQuoteId(item, submitted));
            int status;
            if (item.getSelectedQuoteId() != null) {
                status = InquiryConstants.ITEM_PRICED;
            } else if (submitted.stream().anyMatch(q -> Objects.equals(q.getNoStock(), 1))) {
                status = InquiryConstants.ITEM_NO_STOCK;
            } else {
                status = InquiryConstants.ITEM_PENDING;
            }
            item.setQuoteStatus(status);
            itemMapper.updateById(item);
        }
    }

    /** 任务里每个型号都有价格或无货时变为已回价 */
    public void refreshTask(Long taskId) {
        SourcingTaskDO task = taskMapper.selectById(taskId);
        if (task == null || task.getStatus() == InquiryConstants.TASK_CANCELLED || task.getStatus() == InquiryConstants.TASK_UNASSIGNED) {
            return;
        }
        long pending = itemMapper.selectCount(new LambdaQueryWrapper<InquiryItemDO>()
                .eq(InquiryItemDO::getSourcingTaskId, taskId)
                .eq(InquiryItemDO::getQuoteStatus, InquiryConstants.ITEM_PENDING)
                .isNull(InquiryItemDO::getDeletedAt));
        if (pending == 0 && task.getStatus() == InquiryConstants.TASK_SOURCING) {
            task.setStatus(InquiryConstants.TASK_DONE);
            task.setCompletedAt(LocalDateTime.now());
            taskMapper.updateById(task);
        }
    }

    /** 重算回价进度；询价中的全部有价格或无货时进入可报价 */
    public void refreshInquiry(Long inquiryId) {
        CustomerInquiryDO inquiry = inquiryMapper.selectById(inquiryId);
        if (inquiry == null) {
            return;
        }
        LambdaQueryWrapper<InquiryItemDO> all = new LambdaQueryWrapper<InquiryItemDO>()
                .eq(InquiryItemDO::getCustomerInquiryId, inquiryId).isNull(InquiryItemDO::getDeletedAt);
        long total = itemMapper.selectCount(all);
        long priced = itemMapper.selectCount(new LambdaQueryWrapper<InquiryItemDO>()
                .eq(InquiryItemDO::getCustomerInquiryId, inquiryId).isNull(InquiryItemDO::getDeletedAt)
                .ne(InquiryItemDO::getQuoteStatus, InquiryConstants.ITEM_PENDING));
        inquiry.setTotalItemCount((int) total);
        inquiry.setPricedItemCount((int) priced);
        if (inquiry.getStatus() == InquiryConstants.STATUS_SOURCING && priced == total && total > 0) {
            inquiry.setStatus(InquiryConstants.STATUS_READY_TO_QUOTE);
        }
        inquiryMapper.updateById(inquiry);
    }
}
