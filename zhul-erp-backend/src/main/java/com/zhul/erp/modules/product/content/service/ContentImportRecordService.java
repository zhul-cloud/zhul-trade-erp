package com.zhul.erp.modules.product.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.modules.product.content.dto.ContentImportRecordVO;
import com.zhul.erp.modules.product.content.entity.ProductContentImportDO;
import com.zhul.erp.modules.product.content.repository.ProductContentImportMapper;
import com.zhul.erp.modules.product.support.ProductFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * 上传记录（spec product/product-content「上传记录」）：确认写入的文件。
 * 本公司的记录都能看；资料包写的是共享商品库，其他公司的资料包记录也能看（不显示确认人）；其他公司的营销包看不到。
 */
@Service
@RequiredArgsConstructor
public class ContentImportRecordService {

    private final ProductContentImportMapper importMapper;
    private final ProductFinder productFinder;

    /** 某商品的上传记录，按确认时间倒序（不含原文） */
    public List<ContentImportRecordVO> list(Long productId) {
        productFinder.active(productId);
        Integer tenantId = TenantContext.getTenantId();
        return importMapper.selectList(new LambdaQueryWrapper<ProductContentImportDO>()
                        .select(ProductContentImportDO::getId, ProductContentImportDO::getTenantId,
                                ProductContentImportDO::getFileName, ProductContentImportDO::getPackageType,
                                ProductContentImportDO::getPlatform, ProductContentImportDO::getLang,
                                ProductContentImportDO::getSummary, ProductContentImportDO::getConfirmedBy,
                                ProductContentImportDO::getCreateTime)
                        .eq(ProductContentImportDO::getProductId, productId)
                        .isNull(ProductContentImportDO::getDeletedAt)
                        .and(w -> w.eq(ProductContentImportDO::getTenantId, tenantId)
                                .or().eq(ProductContentImportDO::getPackageType, ContentConstants.TYPE_FACTS))
                        .orderByDesc(ProductContentImportDO::getCreateTime)
                        .orderByDesc(ProductContentImportDO::getId))
                .stream().map(r -> toVO(r, tenantId, false)).toList();
    }

    /** 单条记录与原文；看不到的记录按不存在处理 */
    public ContentImportRecordVO detail(Long id) {
        ProductContentImportDO r = importMapper.selectById(id);
        Integer tenantId = TenantContext.getTenantId();
        if (r == null || r.getDeletedAt() != null
                || !Objects.equals(r.getTenantId(), tenantId) && !ContentConstants.TYPE_FACTS.equals(r.getPackageType())) {
            throw BizException.of(ProductErrorCodes.CONTENT_NOT_FOUND, "上传记录不存在");
        }
        return toVO(r, tenantId, true);
    }

    private static ContentImportRecordVO toVO(ProductContentImportDO r, Integer tenantId, boolean withRaw) {
        ContentImportRecordVO vo = new ContentImportRecordVO();
        boolean mine = Objects.equals(r.getTenantId(), tenantId);
        vo.setId(r.getId());
        vo.setFileName(r.getFileName());
        vo.setPackageType(r.getPackageType());
        vo.setPlatform(r.getPlatform());
        vo.setLang(r.getLang());
        vo.setSummary(r.getSummary());
        vo.setConfirmedBy(mine ? r.getConfirmedBy() : null);
        vo.setConfirmedAt(r.getCreateTime());
        vo.setMine(mine);
        if (withRaw) {
            vo.setRawText(r.getRawText());
        }
        return vo;
    }
}
