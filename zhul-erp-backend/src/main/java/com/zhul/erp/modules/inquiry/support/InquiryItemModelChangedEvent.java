package com.zhul.erp.modules.inquiry.support;

import java.util.List;

/**
 * 询盘型号的真实型号变了（openspec actual-model-and-description D3）。
 * 同步发布、同步处理：报价、PI、销售订单、采购各自更新还没发给外部的单据，与保存真实型号同一事务。
 */
public record InquiryItemModelChangedEvent(List<Change> changes) {

    /** 一个询盘型号的新单据型号与客户型号 */
    public record Change(Long inquiryItemId, String docModel, String customerModel) {
    }
}
