package com.zhul.erp.modules.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.ApplicationVO;
import com.zhul.erp.modules.product.dto.SaveApplicationRequest;
import com.zhul.erp.modules.product.entity.ProductApplicationDO;
import com.zhul.erp.modules.product.repository.ProductApplicationMapper;
import com.zhul.erp.modules.product.service.ProductApplicationService;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.ProductFinder;
import com.zhul.erp.modules.product.support.TextRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductApplicationServiceImpl implements ProductApplicationService {

    private final ProductApplicationMapper applicationMapper;
    private final ProductFinder productFinder;
    private final PlatformScopeGuard platformScopeGuard;

    @Override
    public List<ApplicationVO> list(Long productId) {
        productFinder.active(productId);
        List<ApplicationVO> result = new ArrayList<>();
        for (ProductApplicationDO row : activeRows(productId)) {
            result.add(toVO(row));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationVO create(Long productId, SaveApplicationRequest req) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);
        String title = TextRules.required(req.getTitle(), "应用场景标题", 64);
        assertTitleFree(productId, title, null);

        ProductApplicationDO row = new ProductApplicationDO();
        row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
        row.setProductId(productId);
        row.setTitle(title);
        row.setDescription(TextRules.optional(req.getDescription(), "场景说明", 500));
        row.setIcon(TextRules.optional(req.getIcon(), "图标", 16));
        row.setVerified(verified(req.getVerified()));
        row.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        applicationMapper.insert(row);
        return toVO(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ApplicationVO update(Long productId, Long itemId, SaveApplicationRequest req) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);
        ProductApplicationDO current = owned(productId, itemId);
        String title = TextRules.required(req.getTitle(), "应用场景标题", 64);
        assertTitleFree(productId, title, itemId);

        ProductApplicationDO change = new ProductApplicationDO();
        change.setId(itemId);
        change.setTitle(title);
        change.setDescription(TextRules.optional(req.getDescription(), "场景说明", 500));
        change.setIcon(TextRules.optional(req.getIcon(), "图标", 16));
        change.setVerified(req.getVerified() == null ? current.getVerified() : verified(req.getVerified()));
        change.setSortOrder(req.getSortOrder() == null ? current.getSortOrder() : req.getSortOrder());
        applicationMapper.updateById(change);
        return toVO(owned(productId, itemId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long productId, Long itemId) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);
        owned(productId, itemId);
        ProductApplicationDO change = new ProductApplicationDO();
        change.setId(itemId);
        change.setDeletedAt(LocalDateTime.now());
        applicationMapper.updateById(change);
    }

    private static int verified(Integer value) {
        int v = value == null ? 0 : value;
        if (v != 0 && v != 1) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "是否已核实只能是 0 或 1");
        }
        return v;
    }

    /** 同一商品下标题忽略大小写和首尾空格不可重复（标题已在入口去过首尾空格） */
    private void assertTitleFree(Long productId, String title, Long excludeId) {
        String normalized = title.toLowerCase(Locale.ROOT);
        for (ProductApplicationDO row : activeRows(productId)) {
            if (!Objects.equals(row.getId(), excludeId)
                    && row.getTitle().trim().toLowerCase(Locale.ROOT).equals(normalized)) {
                throw BizException.of(ProductErrorCodes.CONTENT_DUPLICATE, "该应用场景已存在",
                        Map.of("existingId", row.getId()));
            }
        }
    }

    private List<ProductApplicationDO> activeRows(Long productId) {
        return applicationMapper.selectList(new LambdaQueryWrapper<ProductApplicationDO>()
                .eq(ProductApplicationDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductApplicationDO::getProductId, productId)
                .isNull(ProductApplicationDO::getDeletedAt)
                .orderByAsc(ProductApplicationDO::getSortOrder)
                .orderByAsc(ProductApplicationDO::getId));
    }

    private ProductApplicationDO owned(Long productId, Long itemId) {
        ProductApplicationDO row = itemId == null ? null : applicationMapper.selectOne(
                new LambdaQueryWrapper<ProductApplicationDO>()
                        .eq(ProductApplicationDO::getId, itemId)
                        .eq(ProductApplicationDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .eq(ProductApplicationDO::getProductId, productId)
                        .isNull(ProductApplicationDO::getDeletedAt));
        if (row == null) {
            throw BizException.of(ProductErrorCodes.CONTENT_NOT_FOUND, "应用场景不存在");
        }
        return row;
    }

    private static ApplicationVO toVO(ProductApplicationDO row) {
        ApplicationVO vo = new ApplicationVO();
        vo.setId(row.getId());
        vo.setProductId(row.getProductId());
        vo.setTitle(row.getTitle());
        vo.setDescription(row.getDescription());
        vo.setIcon(row.getIcon());
        vo.setVerified(row.getVerified());
        vo.setSortOrder(row.getSortOrder());
        return vo;
    }
}
