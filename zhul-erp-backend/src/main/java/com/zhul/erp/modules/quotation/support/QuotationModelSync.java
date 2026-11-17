package com.zhul.erp.modules.quotation.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.modules.inquiry.support.InquiryItemModelChangedEvent;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import com.zhul.erp.modules.quotation.repository.QuotationItemMapper;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 真实型号变了以后的报价单（spec quotation/quotation「真实型号变更后的报价单」）：
 * 草稿与编辑中新版本的型号行跟着改；已发送的版本不改，详情按 DocModelLookup 比较后提示。
 */
@Component
@RequiredArgsConstructor
public class QuotationModelSync {

    private final QuotationItemMapper itemMapper;
    private final QuotationMapper quotationMapper;

    /** 与保存真实型号同一事务同步处理 */
    @EventListener
    public void on(InquiryItemModelChangedEvent event) {
        Map<Long, InquiryItemModelChangedEvent.Change> changes = event.changes().stream()
                .collect(Collectors.toMap(InquiryItemModelChangedEvent.Change::inquiryItemId, Function.identity(), (a, b) -> b));
        List<QuotationItemDO> lines = itemMapper.selectList(new LambdaQueryWrapper<QuotationItemDO>()
                .select(QuotationItemDO::getId, QuotationItemDO::getQuotationId, QuotationItemDO::getVersionNo,
                        QuotationItemDO::getIsCurrent, QuotationItemDO::getInquiryItemId)
                .in(QuotationItemDO::getInquiryItemId, changes.keySet())
                .isNull(QuotationItemDO::getDeletedAt));
        if (lines.isEmpty()) {
            return;
        }
        Map<Long, QuotationDO> quotations = quotationMapper.selectBatchIds(lines.stream().map(QuotationItemDO::getQuotationId)
                .distinct().toList()).stream().collect(Collectors.toMap(QuotationDO::getId, Function.identity()));
        LocalDateTime now = LocalDateTime.now();
        for (QuotationItemDO line : lines) {
            if (!editable(quotations.get(line.getQuotationId()), line)) {
                continue;
            }
            InquiryItemModelChangedEvent.Change c = changes.get(line.getInquiryItemId());
            itemMapper.update(null, new LambdaUpdateWrapper<QuotationItemDO>()
                    .eq(QuotationItemDO::getId, line.getId())
                    .set(QuotationItemDO::getModel, c.docModel())
                    .set(QuotationItemDO::getCustomerModel, c.customerModel())
                    .set(QuotationItemDO::getUpdateTime, now));
        }
    }

    /** 草稿报价单的型号行，以及已发送报价单编辑中新版本的型号行 */
    public static boolean editable(QuotationDO q, QuotationItemDO line) {
        if (q == null || q.getDeletedAt() != null) {
            return false;
        }
        if (Objects.equals(q.getStatus(), QuotationConstants.STATUS_DRAFT)) {
            return Objects.equals(line.getIsCurrent(), 1);
        }
        return q.getEditingVersionNo() != null && Objects.equals(line.getVersionNo(), q.getEditingVersionNo());
    }
}
