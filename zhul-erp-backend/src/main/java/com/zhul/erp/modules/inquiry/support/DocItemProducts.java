package com.zhul.erp.modules.inquiry.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.product.support.ProductTags;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 报价单、PI、销售订单明细显示用：补出每行引用的商品与订货号（spec 各单据「型号明细格式与商品档案跳转」、
 * product/document-product-link「单据上的商品标记」）。商品取行上记下的（由上一张单据带下来），行上没有时取来源询盘型号的；
 * 已并入的换成主商品，候选的带「候选」标记。订货号取来源询盘型号。整张单据只查固定几次，不逐行查。
 */
@Component
@RequiredArgsConstructor
public class DocItemProducts {

    private final InquiryItemMapper inquiryItemMapper;
    private final ProductTags productTags;

    /** 一行明细的已知信息（brand、model 仅保留兼容调用方，不再按文字匹配商品） */
    public record Line(Long productId, Long inquiryItemId, String brand, String model) {
    }

    /** 补出的商品与订货号，可能为空；candidate 为 true 时 candidateId 是候选审核用的 ID */
    public record Info(Long productId, String orderCode, boolean candidate, Long candidateId) {
    }

    /** 按传入顺序返回 */
    public List<Info> resolve(List<Line> lines) {
        List<Long> itemIds = lines.stream().map(Line::inquiryItemId).filter(id -> id != null && id > 0).distinct().toList();
        Map<Long, InquiryItemDO> items = new HashMap<>();
        if (!itemIds.isEmpty()) {
            inquiryItemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                            .select(InquiryItemDO::getId, InquiryItemDO::getProductId, InquiryItemDO::getOrderCode)
                            .in(InquiryItemDO::getId, itemIds))
                    .forEach(i -> items.put(i.getId(), i));
        }
        List<Long> raw = new ArrayList<>(lines.size());
        for (Line l : lines) {
            InquiryItemDO i = l.inquiryItemId() == null ? null : items.get(l.inquiryItemId());
            raw.add(l.productId() != null ? l.productId() : i == null ? null : i.getProductId());
        }
        Map<Long, ProductTags.Tag> tags = productTags.of(raw.stream().filter(Objects::nonNull).toList());
        List<Info> result = new ArrayList<>(lines.size());
        for (int k = 0; k < lines.size(); k++) {
            Line l = lines.get(k);
            InquiryItemDO i = l.inquiryItemId() == null ? null : items.get(l.inquiryItemId());
            ProductTags.Tag tag = raw.get(k) == null ? null : tags.get(raw.get(k));
            String orderCode = i != null && StringUtils.hasText(i.getOrderCode()) ? i.getOrderCode() : null;
            result.add(tag == null ? new Info(null, orderCode, false, null)
                    : new Info(tag.productId(), orderCode, tag.candidate(), tag.candidateId()));
        }
        return result;
    }
}
