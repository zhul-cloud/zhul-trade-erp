package com.zhul.erp.modules.product.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.zhul.erp.modules.product.candidate.dto.BatchEditRequest;
import com.zhul.erp.modules.product.candidate.dto.BatchEditVO;
import com.zhul.erp.modules.product.candidate.dto.CandidatePageQuery;
import com.zhul.erp.modules.product.candidate.dto.CandidateVO;
import com.zhul.erp.modules.product.candidate.dto.RejectCandidateRequest;
import com.zhul.erp.modules.product.candidate.entity.ProductCandidateDO;
import com.zhul.erp.modules.product.candidate.entity.ProductCandidateSourceDO;
import com.zhul.erp.modules.product.candidate.repository.ProductCandidateMapper;
import com.zhul.erp.modules.product.candidate.repository.ProductCandidateSourceMapper;
import com.zhul.erp.modules.product.constants.LifecycleStatus;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.merge.service.ProductMergeService;
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
import com.zhul.erp.modules.product.support.IndustryScope;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.product.support.ProductOrderCodes;
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
    private final ProductOrderCodes orderCodes;
    private final LogService logService;
    private final IndustryScope industryScope;
    private final ProductMergeService mergeService;
    private final com.zhul.erp.modules.product.support.ProductWriteGuard productWriteGuard;

    // ---------------------------------------------------------------- 查询

    public PageResult<CandidateVO> page(CandidatePageQuery q) {
        int status = q.getStatus() == null ? CandidateConstants.STATUS_PENDING : q.getStatus();
        LambdaQueryWrapper<ProductCandidateDO> w = filtered(q, status);
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = candidateMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        // 待确认品牌的排在最后，其次是没选品类的，方便先批量审核能直接通过的；再按可信度从高到低、更新时间倒序
        w.last("ORDER BY brand_id IS NULL, category_id IS NULL, level DESC, update_time DESC, id DESC LIMIT "
                + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toVos(candidateMapper.selectList(w), false));
    }

    /** 各页签的条数，按列表同样的筛选条件统计（忽略 status），页签上的数字和筛选后的列表一致 */
    public Map<String, Long> counts(CandidatePageQuery q) {
        Map<String, Long> out = new LinkedHashMap<>();
        out.put("pending", candidateMapper.selectCount(filtered(q, CandidateConstants.STATUS_PENDING)));
        out.put("archived", candidateMapper.selectCount(filtered(q, CandidateConstants.STATUS_ARCHIVED)));
        out.put("merged", candidateMapper.selectCount(filtered(q, CandidateConstants.STATUS_MERGED)));
        out.put("rejected", candidateMapper.selectCount(filtered(q, CandidateConstants.STATUS_REJECTED)));
        return out;
    }

    public Map<String, Long> counts() {
        return counts(new CandidatePageQuery());
    }

    private LambdaQueryWrapper<ProductCandidateDO> filtered(CandidatePageQuery q, int status) {
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
        if (Integer.valueOf(1).equals(q.getBrandState())) {
            w.isNotNull(ProductCandidateDO::getBrandId);
        } else if (Integer.valueOf(2).equals(q.getBrandState())) {
            w.isNull(ProductCandidateDO::getBrandId);
        } else if (Integer.valueOf(3).equals(q.getBrandState())) {
            // AI 认不出、记为 Others 的
            w.eq(ProductCandidateDO::getAiBrand, 2);
        }
        if (Integer.valueOf(1).equals(q.getCategoryState())) {
            w.isNotNull(ProductCandidateDO::getCategoryId);
        } else if (Integer.valueOf(2).equals(q.getCategoryState())) {
            w.isNull(ProductCandidateDO::getCategoryId);
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            String key = MpnNormalizer.normalize(kw);
            w.and(x -> x.like(ProductCandidateDO::getMpnRaw, kw).or().like(ProductCandidateDO::getBrandText, kw)
                    .or().like(ProductCandidateDO::getProductName, kw)
                    .or(!key.isEmpty(), y -> y.like(ProductCandidateDO::getMpnNormalized, key))
                    .or().like(ProductCandidateDO::getOrderCode, kw)
                    // 也能按商品编号找（单据上「候选」标记跳过来时用）
                    .or(kw.matches("\\d{1,18}"), y -> y.eq(ProductCandidateDO::getProductId, kw.matches("\\d{1,18}") ? Long.valueOf(kw) : 0L)));
        }
        return w;
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
            // 先加行锁再按最新值判断：加锁前别人刚驳回 / 通过的，不能再用旧快照建档（BUS-022）
            c = lockedRow(id);
            if (c == null || c.getDeletedAt() != null) {
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
            if (!industryScope.categoryVisible(c.getCategoryId())) {
                skipped.add(new BatchApproveVO.Skipped(id, label(c), "品类不属于本公司行业"));
                continue;
            }
            ApproveCandidateRequest req = new ApproveCandidateRequest();
            req.setBrandMode("EXISTING");
            req.setBrandId(c.getBrandId());
            req.setCategoryId(c.getCategoryId());
            // 批量通过时与已建档商品重复的直接并入（与原来的批量行为一致）
            req.setConfirmMerge(true);
            archive(c, req);
            approved++;
        }
        vo.setApproved(approved);
        vo.setSkipped(skipped);
        logService.recordOperateLog(CandidateConstants.MENU, "批量通过", null, Map.of("approved", approved, "skipped", skipped.size()));
        return vo;
    }

    /**
     * 批量修改品牌、品类：只改仍待审核的候选，其余跳过；改了的那一项去掉 AI 建议标记（AI 给出的原名称保留）。
     * 逐条加行锁后修改，与审核互斥；同一请求重复提交结果相同。
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public BatchEditVO batchEdit(BatchEditRequest req) {
        if (req.getBrandId() == null && req.getCategoryId() == null) {
            throw new BizException("请选择要修改的品牌或品类");
        }
        if (req.getBrandId() != null && !industryScope.brandVisible(requireBrand(req.getBrandId()))) {
            throw new BizException("请选择品牌");
        }
        if (req.getCategoryId() != null) {
            ProductCategoryDO cat = categoryMapper.selectById(req.getCategoryId());
            if (cat == null || cat.getDeletedAt() != null || !Objects.equals(cat.getStatus(), ProductConstants.STATUS_ENABLED)
                    || !industryScope.categoryVisible(cat.getId())) {
                throw new BizException("请选择品类");
            }
        }
        int updated = 0;
        int skipped = 0;
        for (Long id : req.getIds().stream().filter(Objects::nonNull).distinct().sorted().toList()) {
            ProductCandidateDO c = candidateMapper.selectById(id);
            if (c == null || c.getDeletedAt() != null || !canSee(c.getId())) {
                skipped++;
                continue;
            }
            c = lockedRow(id);
            if (c.getStatus() != CandidateConstants.STATUS_PENDING) {
                skipped++;
                continue;
            }
            Map<String, Object> before = new LinkedHashMap<>();
            Map<String, Object> after = new LinkedHashMap<>();
            before.put("candidate", label(c));
            after.put("candidate", label(c));
            if (req.getBrandId() != null) {
                before.put("brandId", c.getBrandId());
                after.put("brandId", req.getBrandId());
                c.setBrandId(req.getBrandId());
                c.setAiBrand(0);
            }
            if (req.getCategoryId() != null) {
                before.put("categoryId", c.getCategoryId());
                after.put("categoryId", req.getCategoryId());
                c.setCategoryId(req.getCategoryId());
                c.setAiCategory(0);
            }
            candidateMapper.updateById(c);
            logService.recordOperateLog(CandidateConstants.MENU, "批量修改", before, after);
            updated++;
        }
        BatchEditVO vo = new BatchEditVO();
        vo.setUpdated(updated);
        vo.setSkipped(skipped);
        return vo;
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CandidateVO merge(Long id, Long productId, String reason) {
        ProductCandidateDO c = lockPending(id);
        ProductDO p = productMapper.selectById(productId);
        if (p == null || p.getDeletedAt() != null || !Objects.equals(p.getArchiveStatus(), ProductConstants.ARCHIVE_DONE)) {
            throw new BizException("请选择已建档的商品");
        }
        Map<String, Long> affected = mergeInto(c, p.getId(), reason);
        logService.recordOperateLog(CandidateConstants.MENU, "并入已有商品", null,
                Map.of("candidate", label(c), "productId", productId, "affected", affected));
        return detail(id);
    }

    /** 候选商品被各类单据引用的条数（并入、驳回前给用户看；spec product/product-candidate「候选审核」） */
    public Map<String, Long> references(Long id) {
        ProductCandidateDO c = visible(id);
        return c.getProductId() == null ? Map.of() : mergeService.references(c.getProductId());
    }

    /** 候选商品并入已建档商品：单据改指向主商品，候选变为已并入 */
    private Map<String, Long> mergeInto(ProductCandidateDO c, Long targetId, String reason) {
        Map<String, Long> affected = c.getProductId() == null ? Map.of()
                : mergeService.merge(c.getProductId(), targetId, StringUtils.hasText(reason) ? reason : "候选并入已有商品");
        resolve(c, CandidateConstants.STATUS_MERGED, targetId);
        return affected;
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
        if (c.getProductId() != null && mergeService.usedByOrders(c.getProductId())) {
            throw new BizException("已有订单使用，只能建档或并入");
        }
        c.setStatus(CandidateConstants.STATUS_REJECTED);
        c.setRejectReason(req.getReason());
        c.setRejectNote(note);
        c.setReviewedBy(currentUser.resolve());
        c.setReviewedAt(LocalDateTime.now());
        candidateMapper.updateById(c);
        setArchiveStatus(c.getProductId(), ProductConstants.ARCHIVE_REJECTED);
        archiver.unlinkRejected(c.getId());
        logService.recordOperateLog(CandidateConstants.MENU, "驳回", null,
                Map.of("candidate", label(c), "reason", CandidateConstants.REJECT_NAMES.get(req.getReason())));
        return detail(id);
    }

    /** 已驳回的重新打开为待审核，来源询盘型号回到候选中 */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public CandidateVO reopen(Long id) {
        ProductCandidateDO c = visible(id);
        c = lockedRow(id);
        if (c.getStatus() != CandidateConstants.STATUS_REJECTED) {
            throw new BizException("只有已驳回的候选可以重新打开");
        }
        c.setStatus(CandidateConstants.STATUS_PENDING);
        c.setRejectReason(0);
        c.setRejectNote("");
        c.setReviewedBy(null);
        c.setReviewedAt(null);
        candidateMapper.updateById(c);
        setArchiveStatus(c.getProductId(), ProductConstants.ARCHIVE_CANDIDATE);
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
        // 建档跳过了商品新建的行业校验，这里补上（audit BUS-065）
        if (!industryScope.categoryVisible(req.getCategoryId())) {
            throw new BizException("品类不属于本公司行业");
        }
        String mpn = StringUtils.hasText(req.getMpnRaw()) ? req.getMpnRaw().trim() : c.getMpnRaw();
        String key = MpnNormalizer.normalize(mpn);
        if (key.isEmpty()) {
            throw new BizException("型号至少包含一个字母或数字");
        }
        archiver.ensureProduct(c);
        Long own = c.getProductId();
        // 与已建档商品重复（审核时改了品牌或型号也可能撞上）：转为并入，先让用户确认受影响的单据
        ProductDO existing = productMapper.selectOne(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductDO::getBrandId, brandId)
                .eq(ProductDO::getMpnNormalized, key)
                .eq(ProductDO::getArchiveStatus, ProductConstants.ARCHIVE_DONE)
                .ne(ProductDO::getId, own)
                .isNull(ProductDO::getDeletedAt)
                .last("LIMIT 1"));
        if (existing != null) {
            if (!Boolean.TRUE.equals(req.getConfirmMerge())) {
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("targetId", existing.getId());
                detail.put("targetLabel", productLabel(existing));
                detail.put("references", mergeService.references(own));
                throw BizException.of(CandidateConstants.ERR_WILL_MERGE, "将并入 " + productLabel(existing), detail);
            }
            clearAiMarks(c, brandId, req.getCategoryId());
            c.setBrandId(brandId);
            mergeInto(c, existing.getId(), "审核时与已建档商品重复");
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
        // 建档沿用候选商品的 ID（spec product/product-candidate「候选审核」）：先标已建档，
        // 同品牌同型号的其他候选商品（品牌原文不同、后来识别为同一品牌）并入它，再按商品主数据规则写资料
        setArchiveStatus(own, ProductConstants.ARCHIVE_DONE);
        absorbSameKeyCandidates(own, brandId, key);
        platformScopeGuard.asCandidateReview(() -> productService.update(own, p));
        // 所属公司按审核人记（与原来审核时新建商品一致）
        int owner = platformScopeGuard.asCandidateReview(productWriteGuard::ownerForCreate);
        productMapper.update(null, new LambdaUpdateWrapper<ProductDO>()
                .eq(ProductDO::getId, own)
                .set(ProductDO::getBrandKey, ProductConstants.brandKeyOf(brandId))
                .set(ProductDO::getOwnerTenantId, owner));
        clearAiMarks(c, brandId, req.getCategoryId());
        c.setBrandId(brandId);
        c.setCategoryId(req.getCategoryId());
        resolve(c, CandidateConstants.STATUS_ARCHIVED, own);
        return own;
    }

    /** 品牌键 #品牌ID 与归一化型号相同的其他候选商品，并入刚建档的商品，对应候选一并变为已并入 */
    private void absorbSameKeyCandidates(Long own, Long brandId, String key) {
        List<ProductDO> others = productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .and(w -> w.eq(ProductDO::getBrandKey, ProductConstants.brandKeyOf(brandId)).or().eq(ProductDO::getBrandId, brandId))
                .eq(ProductDO::getMpnNormalized, key)
                .in(ProductDO::getArchiveStatus, ProductConstants.ARCHIVE_CANDIDATE, ProductConstants.ARCHIVE_REJECTED)
                .ne(ProductDO::getId, own));
        for (ProductDO o : others) {
            setArchiveStatus(o.getId(), ProductConstants.ARCHIVE_CANDIDATE);
            mergeService.merge(o.getId(), own, "同品牌同型号的候选随建档并入");
            for (ProductCandidateDO oc : candidateMapper.selectList(new LambdaQueryWrapper<ProductCandidateDO>()
                    .eq(ProductCandidateDO::getProductId, o.getId()))) {
                if (oc.getStatus() == CandidateConstants.STATUS_PENDING || oc.getStatus() == CandidateConstants.STATUS_REJECTED) {
                    resolve(oc, CandidateConstants.STATUS_MERGED, own);
                } else {
                    candidateMapper.update(null, new LambdaUpdateWrapper<ProductCandidateDO>()
                            .eq(ProductCandidateDO::getId, oc.getId()).set(ProductCandidateDO::getProductId, own));
                }
            }
        }
    }

    private void setArchiveStatus(Long productId, int status) {
        if (productId == null) {
            return;
        }
        productMapper.update(null, new LambdaUpdateWrapper<ProductDO>()
                .eq(ProductDO::getId, productId)
                .set(ProductDO::getArchiveStatus, status)
                .set(ProductDO::getUpdateTime, LocalDateTime.now()));
    }

    private static String productLabel(ProductDO p) {
        return (p.getMpnDisplay() != null && !p.getMpnDisplay().isEmpty() ? p.getMpnDisplay() : p.getMpnRaw())
                + "（商品编号 " + p.getId() + "）";
    }

    /** 审核时改了 AI 写入的品类或品牌，AI 标记随之去掉 */
    private static void clearAiMarks(ProductCandidateDO c, Long brandId, Long categoryId) {
        if (!Objects.equals(c.getCategoryId(), categoryId)) {
            c.setAiCategory(0);
        }
        if (!Objects.equals(c.getBrandId(), brandId)) {
            c.setAiBrand(0);
        }
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
        ProductDO p = productMapper.selectById(productId);
        if (p != null) {
            orderCodes.addAll(p.getId(), p.getBrandId(), ProductOrderCodes.split(c.getOrderCode()));
        }
    }

    /**
     * 行锁读取最新值（SELECT … FOR UPDATE 一步完成）。不能先 lockById 再 selectById：同一事务里 MyBatis 会把
     * 之前 selectById 的结果直接从一级缓存返回，拿到的仍是加锁前的旧快照（BUS-022）。
     */
    private ProductCandidateDO lockedRow(Long id) {
        return candidateMapper.selectOne(new LambdaQueryWrapper<ProductCandidateDO>()
                .eq(ProductCandidateDO::getId, id).last("FOR UPDATE"));
    }

    private ProductCandidateDO lockPending(Long id) {
        visible(id);
        ProductCandidateDO c = lockedRow(id);
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

    boolean canSee(Long candidateId) {
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
        // 被单据引用的条数（spec product/product-candidate：已被订单及之后单据引用的不能驳回）
        Map<Long, Map<String, Long>> refs = mergeService.references(rows.stream()
                .filter(c -> c.getStatus() == CandidateConstants.STATUS_PENDING).map(ProductCandidateDO::getProductId)
                .filter(Objects::nonNull).toList());
        java.util.Set<String> orderLabels = com.zhul.erp.modules.product.support.DocumentProductTables.ENTRIES.stream()
                .filter(com.zhul.erp.modules.product.support.DocumentProductTables.Entry::afterOrder)
                .map(com.zhul.erp.modules.product.support.DocumentProductTables.Entry::label).collect(Collectors.toSet());
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
            vo.setOrderCode(c.getOrderCode());
            vo.setCategoryId(c.getCategoryId());
            vo.setCategoryName(at(categories, c.getCategoryId()));
            vo.setCategoryText(c.getCategoryText());
            vo.setAiCategory(c.getAiCategory());
            vo.setAiBrand(c.getAiBrand());
            vo.setAiBrandName(c.getAiBrandName());
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
            Map<String, Long> r = c.getProductId() == null ? Map.of() : refs.getOrDefault(c.getProductId(), Map.of());
            vo.setReferences(r);
            vo.setUsedByOrders(r.keySet().stream().anyMatch(orderLabels::contains));
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
            x.setFileName(s.getFileName());
            x.setCreateBy(s.getCreateBy());
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
