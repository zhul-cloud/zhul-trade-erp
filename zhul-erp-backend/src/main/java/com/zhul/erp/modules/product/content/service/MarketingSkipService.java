package com.zhul.erp.modules.product.content.service;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;
import com.zhul.erp.modules.product.content.support.MarketingChangedEvent;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.content.entity.ProductMarketingSkipDO;
import com.zhul.erp.modules.product.content.repository.ProductMarketingSkipMapper;
import com.zhul.erp.modules.product.content.support.MarketingPlatform;
import com.zhul.erp.modules.product.support.ProductFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品按平台不发布（spec product/product-content「商品按平台不发布」）：只影响本公司，记录标记人；
 * 撤销即软删除，再标记复用同一行并更新标记人。重复标记不报错，保留原标记人。
 */
@Service
@RequiredArgsConstructor
public class MarketingSkipService {

    private final ProductMarketingSkipMapper skipMapper;
    private final ApplicationEventPublisher events;
    private final ProductFinder productFinder;
    private final PlatformSettingService platformSettings;

    @Transactional(rollbackFor = Exception.class)
    public void mark(Long productId, String platformCode) {
        productFinder.active(productId);
        MarketingPlatform platform = platformSettings.requireEnabled(platformCode);
        Integer tenantId = TenantContext.getTenantId();
        ProductMarketingSkipDO row = skipMapper.selectOne(new LambdaQueryWrapper<ProductMarketingSkipDO>()
                .eq(ProductMarketingSkipDO::getTenantId, tenantId)
                .eq(ProductMarketingSkipDO::getProductId, productId)
                .eq(ProductMarketingSkipDO::getPlatform, platform.code()));
        LocalDateTime now = LocalDateTime.now();
        String user = SecurityUtils.getCurrentUsername();
        if (row == null) {
            row = new ProductMarketingSkipDO();
            row.setTenantId(tenantId);
            row.setProductId(productId);
            row.setPlatform(platform.code());
            row.setMarkedAt(now);
            row.setMarkedBy(user);
            try {
                skipMapper.insert(row);
            } catch (DuplicateKeyException e) {
                // 并发时另一个请求先标记了，结果相同
            }
            changed(productId);
            return;
        }
        if (row.getDeletedAt() == null) {
            return;
        }
        skipMapper.update(null, new LambdaUpdateWrapper<ProductMarketingSkipDO>()
                .eq(ProductMarketingSkipDO::getId, row.getId())
                .set(ProductMarketingSkipDO::getDeletedAt, null)
                .set(ProductMarketingSkipDO::getMarkedAt, now)
                .set(ProductMarketingSkipDO::getMarkedBy, user)
                .set(ProductMarketingSkipDO::getUpdateTime, now)
                .set(ProductMarketingSkipDO::getUpdateBy, user));
        changed(productId);
    }

    /** 不发布标记变了：重算本公司这个商品的发品任务 */
    private void changed(Long productId) {
        events.publishEvent(new MarketingChangedEvent(TenantContext.getTenantId(), List.of(productId)));
    }

    /** 撤销不发布；没有标记时什么也不做 */
    @Transactional(rollbackFor = Exception.class)
    public void unmark(Long productId, String platformCode) {
        productFinder.active(productId);
        MarketingPlatform platform = MarketingPlatform.of(platformCode);
        if (platform == null) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "平台须为 alibaba、mic、site 或 social");
        }
        LocalDateTime now = LocalDateTime.now();
        skipMapper.update(null, new LambdaUpdateWrapper<ProductMarketingSkipDO>()
                .eq(ProductMarketingSkipDO::getTenantId, TenantContext.getTenantId())
                .eq(ProductMarketingSkipDO::getProductId, productId)
                .eq(ProductMarketingSkipDO::getPlatform, platform.code())
                .isNull(ProductMarketingSkipDO::getDeletedAt)
                .set(ProductMarketingSkipDO::getDeletedAt, now)
                .set(ProductMarketingSkipDO::getUpdateTime, now)
                .set(ProductMarketingSkipDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
        changed(productId);
    }

    /** 本公司这些商品当前有效的不发布标记：商品 → 平台编码 → 标记 */
    public Map<Long, Map<String, ProductMarketingSkipDO>> skips(Collection<Long> productIds) {
        Map<Long, Map<String, ProductMarketingSkipDO>> result = new HashMap<>();
        if (productIds.isEmpty()) {
            return result;
        }
        List<ProductMarketingSkipDO> rows = skipMapper.selectList(new LambdaQueryWrapper<ProductMarketingSkipDO>()
                .eq(ProductMarketingSkipDO::getTenantId, TenantContext.getTenantId())
                .in(ProductMarketingSkipDO::getProductId, productIds)
                .isNull(ProductMarketingSkipDO::getDeletedAt));
        for (ProductMarketingSkipDO r : rows) {
            result.computeIfAbsent(r.getProductId(), k -> new HashMap<>()).put(r.getPlatform(), r);
        }
        return result;
    }
}
