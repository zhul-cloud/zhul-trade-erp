package com.zhul.erp.modules.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.constants.RelationshipConfidence;
import com.zhul.erp.modules.product.constants.RelationshipType;
import com.zhul.erp.modules.product.dto.RelationshipVO;
import com.zhul.erp.modules.product.dto.SaveRelationshipRequest;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductRelationshipDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.repository.ProductRelationshipMapper;
import com.zhul.erp.modules.product.service.ProductRelationshipService;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.ProductFinder;
import com.zhul.erp.modules.product.support.TextRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductRelationshipServiceImpl implements ProductRelationshipService {

    private static final int MPN_MAX = 128;

    private final ProductRelationshipMapper relationshipMapper;
    private final ProductMapper productMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductFinder productFinder;
    private final PlatformScopeGuard platformScopeGuard;

    /** 校验并解析后的入参 */
    private record Resolved(String relatedMpn, Long relatedProductId, int type, int confidence, String note,
                            String verifiedBy, LocalDateTime verifiedAt, Integer sortOrder) {
    }

    @Override
    public List<RelationshipVO> list(Long productId) {
        productFinder.active(productId);
        List<ProductRelationshipDO> rows = activeRows(productId);
        return toVOs(rows);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RelationshipVO create(Long productId, SaveRelationshipRequest req) {
        platformScopeGuard.requirePlatform();
        ProductDO self = productFinder.active(productId);
        Resolved in = resolve(self, req, null);
        boolean reverse = Boolean.TRUE.equals(req.getCreateReverse()) && RelationshipType.isSymmetric(in.type());

        if (reverse) {
            if (in.relatedProductId() == null) {
                throw BizException.of(ProductErrorCodes.RELATIONSHIP_INVALID, "创建反向关系要求关联型号是目录内的商品");
            }
            // 两端都加锁；按 ID 从小到大加锁，避免 A→B 与 B→A 同时创建时死锁
            productFinder.lockActive(Math.min(productId, in.relatedProductId()));
            productFinder.lockActive(Math.max(productId, in.relatedProductId()));
        } else {
            productFinder.lockActive(productId);
        }

        assertNotDuplicate(productId, in.relatedMpn(), in.type(), null);
        ProductRelationshipDO forward = newRow(productId, in.relatedMpn(), in.relatedProductId(), in);
        relationshipMapper.insert(forward);

        if (reverse) {
            // 反向重复会抛出 BizException，事务整体回滚，正向关系也不保存
            assertNotDuplicate(in.relatedProductId(), self.getMpnRaw(), in.type(), null);
            relationshipMapper.insert(newRow(in.relatedProductId(), self.getMpnRaw(), productId, in));
        }
        return toVOs(List.of(forward)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RelationshipVO update(Long productId, Long relationshipId, SaveRelationshipRequest req) {
        platformScopeGuard.requirePlatform();
        ProductDO self = productFinder.lockActive(productId);
        ProductRelationshipDO current = ownedRow(productId, relationshipId);
        Resolved in = resolve(self, req, current);
        assertNotDuplicate(productId, in.relatedMpn(), in.type(), relationshipId);

        // related_product_id、verified_at 可能要清空为 NULL，实体更新会跳过 null，所以单独用 set 写
        ProductRelationshipDO change = new ProductRelationshipDO();
        change.setRelatedMpn(in.relatedMpn());
        change.setRelationshipType(in.type());
        change.setConfidence(in.confidence());
        change.setNote(in.note());
        change.setVerifiedBy(in.verifiedBy());
        change.setSortOrder(in.sortOrder() == null ? current.getSortOrder() : in.sortOrder());
        relationshipMapper.update(change, new LambdaUpdateWrapper<ProductRelationshipDO>()
                .eq(ProductRelationshipDO::getId, relationshipId)
                .set(ProductRelationshipDO::getRelatedProductId, in.relatedProductId())
                .set(ProductRelationshipDO::getVerifiedAt, in.verifiedAt()));
        return toVOs(List.of(ownedRow(productId, relationshipId))).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long productId, Long relationshipId) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);
        ownedRow(productId, relationshipId);
        ProductRelationshipDO change = new ProductRelationshipDO();
        change.setId(relationshipId);
        change.setDeletedAt(LocalDateTime.now());
        relationshipMapper.updateById(change);
    }

    // ---------- 校验与解析 ----------

    private Resolved resolve(ProductDO self, SaveRelationshipRequest req, ProductRelationshipDO current) {
        if (!RelationshipType.isValid(req.getRelationshipType())) {
            throw BizException.of(ProductErrorCodes.RELATIONSHIP_INVALID, "关系类型不合法");
        }
        int type = req.getRelationshipType();
        Integer requested = req.getConfidence();
        if (requested != null && !RelationshipConfidence.isValid(requested)) {
            throw BizException.of(ProductErrorCodes.RELATIONSHIP_INVALID, "置信度不合法");
        }
        int confidence = requested != null ? requested
                : (current != null ? current.getConfidence() : RelationshipConfidence.MEDIUM);
        String note = TextRules.optional(req.getNote(), "关系说明", 500);
        String verifiedBy = TextRules.optional(req.getVerifiedBy(), "核实人", 32);
        LocalDateTime verifiedAt = null;
        if (confidence == RelationshipConfidence.VERIFIED) {
            if (verifiedBy.isEmpty()) {
                throw BizException.of(ProductErrorCodes.RELATIONSHIP_INVALID, "置信度为「已验证」时必须填写核实人");
            }
            verifiedAt = LocalDateTime.now();
        }

        String relatedMpn = TextRules.optional(req.getRelatedMpn(), "关联型号", MPN_MAX);
        ProductDO related = null;
        if (req.getRelatedProductId() != null) {
            related = productMapper.selectOne(new LambdaQueryWrapper<ProductDO>()
                    .eq(ProductDO::getId, req.getRelatedProductId())
                    .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                    .isNull(ProductDO::getDeletedAt));
            if (related == null) {
                throw BizException.of(ProductErrorCodes.RELATIONSHIP_INVALID, "所选关联商品不存在");
            }
            if (relatedMpn.isEmpty()) {
                relatedMpn = related.getMpnRaw();
            }
        }
        String normalized = MpnNormalizer.normalize(relatedMpn);
        if (normalized.isEmpty()) {
            throw BizException.of(ProductErrorCodes.RELATIONSHIP_INVALID, "请填写关联型号，至少包含一个字母或数字");
        }
        if (normalized.equals(self.getMpnNormalized()) || (related != null && related.getId().equals(self.getId()))) {
            throw BizException.of(ProductErrorCodes.RELATIONSHIP_INVALID, "不能关联商品自己");
        }

        Long relatedId = related == null ? null : related.getId();
        if (related == null) {
            // 型号原文恰好只命中目录中一个商品时自动关联；命中多个品牌则不关联，由用户明确选择
            List<ProductDO> matches = productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                    .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                    .eq(ProductDO::getMpnNormalized, normalized)
                    .isNull(ProductDO::getDeletedAt)
                    .ne(ProductDO::getId, self.getId())
                    .last("LIMIT 2"));
            if (matches.size() == 1) {
                related = matches.get(0);
                relatedId = related.getId();
            }
        }
        return new Resolved(relatedMpn, relatedId, type, confidence, note, verifiedBy, verifiedAt, req.getSortOrder());
    }

    /** 同一商品下（关联型号归一化值 + 关系类型）不可重复，只比较未删除的行 */
    private void assertNotDuplicate(Long productId, String relatedMpn, int type, Long excludeId) {
        String normalized = MpnNormalizer.normalize(relatedMpn);
        for (ProductRelationshipDO row : activeRows(productId)) {
            if (Objects.equals(row.getId(), excludeId)) {
                continue;
            }
            if (row.getRelationshipType() == type && MpnNormalizer.normalize(row.getRelatedMpn()).equals(normalized)) {
                throw BizException.of(ProductErrorCodes.RELATIONSHIP_DUPLICATE, "该关系已存在",
                        Map.of("existingId", row.getId()));
            }
        }
    }

    private ProductRelationshipDO newRow(Long productId, String relatedMpn, Long relatedProductId, Resolved in) {
        ProductRelationshipDO row = new ProductRelationshipDO();
        row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
        row.setProductId(productId);
        row.setRelatedMpn(relatedMpn);
        row.setRelatedProductId(relatedProductId);
        row.setRelationshipType(in.type());
        row.setConfidence(in.confidence());
        row.setNote(in.note());
        row.setVerifiedBy(in.verifiedBy());
        row.setVerifiedAt(in.verifiedAt());
        row.setSortOrder(in.sortOrder() == null ? 0 : in.sortOrder());
        return row;
    }

    private List<ProductRelationshipDO> activeRows(Long productId) {
        return relationshipMapper.selectList(new LambdaQueryWrapper<ProductRelationshipDO>()
                .eq(ProductRelationshipDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductRelationshipDO::getProductId, productId)
                .isNull(ProductRelationshipDO::getDeletedAt)
                .orderByAsc(ProductRelationshipDO::getSortOrder)
                .orderByAsc(ProductRelationshipDO::getId));
    }

    /** 关系必须属于这个商品且未删除，防止用别的商品的关系 ID 越权修改 */
    private ProductRelationshipDO ownedRow(Long productId, Long relationshipId) {
        ProductRelationshipDO row = relationshipId == null ? null
                : relationshipMapper.selectOne(new LambdaQueryWrapper<ProductRelationshipDO>()
                .eq(ProductRelationshipDO::getId, relationshipId)
                .eq(ProductRelationshipDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductRelationshipDO::getProductId, productId)
                .isNull(ProductRelationshipDO::getDeletedAt));
        if (row == null) {
            throw BizException.of(ProductErrorCodes.CONTENT_NOT_FOUND, "型号关系不存在");
        }
        return row;
    }

    private List<RelationshipVO> toVOs(List<ProductRelationshipDO> rows) {
        Set<Long> relatedIds = rows.stream().map(ProductRelationshipDO::getRelatedProductId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        Map<Long, ProductDO> products = new HashMap<>();
        Map<Long, String> brandNames = new HashMap<>();
        if (!relatedIds.isEmpty()) {
            for (ProductDO p : productMapper.selectBatchIds(relatedIds)) {
                products.put(p.getId(), p);
            }
            Set<Long> brandIds = products.values().stream().map(ProductDO::getBrandId).collect(Collectors.toSet());
            for (ProductBrandDO b : brandMapper.selectBatchIds(brandIds)) {
                brandNames.put(b.getId(), b.getBrandName());
            }
        }
        List<RelationshipVO> result = new ArrayList<>(rows.size());
        for (ProductRelationshipDO row : rows) {
            RelationshipVO vo = new RelationshipVO();
            vo.setId(row.getId());
            vo.setProductId(row.getProductId());
            vo.setRelatedMpn(row.getRelatedMpn());
            vo.setRelatedProductId(row.getRelatedProductId());
            ProductDO related = row.getRelatedProductId() == null ? null : products.get(row.getRelatedProductId());
            if (related != null) {
                vo.setRelatedProductDisplay(related.getMpnDisplay());
                vo.setRelatedBrandName(brandNames.get(related.getBrandId()));
            }
            vo.setRelationshipType(row.getRelationshipType());
            vo.setConfidence(row.getConfidence());
            vo.setNote(row.getNote());
            vo.setVerifiedBy(row.getVerifiedBy());
            vo.setVerifiedAt(row.getVerifiedAt());
            vo.setSortOrder(row.getSortOrder());
            result.add(vo);
        }
        return result;
    }
}
