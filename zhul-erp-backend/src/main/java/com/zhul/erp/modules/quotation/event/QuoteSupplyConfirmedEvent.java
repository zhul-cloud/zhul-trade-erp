package com.zhul.erp.modules.quotation.event;

import java.util.List;

/**
 * 报价单发送前确认了供应（OpenSpec supply-chain-phase3-quote-fulfillment design 决策 4）。
 * 在发送事务里发布；监听方须在事务提交后处理，处理失败不影响发送。
 *
 * @param tenantId    报价单所属租户
 * @param quotationNo 报价单号
 * @param operatorId  发送人
 * @param lines       有提示的行
 */
public record QuoteSupplyConfirmedEvent(Integer tenantId, String quotationNo, Long operatorId, List<Line> lines) {

    /** 一行：报价行ID、品牌、型号、确认时看到的提示 */
    public record Line(Long itemId, String brand, String model, List<String> hints) {
    }
}
