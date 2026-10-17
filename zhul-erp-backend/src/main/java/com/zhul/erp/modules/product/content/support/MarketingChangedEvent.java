package com.zhul.erp.modules.product.content.support;

import java.util.Collection;

/**
 * 影响发品任务的营销变化：营销内容写入、资料核对、标记或撤销不发布、平台设置修改。
 * 同步发布，监听方在发布方的事务里处理。
 *
 * @param tenantId   公司；为空表示所有有该商品营销任务的公司（资料核对是共享操作）
 * @param productIds 商品；为空表示这家公司全部有营销写入的商品（平台设置修改）
 */
public record MarketingChangedEvent(Integer tenantId, Collection<Long> productIds) {
}
