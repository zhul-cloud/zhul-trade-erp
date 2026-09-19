package com.zhul.erp.modules.product.support;

/**
 * 商品被业务单据引用次数的扩展点（design.md 决策 6）。依赖方向是业务模块 → 商品模块：
 * 询盘、报价、订单等模块各自实现并注册为 Spring Bean，商品模块汇总所有实现的结果来决定能否删除、能否改型号。
 * 本期没有任何实现，引用次数恒为 0。
 */
public interface ProductUsageChecker {

    /** 该商品被本模块的单据引用了多少次 */
    long countUsage(Long productId);
}
