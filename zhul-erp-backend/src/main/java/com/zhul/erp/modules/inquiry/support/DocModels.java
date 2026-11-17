package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import org.springframework.util.StringUtils;

/**
 * 单据型号与客户型号（openspec actual-model-and-description D1）：
 * 报价单、PI、销售订单、采购单的型号有真实型号时取真实型号，否则取确认型号；
 * 客户型号是询盘确认的型号，与型号按 {@link PriceKeys#model} 归一化后相同时不显示。
 */
public final class DocModels {

    private DocModels() {
    }

    /** 单据型号：真实型号 → 确认型号 → 原始型号 */
    public static String docModel(InquiryItemDO item) {
        if (StringUtils.hasText(item.getActualModel())) {
            return item.getActualModel().trim();
        }
        return customerModel(item);
    }

    /** 客户型号：确认型号 → 原始型号 */
    public static String customerModel(InquiryItemDO item) {
        if (StringUtils.hasText(item.getConfirmedModel())) {
            return item.getConfirmedModel().trim();
        }
        return item.getOriginalModel() == null ? "" : item.getOriginalModel().trim();
    }

    /** 两个型号归一化后是否相同（忽略大小写、空格与符号）；任一为空视为相同 */
    public static boolean sameModel(String a, String b) {
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return true;
        }
        return PriceKeys.model(a).equals(PriceKeys.model(b));
    }

    /** 需要显示的客户型号：与型号不同时返回客户型号，否则返回空串 */
    public static String shownCustomerModel(String model, String customerModel) {
        return sameModel(model, customerModel) ? "" : customerModel.trim();
    }
}
