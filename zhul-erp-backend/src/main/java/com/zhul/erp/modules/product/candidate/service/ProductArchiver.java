package com.zhul.erp.modules.product.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.repository.SourcingQuoteMapper;
import com.zhul.erp.modules.product.candidate.constants.CandidateConstants;
import com.zhul.erp.modules.product.candidate.entity.ProductCandidateDO;
import com.zhul.erp.modules.product.candidate.entity.ProductCandidateSourceDO;
import com.zhul.erp.modules.product.candidate.repository.ProductCandidateMapper;
import com.zhul.erp.modules.product.candidate.repository.ProductCandidateSourceMapper;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.candidate.support.ModelRules;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 询盘型号自动建档（spec inquiry/inquiry-intake「确认询盘时自动建档」、product/product-candidate）。
 * {@link #sync} 按询盘型号的当前状态重算：真实型号优先，否则确认型号 → 商品库已有则关联；没有且像型号则进候选池，
 * 来源按当前事实重建（询盘或回填真实型号；有提交的有货回价加「采购回价有货」；有有效订单加「成交」）；不像型号则待回填。
 * 重算是幂等的，询盘确认、改真实型号、提交回价、订单生成、历史补齐都调用它；须在调用方事务内执行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductArchiver {

    private static final int TEXT_MAX = 500;
    private static final int NAME_MAX = 255;

    private final InquiryItemMapper itemMapper;
    private final SourcingQuoteMapper quoteMapper;
    private final SalesOrderItemMapper soItemMapper;
    private final SalesOrderMapper soMapper;
    private final ProductMapper productMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductCandidateMapper candidateMapper;
    private final ProductCandidateSourceMapper sourceMapper;

    /** 重算这些询盘型号的建档状态与候选来源 */
    public void sync(Collection<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) {
            return;
        }
        List<InquiryItemDO> items = itemMapper.selectBatchIds(new LinkedHashSet<>(itemIds)).stream()
                .filter(i -> i.getDeletedAt() == null).sorted(Comparator.comparing(InquiryItemDO::getId)).toList();
        if (items.isEmpty()) {
            return;
        }
        List<ProductCategoryDO> categories = categoryMapper.selectList(new LambdaQueryWrapper<ProductCategoryDO>()
                .eq(ProductCategoryDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductCategoryDO::getStatus, ProductConstants.STATUS_ENABLED)
                .isNull(ProductCategoryDO::getDeletedAt));
        Set<Long> touched = new HashSet<>();
        for (InquiryItemDO item : items) {
            touched.addAll(syncOne(item, categories));
        }
        touched.forEach(this::refresh);
    }

    /** 返回受影响（来源有增减）的候选 ID */
    private Set<Long> syncOne(InquiryItemDO item, List<ProductCategoryDO> categories) {
        boolean actual = StringUtils.hasText(item.getActualModel());
        String model = actual ? item.getActualModel().trim() : nz(item.getConfirmedModel()).trim();
        String key = actual ? (StringUtils.hasText(item.getActualModelKey()) ? item.getActualModelKey() : MpnNormalizer.normalize(model))
                : (StringUtils.hasText(item.getModelKey()) ? item.getModelKey() : MpnNormalizer.normalize(model));
        Set<Long> touched = new HashSet<>();
        // 商品库已有：直接关联
        ProductDO product = item.getBrandId() == null || key.isEmpty() ? null : productMapper.selectOne(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductDO::getBrandId, item.getBrandId())
                .eq(ProductDO::getMpnNormalized, key)
                .eq(ProductDO::getStatus, ProductConstants.STATUS_ENABLED)
                .isNull(ProductDO::getDeletedAt)
                .last("LIMIT 1"));
        if (product != null) {
            touched.addAll(dropSources(item.getId(), null));
            mark(item.getId(), product.getId(), CandidateConstants.ARCHIVE_DONE);
            return touched;
        }
        if (key.isEmpty() || !ModelRules.looksLikeModel(model)) {
            touched.addAll(dropSources(item.getId(), null));
            mark(item.getId(), null, CandidateConstants.ARCHIVE_NEED_MODEL);
            return touched;
        }
        ProductCandidateDO c = upsert(item, model, key, categories);
        touched.add(c.getId());
        touched.addAll(dropSources(item.getId(), c.getId()));
        writeSources(c, item, actual);
        if (c.getStatus() == CandidateConstants.STATUS_ARCHIVED || c.getStatus() == CandidateConstants.STATUS_MERGED) {
            mark(item.getId(), c.getProductId(), CandidateConstants.ARCHIVE_DONE);
        } else if (c.getStatus() == CandidateConstants.STATUS_REJECTED) {
            mark(item.getId(), null, CandidateConstants.ARCHIVE_NEED_MODEL);
        } else {
            mark(item.getId(), null, CandidateConstants.ARCHIVE_CANDIDATE);
        }
        return touched;
    }

    private ProductCandidateDO upsert(InquiryItemDO item, String model, String key, List<ProductCategoryDO> categories) {
        String brandKey = nz(item.getBrandKey());
        candidateMapper.lockByKey(brandKey, key);
        ProductCandidateDO c = byKey(brandKey, key);
        if (c != null) {
            if (c.getDeletedAt() != null) {
                // 曾因没有来源被软删除的候选重新出现：恢复为待审核（updateById 不写 null，要显式更新）
                candidateMapper.update(null, new LambdaUpdateWrapper<ProductCandidateDO>()
                        .eq(ProductCandidateDO::getId, c.getId())
                        .set(ProductCandidateDO::getDeletedAt, null)
                        .set(ProductCandidateDO::getStatus, CandidateConstants.STATUS_PENDING));
                c.setDeletedAt(null);
                c.setStatus(CandidateConstants.STATUS_PENDING);
            }
            return c;
        }
        c = new ProductCandidateDO();
        c.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
        c.setBrandId(item.getBrandId());
        c.setBrandText(cut(nz(item.getBrand()).trim(), 64));
        c.setBrandKey(brandKey);
        c.setMpnRaw(cut(model, 128));
        c.setMpnNormalized(key);
        c.setCategoryText(cut(nz(item.getCategory()).trim(), 64));
        c.setCategoryId(suggestCategory(item.getCategory(), categories));
        String en = nz(item.getDescriptionEn()).trim();
        c.setProductName(cut(en.isEmpty() ? nz(item.getDescription()).trim() : en, NAME_MAX));
        c.setDescription(cut(nz(item.getDescription()).trim(), TEXT_MAX));
        c.setDescriptionEn(cut(en, TEXT_MAX));
        c.setStatus(CandidateConstants.STATUS_PENDING);
        c.setLevel(CandidateConstants.LEVEL_INQUIRY);
        c.setSourceCount(0);
        c.setRejectReason(0);
        c.setRejectNote("");
        try {
            candidateMapper.insert(c);
        } catch (DuplicateKeyException e) {
            // 并发进池：唯一键兜底，取已插入的那一行
            c = byKey(brandKey, key);
        }
        return c;
    }

    private ProductCandidateDO byKey(String brandKey, String key) {
        return candidateMapper.selectOne(new LambdaQueryWrapper<ProductCandidateDO>()
                .eq(ProductCandidateDO::getBrandKey, brandKey)
                .eq(ProductCandidateDO::getMpnNormalized, key));
    }

    /** 按当前事实写这个询盘型号在候选上的来源：缺的补、多的删 */
    private void writeSources(ProductCandidateDO c, InquiryItemDO item, boolean actual) {
        Map<String, Long> wanted = new HashMap<>();
        wanted.put(sourceKey(actual ? CandidateConstants.SOURCE_ACTUAL_MODEL : CandidateConstants.SOURCE_INQUIRY, null), null);
        boolean inStock = quoteMapper.selectCount(new LambdaQueryWrapper<SourcingQuoteDO>()
                .eq(SourcingQuoteDO::getInquiryItemId, item.getId())
                .in(SourcingQuoteDO::getStatus, InquiryConstants.QUOTE_SUBMITTED, InquiryConstants.QUOTE_PENDING_REVIEW)
                .eq(SourcingQuoteDO::getNoStock, 0)
                .isNull(SourcingQuoteDO::getDeletedAt)) > 0;
        if (inStock) {
            wanted.put(sourceKey(CandidateConstants.SOURCE_IN_STOCK, null), null);
        }
        for (Long soId : dealOrders(item.getId())) {
            wanted.put(sourceKey(CandidateConstants.SOURCE_DEAL, soId), soId);
        }
        List<ProductCandidateSourceDO> existing = sourceMapper.selectList(new LambdaQueryWrapper<ProductCandidateSourceDO>()
                .eq(ProductCandidateSourceDO::getCandidateId, c.getId())
                .eq(ProductCandidateSourceDO::getInquiryItemId, item.getId())
                .isNull(ProductCandidateSourceDO::getDeletedAt));
        LocalDateTime now = LocalDateTime.now();
        for (ProductCandidateSourceDO s : existing) {
            String k = sourceKey(s.getSourceType(), s.getSoId());
            if (wanted.containsKey(k)) {
                wanted.remove(k);
            } else {
                s.setDeletedAt(now);
                sourceMapper.updateById(s);
            }
        }
        for (Map.Entry<String, Long> e : wanted.entrySet()) {
            ProductCandidateSourceDO s = new ProductCandidateSourceDO();
            s.setTenantId(item.getTenantId());
            s.setCandidateId(c.getId());
            s.setSourceType(Integer.parseInt(e.getKey().split(":")[0]));
            s.setCustomerInquiryId(item.getCustomerInquiryId());
            s.setInquiryItemId(item.getId());
            s.setSoId(e.getValue());
            sourceMapper.insert(s);
        }
    }

    private static String sourceKey(Integer type, Long soId) {
        return type + ":" + (soId == null ? "" : soId);
    }

    /** 引用这个询盘型号、且订单有效的销售订单 */
    private List<Long> dealOrders(Long itemId) {
        Set<Long> soIds = soItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                        .select(SalesOrderItemDO::getSoId)
                        .eq(SalesOrderItemDO::getInquiryItemId, itemId)
                        .isNull(SalesOrderItemDO::getDeletedAt))
                .stream().map(SalesOrderItemDO::getSoId).collect(Collectors.toSet());
        if (soIds.isEmpty()) {
            return List.of();
        }
        return soMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                        .select(SalesOrderDO::getId)
                        .in(SalesOrderDO::getId, soIds)
                        .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                        .isNull(SalesOrderDO::getDeletedAt))
                .stream().map(SalesOrderDO::getId).sorted().toList();
    }

    /** 删除这个询盘型号在其他候选（keep 之外）上的来源，返回受影响的候选 */
    private Set<Long> dropSources(Long itemId, Long keep) {
        List<ProductCandidateSourceDO> rows = sourceMapper.selectList(new LambdaQueryWrapper<ProductCandidateSourceDO>()
                .eq(ProductCandidateSourceDO::getInquiryItemId, itemId)
                .ne(keep != null, ProductCandidateSourceDO::getCandidateId, keep)
                .isNull(ProductCandidateSourceDO::getDeletedAt));
        LocalDateTime now = LocalDateTime.now();
        Set<Long> out = new HashSet<>();
        for (ProductCandidateSourceDO s : rows) {
            s.setDeletedAt(now);
            sourceMapper.updateById(s);
            out.add(s.getCandidateId());
        }
        return out;
    }

    /** 重算候选的来源数、可信度与出现时间；待审核且没有来源的软删除 */
    public void refresh(Long candidateId) {
        ProductCandidateDO c = candidateMapper.selectById(candidateId);
        if (c == null || c.getDeletedAt() != null) {
            return;
        }
        List<ProductCandidateSourceDO> live = sourceMapper.selectList(new LambdaQueryWrapper<ProductCandidateSourceDO>()
                .eq(ProductCandidateSourceDO::getCandidateId, candidateId)
                .isNull(ProductCandidateSourceDO::getDeletedAt));
        if (live.isEmpty() && c.getStatus() == CandidateConstants.STATUS_PENDING) {
            c.setDeletedAt(LocalDateTime.now());
            candidateMapper.updateById(c);
            return;
        }
        c.setSourceCount(live.size());
        c.setLevel(live.stream().mapToInt(s -> CandidateConstants.levelOf(s.getSourceType())).max().orElse(CandidateConstants.LEVEL_INQUIRY));
        c.setFirstSeenAt(live.stream().map(ProductCandidateSourceDO::getCreateTime).filter(Objects::nonNull).min(Comparator.naturalOrder())
                .orElse(c.getFirstSeenAt()));
        c.setLastSeenAt(live.stream().map(ProductCandidateSourceDO::getCreateTime).filter(Objects::nonNull).max(Comparator.naturalOrder())
                .orElse(c.getLastSeenAt()));
        candidateMapper.updateById(c);
    }

    /** 候选建档或并入后：全部来源的询盘型号关联到商品 */
    public void linkSources(Long candidateId, Long productId) {
        Set<Long> itemIds = sourceMapper.selectList(new LambdaQueryWrapper<ProductCandidateSourceDO>()
                        .select(ProductCandidateSourceDO::getInquiryItemId)
                        .eq(ProductCandidateSourceDO::getCandidateId, candidateId)
                        .isNotNull(ProductCandidateSourceDO::getInquiryItemId)
                        .isNull(ProductCandidateSourceDO::getDeletedAt))
                .stream().map(ProductCandidateSourceDO::getInquiryItemId).collect(Collectors.toSet());
        if (!itemIds.isEmpty()) {
            itemMapper.update(null, new LambdaUpdateWrapper<InquiryItemDO>()
                    .in(InquiryItemDO::getId, itemIds)
                    .set(InquiryItemDO::getProductId, productId)
                    .set(InquiryItemDO::getArchiveStatus, CandidateConstants.ARCHIVE_DONE)
                    .set(InquiryItemDO::getUpdateTime, LocalDateTime.now()));
        }
    }

    /** 候选被驳回：来源询盘型号回到待回填真实型号 */
    public void unlinkRejected(Long candidateId) {
        Set<Long> itemIds = sourceMapper.selectList(new LambdaQueryWrapper<ProductCandidateSourceDO>()
                        .select(ProductCandidateSourceDO::getInquiryItemId)
                        .eq(ProductCandidateSourceDO::getCandidateId, candidateId)
                        .isNotNull(ProductCandidateSourceDO::getInquiryItemId)
                        .isNull(ProductCandidateSourceDO::getDeletedAt))
                .stream().map(ProductCandidateSourceDO::getInquiryItemId).collect(Collectors.toSet());
        if (!itemIds.isEmpty()) {
            itemMapper.update(null, new LambdaUpdateWrapper<InquiryItemDO>()
                    .in(InquiryItemDO::getId, itemIds)
                    .set(InquiryItemDO::getProductId, null)
                    .set(InquiryItemDO::getArchiveStatus, CandidateConstants.ARCHIVE_NEED_MODEL)
                    .set(InquiryItemDO::getUpdateTime, LocalDateTime.now()));
        }
    }

    private void mark(Long itemId, Long productId, int status) {
        itemMapper.update(null, new LambdaUpdateWrapper<InquiryItemDO>()
                .eq(InquiryItemDO::getId, itemId)
                .set(InquiryItemDO::getProductId, productId)
                .set(InquiryItemDO::getArchiveStatus, status));
    }

    /**
     * 品类建议：询盘的品类文字与品类中文名、英文名、编码比较，先精确再包含；命中二级品类时取其一级品类（商品只能挂一级品类）。
     * 只有唯一结果时才建议。
     */
    static Long suggestCategory(String text, List<ProductCategoryDO> categories) {
        String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) {
            return null;
        }
        Map<Long, ProductCategoryDO> byId = categories.stream().collect(Collectors.toMap(ProductCategoryDO::getId, c -> c, (a, b) -> a));
        for (boolean exact : new boolean[] {true, false}) {
            Set<Long> hits = new LinkedHashSet<>();
            for (ProductCategoryDO c : categories) {
                for (String name : new String[] {c.getCategoryNameZh(), c.getCategoryName(), c.getCategoryCode()}) {
                    String n = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
                    if (n.isEmpty()) {
                        continue;
                    }
                    if (exact ? n.equals(t) : (n.contains(t) || t.contains(n))) {
                        ProductCategoryDO top = c.getParentId() == null ? c : byId.get(c.getParentId());
                        if (top != null && top.getParentId() == null) {
                            hits.add(top.getId());
                        }
                    }
                }
            }
            if (hits.size() == 1) {
                return hits.iterator().next();
            }
            if (hits.size() > 1) {
                return null;
            }
        }
        return null;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String cut(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
