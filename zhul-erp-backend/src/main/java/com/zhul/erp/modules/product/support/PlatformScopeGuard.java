package com.zhul.erp.modules.product.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import org.springframework.stereotype.Component;

/**
 * 商品主数据是平台共享数据，写操作除权限码外，还必须由平台账号（JWT 中 tenantId 为 0）执行
 * （design.md 决策 2）。这里是判定"是否平台账号"的唯一位置，识别规则日后变化只改这一处。
 * 唯一例外是商品候选审核：审核服务校验过权限与可见范围后，在 {@link #asCandidateReview} 范围内调用商品、品牌、系列的
 * 新建与添加别名，这些方法的校验照常执行，只放开「必须是平台账号」这一条（openspec add-product-candidate-pool）。
 */
@Component
public class PlatformScopeGuard {

    private static final ThreadLocal<Boolean> CANDIDATE_REVIEW = new ThreadLocal<>();

    /** 在候选审核范围内执行：允许租户账号新建商品、品牌、别名、系列 */
    public <T> T asCandidateReview(java.util.function.Supplier<T> action) {
        Boolean before = CANDIDATE_REVIEW.get();
        CANDIDATE_REVIEW.set(Boolean.TRUE);
        try {
            return action.get();
        } finally {
            if (before == null) {
                CANDIDATE_REVIEW.remove();
            } else {
                CANDIDATE_REVIEW.set(before);
            }
        }
    }

    public boolean isPlatform() {
        Integer tenantId = TenantContext.getTenantId();
        return tenantId != null && tenantId == ProductConstants.PLATFORM_TENANT_ID;
    }

    public void requirePlatform() {
        if (!isPlatform() && !Boolean.TRUE.equals(CANDIDATE_REVIEW.get())) {
            throw BizException.of(ProductErrorCodes.PLATFORM_ADMIN_REQUIRED, "仅平台账号可维护商品主数据");
        }
    }
}
