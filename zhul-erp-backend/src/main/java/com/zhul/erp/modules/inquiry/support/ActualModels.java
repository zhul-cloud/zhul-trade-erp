package com.zhul.erp.modules.inquiry.support;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.ActualModelVO;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.product.candidate.constants.CandidateConstants;
import com.zhul.erp.modules.product.candidate.service.ProductArchiver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 真实型号的唯一写入口（openspec actual-model-and-description D3）：采购在「我的询价」、业务员在询盘详情与报价单草稿都经这里。
 * 调用方负责权限、询盘未取消的校验与询盘行锁，并在同一事务内调用。
 */
@Component
@RequiredArgsConstructor
public class ActualModels {

    public static final int MAX_LENGTH = 128;

    private final InquiryItemMapper itemMapper;
    private final ProductArchiver productArchiver;
    private final ApplicationEventPublisher events;

    /**
     * 写入真实型号（null 表示这一项不改，空串表示清空），记录修改人与时间，重新建档，
     * 并发布 {@link InquiryItemModelChangedEvent} 同步下游单据。返回有变化的询盘型号 ID。
     */
    public List<Long> save(List<InquiryItemDO> items, Map<Long, String> values) {
        List<Long> changed = new ArrayList<>();
        List<InquiryItemModelChangedEvent.Change> changes = new ArrayList<>();
        String operator = SecurityUtils.getCurrentUsername();
        LocalDateTime now = LocalDateTime.now();
        for (InquiryItemDO item : items) {
            String raw = values.get(item.getId());
            if (raw == null) {
                continue;
            }
            String actual = clean(raw);
            if (Objects.equals(Objects.requireNonNullElse(item.getActualModel(), ""), actual)) {
                continue;
            }
            boolean cleared = actual.isEmpty();
            // 只改这几列：InquiryItemDO 的所属任务、选定价格按「总是更新」配置，用半空对象 updateById 会把它们写成 NULL
            itemMapper.update(null, new LambdaUpdateWrapper<InquiryItemDO>()
                    .eq(InquiryItemDO::getId, item.getId())
                    .set(InquiryItemDO::getActualModel, actual)
                    .set(InquiryItemDO::getActualModelKey, cleared ? "" : PriceKeys.model(actual))
                    .set(InquiryItemDO::getActualModelBy, cleared ? "" : operator)
                    .set(InquiryItemDO::getActualModelAt, cleared ? null : now)
                    .set(InquiryItemDO::getUpdateTime, now));
            item.setActualModel(actual);
            item.setActualModelBy(cleared ? "" : operator);
            item.setActualModelAt(cleared ? null : now);
            changed.add(item.getId());
            changes.add(new InquiryItemModelChangedEvent.Change(item.getId(), DocModels.docModel(item),
                    DocModels.customerModel(item)));
        }
        if (!changed.isEmpty()) {
            productArchiver.sync(changed);
            events.publishEvent(new InquiryItemModelChangedEvent(changes));
        }
        return changed;
    }

    /** 保存后返回给页面的这一个询盘型号 */
    public static ActualModelVO vo(InquiryItemDO item) {
        ActualModelVO vo = new ActualModelVO();
        vo.setInquiryItemId(item.getId());
        vo.setActualModel(Objects.requireNonNullElse(item.getActualModel(), ""));
        vo.setActualModelBy(Objects.requireNonNullElse(item.getActualModelBy(), ""));
        vo.setActualModelAt(item.getActualModelAt());
        vo.setArchiveStatus(item.getArchiveStatus());
        vo.setArchiveStatusName(CandidateConstants.ARCHIVE_NAMES.get(item.getArchiveStatus()));
        vo.setProductId(item.getProductId());
        String doc = DocModels.docModel(item);
        vo.setDocModel(doc);
        vo.setCustomerModel(DocModels.shownCustomerModel(doc, DocModels.customerModel(item)));
        return vo;
    }

    /** 去首尾空格，超长截断 */
    public static String clean(String raw) {
        String t = raw == null ? "" : raw.trim();
        return t.length() > MAX_LENGTH ? t.substring(0, MAX_LENGTH) : t;
    }
}
