package com.zhul.erp.modules.inquiry.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.product.service.ProductLookupService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 报价单、PI、销售订单明细显示用：补出每行对应的商品档案与订货号（spec 各单据「型号明细格式与商品档案跳转」）。
 * 商品档案依次取：行自身记下的（手动订单）→ 来源询盘型号已建档的 → 品牌 + 型号精确匹配；订货号取来源询盘型号。
 * 整张单据只查两次（询盘型号、商品档案），不逐行查。
 */
@Component
@RequiredArgsConstructor
public class DocItemProducts {

    private final InquiryItemMapper inquiryItemMapper;
    private final ProductLookupService productLookup;

    /** 一行明细的已知信息 */
    public record Line(Long productId, Long inquiryItemId, String brand, String model) {
    }

    /** 补出的商品档案与订货号，可能为空 */
    public record Info(Long productId, String orderCode) {
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
        List<String[]> toMatch = new ArrayList<>();
        for (Line l : lines) {
            InquiryItemDO i = l.inquiryItemId() == null ? null : items.get(l.inquiryItemId());
            if (l.productId() == null && (i == null || i.getProductId() == null)) {
                toMatch.add(new String[] {l.brand(), l.model()});
            }
        }
        Map<String, Long> matched = toMatch.isEmpty() ? Map.of() : productLookup.matchAll(toMatch);
        List<Info> result = new ArrayList<>(lines.size());
        for (Line l : lines) {
            InquiryItemDO i = l.inquiryItemId() == null ? null : items.get(l.inquiryItemId());
            Long productId = l.productId() != null ? l.productId()
                    : i != null && i.getProductId() != null ? i.getProductId()
                    : matched.get(ProductLookupService.matchKey(l.brand(), l.model()));
            String orderCode = i != null && StringUtils.hasText(i.getOrderCode()) ? i.getOrderCode() : null;
            result.add(new Info(productId, orderCode));
        }
        return result;
    }
}
