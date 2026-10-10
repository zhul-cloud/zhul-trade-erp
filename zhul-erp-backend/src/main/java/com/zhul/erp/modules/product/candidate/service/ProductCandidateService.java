package com.zhul.erp.modules.product.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.product.candidate.constants.CandidateConstants;
import com.zhul.erp.modules.product.candidate.dto.ApproveCandidateRequest;
import com.zhul.erp.modules.product.candidate.dto.BatchApproveVO;
import com.zhul.erp.modules.product.candidate.dto.CandidatePageQuery;
import com.zhul.erp.modules.product.candidate.dto.CandidateVO;
import com.zhul.erp.modules.product.candidate.dto.RejectCandidateRequest;
import com.zhul.erp.modules.product.candidate.entity.ProductCandidateDO;
import com.zhul.erp.modules.product.candidate.entity.ProductCandidateSourceDO;
import com.zhul.erp.modules.product.candidate.repository.ProductCandidateMapper;
import com.zhul.erp.modules.product.candidate.repository.ProductCandidateSourceMapper;
import com.zhul.erp.modules.product.constants.LifecycleStatus;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.dto.CreateSeriesRequest;
import com.zhul.erp.modules.product.dto.SaveBrandRequest;
import com.zhul.erp.modules.product.dto.SaveProductRequest;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.service.BrandService;
import com.zhul.erp.modules.product.service.ProductService;
import com.zhul.erp.modules.product.service.SeriesService;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品候选审核（spec product/product-candidate）。可见范围：平台账号看全部；租户账号只看有本公司来源的候选，
 * 来源明细只给本公司的。事务边界：通过建档 = 品牌（新建或别名）+ 系列 + 商品 + 候选状态 + 来源询盘型号回填关联。
 */
@Service
@RequiredArgsConstructor
public class ProductCandidateService {

    private final ProductCandidateMapper candidateMapper;
    private final ProductCandidateSourceMapper sourceMapper;
    private final ProductArchiver archiver;
    private final ProductService productService;
    private final BrandService brandService;
    private final SeriesService seriesService;
    private final ProductMapper productMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductCategoryMapper categoryMapper;
    private final InquiryItemMapper itemMapper;
    private final CustomerInquiryMapper inquiryMapper;
    private final SalesOrderMapper soMapper;
    private final PlatformScopeGuard platformScopeGuard;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;

    // ---------------------------------------------------------------- 查询

    public PageResult<CandidateVO> page(CandidatePageQuery q) {
        int status = q.getStatus() == null ? CandidateConstants.STATUS_PENDING : q.getStatus();
        LambdaQueryWrapper<ProductCandidateDO> w = new LambdaQueryWrapper<ProductCandidateDO>()
                .eq(ProductCandidateDO::getStatus, status)
                .isNull(ProductCandidateDO::getDeletedAt);
        if (!platformScopeGuard.isPlatform()) {
            w.inSql(ProductCandidateDO::getId, "SELECT candidate_id FROM product_candidate_source WHERE deleted_at IS NULL AND tenant_id = "
                    + tenantId());
        }
        if (q.getLevel() != null) {
            w.eq(ProductCandidateDO::getLevel, q.getLevel());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            String key = MpnNormalizer.normalize(kw);
            w.and(x -> x.like(ProductCandidateDO::getMpnRaw, kw).or().like(ProductCandidateDO::getBrandText, kw)
                    .or().like(ProductCandidateDO::getProductName, kw)
                    .or(!key.isEmpty(), y -> y.like(ProductCandidateDO::getMpnNormalized, key)));
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = candidateMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        // 各页签都按可信度从高到低、再按更新时间倒序
        w.last("ORDER BY level DESC, update_time DESC, id DESC LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toVos(candidateMapper.selectList(w), false));
    }

    public Map<String, Long> counts() {
        Map<String, Long> out = new LinkedHashMap<>();
        LambdaQueryWrapper<ProductCandidateDO> w = new LambdaQueryWrapper<ProductCandidateDO>()
                .eq(ProductCandidateDO::getStatus, CandidateConstants.STATUS_PENDING)
                .isNull(ProductCandidateDO::getDeletedAt);
        if (!platformScopeGuard.isPlatform()) {
            w.inSql(ProductCandidateDO::getId, "SELECT candidate_id FROM product_candidate_source WHERE deleted_at IS NULL AND tenant_id = "
                    + tenantId());
        }
        out.put("pending", candidateMapper.selectCount(w));
        return out;
    }

    public CandidateVO detail(Long id) {
        return toVos(List.of(visible(id)), true).get(0);
    }

    // ---------------------------------------------------------------- 审核

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CandidateVO approve(Long id, ApproveCandidateRequest req) {
        ProductCandidateDO c = lockPending(id);
        Long productId = archive(c, req);
        logService.recordOperateLog(CandidateConstants.MENU, "通过建档", null, Map.of("candidate", label(c), "productId", productId));
        return detail(id);
    }

    /** 批量通过：品牌已识别、建议品类已确定的按候选现有内容建档，其余跳过 */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public BatchApproveVO batchApprove(List<Long> ids) {
        BatchApproveVO vo = new BatchApproveVO();
        List<BatchApproveVO.Skipped> skipped = new ArrayList<>();
        int approved = 0;
        for (Long id : ids.stream().distinct().sorted().toList()) {
            ProductCandidateDO c = candidateMapper.selectById(id);
            if (c == null || c.getDeletedAt() != null || !canSee(c.getId())) {
                skipped.add(new BatchApproveVO.Skipped(id, "", "候选不存在"));
                continue;
            }
            if (c.getStatus() != CandidateConstants.STATUS_PENDING) {
                skipped.add(new BatchApproveVO.Skipped(id, label(c), "已审核"));
                continue;
            }
            if (c.getBrandId() == null) {
                skipped.add(new BatchApproveVO.Skipped(id, label(c), "品牌未确认"));
                continue;
            }
            if (c.getCategoryId() == null) {
                skipped.add(new BatchApproveVO.Skipped(id, label(c), "品类未选"));
                continue;
            }
            candidateMapper.lockById(id);
            ApproveCandidateRequest req = new ApproveCandidateRequest();
            req.setBrandMode("EXISTING");
            req.setBrandId(c.getBrandId());
            req.setCategoryId(c.getCategoryId());
            archive(c, req);
            approved++;
        }
        vo.setApproved(approved);
        vo.setSkipped(skipped);
        logService.recordOperateLog(CandidateConstants.MENU, "批量通过", null, Map.of("approved", approved, "skipped", skipped.size()));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CandidateVO merge(Long id, Long productId) {
        ProductCandidateDO c = lockPending(id);
        ProductDO p = productMapper.selectById(productId);
        if (p == null || p.getDeletedAt() != null) {
            throw new BizException("商品不存在");
        }
        resolve(c, CandidateConstants.STATUS_MERGED, p.getId());
        logService.recordOperateLog(CandidateConstants.MENU, "并入已有商品", null, Map.of("candidate", label(c), "productId", productId));
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CandidateVO reject(Long id, RejectCandidateRequest req) {
        ProductCandidateDO c = lockPending(id);
        if (!CandidateConstants.REJECT_NAMES.containsKey(req.getReason())) {
            throw new BizException("请选择驳回原因");
        }
        String note = req.getNote() == null ? "" : req.getNote().trim();
        if (req.getReason() == CandidateConstants.REJECT_OTHER && note.isEmpty()) {
            throw new BizException("选择「其他」时请填写说明");
        }
        c.setStatus(CandidateConstants.STATUS_REJECTED);
        c.setRejectReason(req.getReason());
        c.setRejectNote(note);
        c.setReviewedBy(currentUser.resolve());
        c.setReviewedAt(LocalDateTime.now());
        candidateMapper.updateById(c);
        archiver.unlinkRejected(c.getId());
        logService.recordOperateLog(CandidateConstants.MENU, "驳回", null,
                Map.of("candidate", label(c), "reason", CandidateConstants.REJECT_NAMES.get(req.getReason())));
        return detail(id);
    }

    /** 已驳回的重新打开为待审核，来源询盘型号回到候选中 */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CandidateVO reopen(Long id) {
        ProductCandidateDO c = visible(id);
        candidateMapper.lockById(id);
        c = candidateMapper.selectById(id);
        if (c.getStatus() != CandidateConstants.STATUS_REJECTED) {
            throw new BizException("只有已驳回的候选可以重新打开");
        }
        c.setStatus(CandidateConstants.STATUS_PENDING);
        c.setRejectReason(0);
        c.setRejectNote("");
        c.setReviewedBy(null);
        c.setReviewedAt(null);
        candidateMapper.updateById(c);
        archiver.sync(sourceItemIds(id));
        logService.recordOperateLog(CandidateConstants.MENU, "重新打开", null, Map.of("candidate", label(c)));
        return detail(id);
    }

    // ---------------------------------------------------------------- 内部

    /** 建档：品牌（已有 / 别名 / 新建）→ 系列 → 商品；商品库已有相同型号时改为并入 */
    private Long archive(ProductCandidateDO c, ApproveCandidateRequest req) {
        Long brandId = resolveBrand(c, req);
        if (req.getCategoryId() == null) {
            throw new BizException("请选择品类");
        }
        String mpn = StringUtils.hasText(req.getMpnRaw()) ? req.getMpnRaw().trim() : c.getMpnRaw();
        ProductDO existing = productMapper.selectOne(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductDO::getBrandId, brandId)
                .eq(ProductDO::getMpnNormalized, MpnNormalizer.normalize(mpn))
                .isNull(ProductDO::getDeletedAt)
                .last("LIMIT 1"));
        if (existing != null) {
            c.setBrandId(brandId);
            resolve(c, CandidateConstants.STATUS_MERGED, existing.getId());
            return existing.getId();
        }
        Long seriesId = req.getSeriesId();
        if (StringUtils.hasText(req.getNewSeriesName())) {
            CreateSeriesRequest s = new CreateSeriesRequest();
            s.setBrandId(brandId);
            s.setSeriesName(req.getNewSeriesName().trim());
            seriesId = platformScopeGuard.asCandidateReview(() -> seriesService.create(s)).getId();
        }
        SaveProductRequest p = new SaveProductRequest();
        p.setBrandId(brandId);
        p.setCategoryId(req.getCategoryId());
        p.setSeriesId(seriesId);
        p.setMpnRaw(mpn);
        p.setMpnDisplay(StringUtils.hasText(req.getMpnDisplay()) ? req.getMpnDisplay().trim() : mpn);
        // 商品名称、简短描述默认用中文描述（系统内中文）
        p.setProductName(req.getProductName() != null ? req.getProductName().trim()
                : (StringUtils.hasText(c.getDescription()) ? c.getDescription() : c.getProductName()));
        p.setShortDescription(req.getShortDescription() != null ? req.getShortDescription().trim()
                : (StringUtils.hasText(c.getDescription()) ? c.getDescription() : c.getDescriptionEn()));
        Lifecycle suggested = suggestLifecycle(c.getId());
        p.setLifecycleStatus(req.getLifecycleStatus() != null ? req.getLifecycleStatus() : suggested.status());
        p.setLifecycleSource(req.getLifecycleSource() != null ? req.getLifecycleSource().trim()
                : (Objects.equals(p.getLifecycleStatus(), suggested.status()) ? suggested.source() : ""));
        Long productId = platformScopeGuard.asCandidateReview(() -> productService.create(p)).getId();
        c.setBrandId(brandId);
        c.setCategoryId(req.getCategoryId());
        resolve(c, CandidateConstants.STATUS_ARCHIVED, productId);
        return productId;
    }

    record Lifecycle(int status, String source) {
    }

    /**
     * 建议的生命周期：来源询盘型号里采购最近核实的生产状态（在产 → 在产；停产 → 已停产，依据写「采购询价核实停产」及替代型号；
     * 都是待查 → 未知）。
     */
    Lifecycle suggestLifecycle(Long candidateId) {
        List<Long> ids = sourceItemIds(candidateId);
        if (ids.isEmpty()) {
            return new Lifecycle(LifecycleStatus.UNKNOWN, "");
        }
        InquiryItemDO verified = itemMapper.selectBatchIds(ids).stream()
                .filter(i -> i.getLifecycle() != null && i.getLifecycle() != InquiryConstants.LIFECYCLE_UNKNOWN)
                .max(Comparator.comparing(InquiryItemDO::getUpdateTime, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        if (verified == null) {
            return new Lifecycle(LifecycleStatus.UNKNOWN, "");
        }
        if (verified.getLifecycle() == InquiryConstants.LIFECYCLE_DISCONTINUED) {
            String replacement = verified.getReplacementModel() == null ? "" : verified.getReplacementModel().trim();
            return new Lifecycle(LifecycleStatus.DISCONTINUED, "采购询价核实停产" + (replacement.isEmpty() ? "" : "，替代型号 " + replacement));
        }
        return new Lifecycle(LifecycleStatus.ACTIVE, "");
    }

    private Long resolveBrand(ProductCandidateDO c, ApproveCandidateRequest req) {
        String mode = req.getBrandMode() == null ? "EXISTING" : req.getBrandMode().trim().toUpperCase(Locale.ROOT);
        Long brandId;
        switch (mode) {
            case "NEW" -> {
                if (!StringUtils.hasText(req.getBrandName())) {
                    throw new BizException("请填写品牌名称");
                }
                String name = req.getBrandName().trim();
                SaveBrandRequest b = new SaveBrandRequest();
                b.setBrandName(name);
                if (StringUtils.hasText(c.getBrandText()) && !c.getBrandText().trim().equalsIgnoreCase(name)) {
                    b.setAliases(List.of(c.getBrandText().trim()));
                }
                brandId = platformScopeGuard.asCandidateReview(() -> brandService.create(b)).getId();
            }
            case "ALIAS" -> {
                brandId = requireBrand(req.getBrandId());
                if (StringUtils.hasText(c.getBrandText())) {
                    Long target = brandId;
                    platformScopeGuard.asCandidateReview(() -> {
                        brandService.addAlias(target, c.getBrandText().trim());
                        return null;
                    });
                }
            }
            default -> brandId = requireBrand(req.getBrandId() != null ? req.getBrandId() : c.getBrandId());
        }
        if (!"EXISTING".equals(mode) || c.getBrandId() == null) {
            recognizeSameBrandText(c, brandId);
        }
        return brandId;
    }

    /** 品牌原文确认后，其他待审核候选里同样的品牌原文也识别为该品牌 */
    private void recognizeSameBrandText(ProductCandidateDO c, Long brandId) {
        if (!StringUtils.hasText(c.getBrandKey()) || c.getBrandKey().startsWith("#")) {
            return;
        }
        List<ProductCandidateDO> same = candidateMapper.selectList(new LambdaQueryWrapper<ProductCandidateDO>()
                .eq(ProductCandidateDO::getBrandKey, c.getBrandKey())
                .eq(ProductCandidateDO::getStatus, CandidateConstants.STATUS_PENDING)
                .isNull(ProductCandidateDO::getBrandId)
                .ne(ProductCandidateDO::getId, c.getId())
                .isNull(ProductCandidateDO::getDeletedAt));
        for (ProductCandidateDO o : same) {
            o.setBrandId(brandId);
            candidateMapper.updateById(o);
        }
    }

    private Long requireBrand(Long brandId) {
        ProductBrandDO b = brandId == null ? null : brandMapper.selectById(brandId);
        if (b == null || b.getDeletedAt() != null) {
            throw new BizException("请选择品牌");
        }
        return b.getId();
    }

    private void resolve(ProductCandidateDO c, int status, Long productId) {
        c.setStatus(status);
        c.setProductId(productId);
        c.setReviewedBy(currentUser.resolve());
        c.setReviewedAt(LocalDateTime.now());
        candidateMapper.updateById(c);
        archiver.linkSources(c.getId(), productId);
    }

    private ProductCandidateDO lockPending(Long id) {
        visible(id);
        candidateMapper.lockById(id);
        ProductCandidateDO c = candidateMapper.selectById(id);
        if (c.getStatus() != CandidateConstants.STATUS_PENDING) {
            throw new BizException("候选已审核（" + CandidateConstants.STATUS_NAMES.get(c.getStatus()) + "）");
        }
        return c;
    }

    private ProductCandidateDO visible(Long id) {
        ProductCandidateDO c = id == null ? null : candidateMapper.selectById(id);
        if (c == null || c.getDeletedAt() != null || !canSee(c.getId())) {
            throw new BizException("候选不存在");
        }
        return c;
    }

    private boolean canSee(Long candidateId) {
        return platformScopeGuard.isPlatform() || sourceMapper.selectCount(new LambdaQueryWrapper<ProductCandidateSourceDO>()
                .eq(ProductCandidateSourceDO::getCandidateId, candidateId)
                .eq(ProductCandidateSourceDO::getTenantId, tenantId())
                .isNull(ProductCandidateSourceDO::getDeletedAt)) > 0;
    }

    private List<Long> sourceItemIds(Long candidateId) {
        return sourceMapper.selectList(new LambdaQueryWrapper<ProductCandidateSourceDO>()
                        .select(ProductCandidateSourceDO::getInquiryItemId)
                        .eq(ProductCandidateSourceDO::getCandidateId, candidateId)
                        .isNotNull(ProductCandidateSourceDO::getInquiryItemId)
                        .isNull(ProductCandidateSourceDO::getDeletedAt))
                .stream().map(ProductCandidateSourceDO::getInquiryItemId).distinct().toList();
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? -1 : t;
    }

    private static String label(ProductCandidateDO c) {
        return (StringUtils.hasText(c.getBrandText()) ? c.getBrandText() + " / " : "") + c.getMpnRaw();
    }

    private List<CandidateVO> toVos(List<ProductCandidateDO> rows, boolean full) {
        if (rows.isEmpty()) {
            return List.of();
        }
        boolean platform = platformScopeGuard.isPlatform();
        int me = tenantId();
        List<Long> ids = rows.stream().map(ProductCandidateDO::getId).toList();
        Map<Long, List<ProductCandidateSourceDO>> sources = sourceMapper.selectList(new LambdaQueryWrapper<ProductCandidateSourceDO>()
                        .in(ProductCandidateSourceDO::getCandidateId, ids)
                        .isNull(ProductCandidateSourceDO::getDeletedAt)
                        .orderByDesc(ProductCandidateSourceDO::getCreateTime)
                        .orderByDesc(ProductCandidateSourceDO::getId))
                .stream().collect(Collectors.groupingBy(ProductCandidateSourceDO::getCandidateId));
        Map<Long, String> brands = names(rows.stream().map(ProductCandidateDO::getBrandId).filter(Objects::nonNull).toList(), true);
        Map<Long, String> categories = names(rows.stream().map(ProductCandidateDO::getCategoryId).filter(Objects::nonNull).toList(), false);
        Map<Long, String> users = lookups.userNames(rows.stream().map(ProductCandidateDO::getReviewedBy).filter(Objects::nonNull).toList());
        Map<Long, String> products = new HashMap<>();
        List<Long> productIds = rows.stream().map(ProductCandidateDO::getProductId).filter(Objects::nonNull).toList();
        if (!productIds.isEmpty()) {
            productMapper.selectBatchIds(productIds).forEach(p -> products.put(p.getId(), p.getMpnDisplay() != null ? p.getMpnDisplay() : p.getMpnRaw()));
        }
        // 来源询盘型号的原文（回填真实型号时展示「回填自…」）
        Set<Long> itemIds = sources.values().stream().flatMap(List::stream)
                .filter(s -> platform || s.getTenantId() == me).map(ProductCandidateSourceDO::getInquiryItemId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, InquiryItemDO> items = itemIds.isEmpty() ? Map.of()
                : itemMapper.selectBatchIds(itemIds).stream().collect(Collectors.toMap(InquiryItemDO::getId, i -> i));
        // 来源单据号：一页候选的可见来源一次查完
        List<ProductCandidateSourceDO> visibleSources = sources.values().stream().flatMap(List::stream)
                .filter(s -> platform || s.getTenantId() == me).toList();
        Map<Long, String> codes = codesOf(visibleSources);
        Map<Long, String> soNos = soNosOf(visibleSources);
        List<CandidateVO> out = new ArrayList<>(rows.size());
        for (ProductCandidateDO c : rows) {
            List<ProductCandidateSourceDO> all = sources.getOrDefault(c.getId(), List.of());
            List<ProductCandidateSourceDO> mine = platform ? all : all.stream().filter(s -> s.getTenantId() == me).toList();
            CandidateVO vo = new CandidateVO();
            vo.setId(c.getId());
            vo.setBrandId(c.getBrandId());
            vo.setBrandName(at(brands, c.getBrandId()));
            vo.setBrandText(c.getBrandText());
            vo.setMpnRaw(c.getMpnRaw());
            vo.setCategoryId(c.getCategoryId());
            vo.setCategoryName(at(categories, c.getCategoryId()));
            vo.setCategoryText(c.getCategoryText());
            vo.setProductName(c.getProductName());
            vo.setDescription(c.getDescription());
            vo.setDescriptionEn(c.getDescriptionEn());
            vo.setStatus(c.getStatus());
            vo.setStatusName(CandidateConstants.STATUS_NAMES.get(c.getStatus()));
            vo.setLevel(c.getLevel());
            vo.setLevelName(CandidateConstants.LEVEL_NAMES.get(c.getLevel()));
            vo.setInquiryCount((int) mine.stream().map(ProductCandidateSourceDO::getInquiryItemId).filter(Objects::nonNull).distinct().count());
            vo.setDealCount((int) mine.stream().map(ProductCandidateSourceDO::getSoId).filter(Objects::nonNull).distinct().count());
            vo.setFirstSeenAt(c.getFirstSeenAt());
            vo.setLastSeenAt(c.getLastSeenAt());
            vo.setProductId(c.getProductId());
            vo.setProductLabel(at(products, c.getProductId()));
            vo.setRejectReason(c.getRejectReason());
            vo.setRejectReasonName(at(CandidateConstants.REJECT_NAMES, c.getRejectReason()));
            vo.setRejectNote(c.getRejectNote());
            vo.setReviewedByName(at(users, c.getReviewedBy()));
            vo.setReviewedAt(c.getReviewedAt());
            vo.setOriginalModel(mine.stream().filter(s -> s.getSourceType() == CandidateConstants.SOURCE_ACTUAL_MODEL)
                    .map(s -> at(items, s.getInquiryItemId())).filter(Objects::nonNull)
                    .map(InquiryItemDO::getConfirmedModel).findFirst().orElse(null));
            vo.setSources(sourceVos(full ? mine : mine.stream().limit(3).toList(), items, codes, soNos));
            if (full) {
                Lifecycle lc = suggestLifecycle(c.getId());
                vo.setSuggestedLifecycle(lc.status());
                vo.setSuggestedLifecycleSource(lc.source());
            }
            vo.setMineSourceCount(mine.size());
            vo.setOtherSourceCount(all.size() - mine.size());
            vo.setCreateTime(c.getCreateTime());
            vo.setCreateBy(c.getCreateBy());
            vo.setUpdateTime(c.getUpdateTime());
            vo.setUpdateBy(c.getUpdateBy());
            out.add(vo);
        }
        return out;
    }

    private Map<Long, String> codesOf(List<ProductCandidateSourceDO> rows) {
        Set<Long> ids = rows.stream().map(ProductCandidateSourceDO::getCustomerInquiryId).filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : inquiryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(CustomerInquiryDO::getId, CustomerInquiryDO::getInquiryCode));
    }

    private Map<Long, String> soNosOf(List<ProductCandidateSourceDO> rows) {
        Set<Long> ids = rows.stream().map(ProductCandidateSourceDO::getSoId).filter(Objects::nonNull).collect(Collectors.toSet());
        return ids.isEmpty() ? Map.of() : soMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SalesOrderDO::getId, SalesOrderDO::getSoNo));
    }

    private List<CandidateVO.Source> sourceVos(List<ProductCandidateSourceDO> rows, Map<Long, InquiryItemDO> items,
                                               Map<Long, String> codes, Map<Long, String> soNos) {
        return rows.stream().map(s -> {
            CandidateVO.Source x = new CandidateVO.Source();
            x.setSourceType(s.getSourceType());
            x.setSourceTypeName(CandidateConstants.SOURCE_NAMES.get(s.getSourceType()));
            x.setCustomerInquiryId(s.getCustomerInquiryId());
            x.setInquiryCode(at(codes, s.getCustomerInquiryId()));
            InquiryItemDO item = at(items, s.getInquiryItemId());
            x.setOriginalModel(item == null ? null : item.getConfirmedModel());
            x.setSoId(s.getSoId());
            x.setSoNo(at(soNos, s.getSoId()));
            x.setCreateTime(s.getCreateTime());
            return x;
        }).toList();
    }

    /** Map.of() 不接受 null 键，统一用它取值 */
    private static <K, V> V at(Map<K, V> map, K key) {
        return key == null ? null : map.get(key);
    }

    private Map<Long, String> names(Collection<Long> ids, boolean brand) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        if (brand) {
            return brandMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(ProductBrandDO::getId, ProductBrandDO::getBrandName));
        }
        return categoryMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(ProductCategoryDO::getId,
                c -> StringUtils.hasText(c.getCategoryNameZh()) ? c.getCategoryNameZh() : c.getCategoryName()));
    }
}
