package com.zhul.erp.modules.product.support;

import java.util.List;

/**
 * 商品品类已改（采纳 AI 建议或手动改品类），在改品类的事务内发布。
 * 关心商品品类的模块（如供应链按商品主数据补齐需求型号品类）在事务提交后监听，商品模块不依赖它们。
 */
public record ProductCategoryChangedEvent(List<Long> productIds) {
}
