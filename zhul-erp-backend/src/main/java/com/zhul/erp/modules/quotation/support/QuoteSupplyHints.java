package com.zhul.erp.modules.quotation.support;

import java.util.List;
import java.util.Map;

/**
 * 报价行的供应提示（OpenSpec supply-chain-phase3-quote-fulfillment design 决策 1、2）。
 * 报价模块只定义接口，由供应链模块实现；没有实现时视为没有提示。调用方负责租户上下文。
 */
public interface QuoteSupplyHints {

    /** 一行报价：行ID、品牌、型号、成本来源询价记录ID（可空） */
    record Line(Long itemId, String brand, String model, Long costQuoteId) {
    }

    /** @return 行ID → 提示文字；没有提示的行不在结果里 */
    Map<Long, List<String>> hints(List<Line> lines);
}
