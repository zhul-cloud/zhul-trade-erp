package com.zhul.erp.modules.product.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.product.candidate.entity.ProductCandidateDO;
import com.zhul.erp.modules.product.candidate.repository.ProductCandidateMapper;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 单据明细上的商品标记（spec product/document-product-link「单据上的商品标记」）：已并入的换成主商品；
 * 已建档的可跳商品详情；候选的标「候选」并带候选 ID 供审核跳转；已驳回或不存在的当作没有关联。
 */
@Component
@RequiredArgsConstructor
public class ProductTags {

    private final ProductMapper productMapper;
    private final ProductCandidateMapper candidateMapper;

    /** productId 为实际引用的商品（已并入的已换成主商品）；candidateId 只在候选时有值 */
    public record Tag(Long productId, boolean candidate, Long candidateId) {
    }

    /** 按引用的商品 ID 查标记；查不到的不在结果里 */
    public Map<Long, Tag> of(Collection<Long> productIds) {
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().toList();
        Map<Long, Tag> out = new HashMap<>();
        if (ids.isEmpty()) {
            return out;
        }
        Map<Long, ProductDO> rows = new HashMap<>();
        productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                        .select(ProductDO::getId, ProductDO::getArchiveStatus, ProductDO::getMergedIntoId)
                        .in(ProductDO::getId, ids))
                .forEach(p -> rows.put(p.getId(), p));
        List<Long> targets = rows.values().stream().map(ProductDO::getMergedIntoId).filter(Objects::nonNull)
                .filter(t -> !rows.containsKey(t)).distinct().toList();
        if (!targets.isEmpty()) {
            productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                            .select(ProductDO::getId, ProductDO::getArchiveStatus, ProductDO::getMergedIntoId)
                            .in(ProductDO::getId, targets))
                    .forEach(p -> rows.put(p.getId(), p));
        }
        List<Long> candidates = rows.values().stream()
                .filter(p -> Objects.equals(p.getArchiveStatus(), ProductConstants.ARCHIVE_CANDIDATE)).map(ProductDO::getId).toList();
        Map<Long, Long> candidateIds = new HashMap<>();
        if (!candidates.isEmpty()) {
            candidateMapper.selectList(new LambdaQueryWrapper<ProductCandidateDO>()
                            .select(ProductCandidateDO::getId, ProductCandidateDO::getProductId)
                            .in(ProductCandidateDO::getProductId, candidates)
                            .isNull(ProductCandidateDO::getDeletedAt))
                    .forEach(c -> candidateIds.putIfAbsent(c.getProductId(), c.getId()));
        }
        for (Long id : ids) {
            ProductDO p = rows.get(id);
            if (p != null && p.getMergedIntoId() != null) {
                p = rows.get(p.getMergedIntoId());
            }
            if (p == null) {
                continue;
            }
            int status = p.getArchiveStatus() == null ? ProductConstants.ARCHIVE_DONE : p.getArchiveStatus();
            if (status == ProductConstants.ARCHIVE_DONE) {
                out.put(id, new Tag(p.getId(), false, null));
            } else if (status == ProductConstants.ARCHIVE_CANDIDATE) {
                out.put(id, new Tag(p.getId(), true, candidateIds.get(p.getId())));
            }
        }
        return out;
    }
}
