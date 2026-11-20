package com.zhul.erp.modules.quotation.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import org.springframework.beans.BeanUtils;

import java.util.List;
import java.util.Map;

/**
 * 文字报价的「报价货况」「报价货期」（spec quotation/quotation「文字报价」）：
 * 渲染前在内存里替换每个有货行的货况、货期码值，返回副本，不改报价单。
 */
public final class QuoteTextOptions {

    /** 报价货期「按采购货期顺延」 */
    public static final String SHIFT = "shift";
    /** 报价货期「按采购货期」：原样取采购回价的货期，不顺延 */
    public static final String PURCHASE = "purchase";

    private QuoteTextOptions() {
    }

    /**
     * @param condition     货况码值
     * @param leadTime      {@link #SHIFT}、{@link #PURCHASE} 或货期码值
     * @param leadOrder     启用的货期码值，按字典顺序
     * @param purchaseLeads 采购回价 ID → 采购报的货期（回价已删除的不在里面）
     */
    public static List<QuotationItemDO> apply(List<QuotationItemDO> items, int condition, String leadTime,
                                              List<Integer> leadOrder, Map<Long, Integer> purchaseLeads) {
        boolean shift = SHIFT.equals(leadTime);
        boolean asPurchase = PURCHASE.equals(leadTime);
        Integer fixed = shift || asPurchase ? null : Integer.valueOf(leadTime);
        return items.stream().map(i -> {
            if (QuotationRenderModels.isNoStockLine(i)) {
                return i;
            }
            QuotationItemDO c = new QuotationItemDO();
            BeanUtils.copyProperties(i, c);
            c.setItemCondition(condition);
            c.setLeadTime(shift ? shifted(i, leadOrder, purchaseLeads)
                    : asPurchase ? purchaseLead(i, purchaseLeads) : fixed);
            return c;
        }).toList();
    }

    /** 采购回价的货期，没有回价时取行上的货期；未填为 0 */
    static int purchaseLead(QuotationItemDO i, Map<Long, Integer> purchaseLeads) {
        Integer base = i.getCostQuoteId() != null && purchaseLeads.containsKey(i.getCostQuoteId())
                ? purchaseLeads.get(i.getCostQuoteId()) : i.getLeadTime();
        return base == null ? 0 : base;
    }

    /** 采购回价的货期（没有回价时取行上的货期）往后挪一档；最后一档不变，未填（0）保持为空 */
    static int shifted(QuotationItemDO i, List<Integer> leadOrder, Map<Long, Integer> purchaseLeads) {
        int base = purchaseLead(i, purchaseLeads);
        if (base == 0) {
            return 0;
        }
        int idx = leadOrder.indexOf(base);
        if (idx < 0) {
            return base;
        }
        return leadOrder.get(Math.min(idx + 1, leadOrder.size() - 1));
    }

    /** 校验并解析请求参数：货况省略时为全新原装，货期省略时为按采购货期顺延 */
    public static int condition(Integer requested, int defaultCode, List<Integer> enabled) {
        int code = requested == null ? defaultCode : requested;
        if (requested != null && !enabled.contains(code)) {
            throw new BizException("报价货况不存在");
        }
        return code;
    }

    public static String leadTime(String requested, List<Integer> enabled) {
        if (requested == null || requested.isBlank() || SHIFT.equals(requested.trim())) {
            return SHIFT;
        }
        if (PURCHASE.equals(requested.trim())) {
            return PURCHASE;
        }
        String v = requested.trim();
        if (!v.matches("\\d{1,9}") || !enabled.contains(Integer.parseInt(v))) {
            throw new BizException("报价货期不存在");
        }
        return String.valueOf(Integer.parseInt(v));
    }
}
