package com.zhul.erp.modules.product.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.modules.product.content.dto.ContentTaskQuery;
import com.zhul.erp.modules.product.content.dto.ContentTaskVO;
import com.zhul.erp.modules.product.content.dto.ProductContentVO;
import com.zhul.erp.modules.product.content.entity.ProductContentImportDO;
import com.zhul.erp.modules.product.content.entity.ProductContentTaskDO;
import com.zhul.erp.modules.product.content.entity.ProductLocaleDO;
import com.zhul.erp.modules.product.content.entity.ProductSeoDO;
import com.zhul.erp.modules.product.content.entity.ProductSeoFaqDO;
import com.zhul.erp.modules.product.content.repository.ContentTaskRow;
import com.zhul.erp.modules.product.content.repository.ProductContentImportMapper;
import com.zhul.erp.modules.product.content.repository.ProductContentTaskMapper;
import com.zhul.erp.modules.product.content.repository.ProductLocaleMapper;
import com.zhul.erp.modules.product.content.repository.ProductSeoFaqMapper;
import com.zhul.erp.modules.product.content.repository.ProductSeoMapper;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.support.LikeUtils;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.product.support.ProductFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 内容任务：列表、任务行的按需创建与语言状态、商品详情里的本公司内容 */
@Service
@RequiredArgsConstructor
public class ContentTaskService {

    private final ProductContentTaskMapper taskMapper;
    private final ProductContentImportMapper importMapper;
    private final ProductSeoMapper seoMapper;
    private final ProductSeoFaqMapper faqMapper;
    private final ProductLocaleMapper localeMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductFinder productFinder;

    public PageResult<ContentTaskVO> page(ContentTaskQuery q) {
        int page = q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage();
        int size = q.getPageSize() == null || q.getPageSize() < 1 ? 20 : Math.min(q.getPageSize(), 200);
        String keyword = keyword(q.getKeyword());
        String mpn = mpn(q.getKeyword());
        Integer tenantId = TenantContext.getTenantId();
        long total = taskMapper.count(tenantId, q.getStatus(), q.getBrandId(), q.getCategoryId(), keyword, mpn);
        List<ContentTaskRow> rows = total == 0 ? List.of()
                : taskMapper.page(tenantId, q.getStatus(), q.getBrandId(), q.getCategoryId(), keyword, mpn,
                (long) (page - 1) * size, size);
        Map<Long, String> brands = brandNames(rows.stream().map(ContentTaskRow::getBrandId).collect(Collectors.toSet()));
        Map<Long, String> categories = categoryNames(rows.stream().map(ContentTaskRow::getCategoryId).collect(Collectors.toSet()));
        List<ContentTaskVO> records = new ArrayList<>(rows.size());
        for (ContentTaskRow r : rows) {
            ContentTaskVO vo = new ContentTaskVO();
            vo.setProductId(r.getProductId());
            vo.setTaskId(r.getTaskId());
            vo.setBrandName(brands.get(r.getBrandId()));
            vo.setCategoryName(categories.get(r.getCategoryId()));
            vo.setMpn(StringUtils.hasText(r.getMpnDisplay()) ? r.getMpnDisplay() : r.getMpnRaw());
            vo.setProductName(r.getProductName());
            vo.setStatus(r.getStatus());
            vo.setZhAt(r.getZhAt());
            vo.setZhBy(r.getZhBy());
            vo.setEnAt(r.getEnAt());
            vo.setEnBy(r.getEnBy());
            vo.setRuAt(r.getRuAt());
            vo.setRuBy(r.getRuBy());
            vo.setDownloadedAt(r.getDownloadedAt());
            vo.setCreateTime(r.getCreateTime());
            vo.setCreateBy(r.getCreateBy());
            vo.setUpdateTime(r.getUpdateTime());
            vo.setUpdateBy(r.getUpdateBy());
            records.add(vo);
        }
        return PageResult.of(total, records);
    }

    /** 各状态数量：all / 1 / 2 / 3 */
    public Map<String, Long> counts(ContentTaskQuery q) {
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("all", 0L);
        for (int s = ContentConstants.STATUS_PENDING; s <= ContentConstants.STATUS_DONE; s++) {
            result.put(String.valueOf(s), 0L);
        }
        for (Map<String, Object> row : taskMapper.countByStatus(TenantContext.getTenantId(), q.getBrandId(),
                q.getCategoryId(), keyword(q.getKeyword()), mpn(q.getKeyword()))) {
            long cnt = ((Number) row.get("cnt")).longValue();
            result.put(String.valueOf(row.get("status")), cnt);
            result.merge("all", cnt, Long::sum);
        }
        return result;
    }

    /** 取本公司该商品的任务行并加锁，没有时创建；必须在事务内调用 */
    public ProductContentTaskDO lockOrCreate(Long productId) {
        Integer tenantId = TenantContext.getTenantId();
        Long id = taskMapper.lockByProduct(tenantId, productId);
        if (id == null) {
            ProductContentTaskDO row = new ProductContentTaskDO();
            row.setTenantId(tenantId);
            row.setProductId(productId);
            row.setStatus(ContentConstants.STATUS_PENDING);
            try {
                taskMapper.insert(row);
            } catch (DuplicateKeyException e) {
                // 并发时另一个请求先插入了，锁住它即可
            }
            id = taskMapper.lockByProduct(tenantId, productId);
        }
        return taskMapper.selectById(id);
    }

    /** 记录下载任务包时间（逐个商品独立，不锁商品） */
    public void markDownloaded(Collection<Long> productIds) {
        Integer tenantId = TenantContext.getTenantId();
        LocalDateTime now = LocalDateTime.now();
        for (Long productId : productIds) {
            int updated = taskMapper.update(null, new LambdaUpdateWrapper<ProductContentTaskDO>()
                    .eq(ProductContentTaskDO::getTenantId, tenantId)
                    .eq(ProductContentTaskDO::getProductId, productId)
                    .set(ProductContentTaskDO::getDownloadedAt, now)
                    .set(ProductContentTaskDO::getUpdateTime, now)
                    .set(ProductContentTaskDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
            if (updated == 0) {
                ProductContentTaskDO row = new ProductContentTaskDO();
                row.setTenantId(tenantId);
                row.setProductId(productId);
                row.setStatus(ContentConstants.STATUS_PENDING);
                row.setDownloadedAt(now);
                try {
                    taskMapper.insert(row);
                } catch (DuplicateKeyException e) {
                    // 并发插入，下载时间以另一个请求为准
                }
            }
        }
    }

    /** 标记某语言已写入并重算状态（调用方已锁住任务行） */
    public void markWritten(ProductContentTaskDO task, String lang) {
        LocalDateTime now = LocalDateTime.now();
        String user = SecurityUtils.getCurrentUsername();
        switch (lang) {
            case "zh" -> {
                task.setZhAt(now);
                task.setZhBy(user);
            }
            case "en" -> {
                task.setEnAt(now);
                task.setEnBy(user);
            }
            default -> {
                task.setRuAt(now);
                task.setRuBy(user);
            }
        }
        int written = (task.getZhAt() != null ? 1 : 0) + (task.getEnAt() != null ? 1 : 0) + (task.getRuAt() != null ? 1 : 0);
        task.setStatus(written == 0 ? ContentConstants.STATUS_PENDING
                : written == ContentConstants.LANGS.size() ? ContentConstants.STATUS_DONE : ContentConstants.STATUS_PARTIAL);
        taskMapper.updateById(task);
    }

    /** 商品详情：本公司 SEO/GEO、FAQ、各语言规格摘要与最近一次上传 */
    public ProductContentVO productContent(Long productId) {
        productFinder.active(productId);
        Integer tenantId = TenantContext.getTenantId();
        ProductContentVO vo = new ProductContentVO();
        ProductContentTaskDO task = taskMapper.selectOne(new LambdaQueryWrapper<ProductContentTaskDO>()
                .eq(ProductContentTaskDO::getTenantId, tenantId)
                .eq(ProductContentTaskDO::getProductId, productId)
                .isNull(ProductContentTaskDO::getDeletedAt));
        vo.setStatus(task == null ? ContentConstants.STATUS_PENDING : task.getStatus());
        if (task != null) {
            vo.setZhAt(task.getZhAt());
            vo.setEnAt(task.getEnAt());
            vo.setRuAt(task.getRuAt());
        }
        Map<String, ProductSeoDO> seo = seoMapper.selectList(new LambdaQueryWrapper<ProductSeoDO>()
                        .eq(ProductSeoDO::getTenantId, tenantId)
                        .eq(ProductSeoDO::getProductId, productId)
                        .isNull(ProductSeoDO::getDeletedAt))
                .stream().collect(Collectors.toMap(ProductSeoDO::getLang, Function.identity(), (a, b) -> a));
        Map<String, String> summaries = localeMapper.selectList(new LambdaQueryWrapper<ProductLocaleDO>()
                        .eq(ProductLocaleDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .eq(ProductLocaleDO::getProductId, productId))
                .stream().collect(Collectors.toMap(ProductLocaleDO::getLang, ProductLocaleDO::getSpecSummary, (a, b) -> a));
        List<ProductSeoFaqDO> faqs = faqMapper.selectList(new LambdaQueryWrapper<ProductSeoFaqDO>()
                .eq(ProductSeoFaqDO::getTenantId, tenantId)
                .eq(ProductSeoFaqDO::getProductId, productId)
                .isNull(ProductSeoFaqDO::getDeletedAt)
                .orderByAsc(ProductSeoFaqDO::getSortOrder)
                .orderByAsc(ProductSeoFaqDO::getId));
        Set<Long> importIds = new HashSet<>();
        seo.values().forEach(s -> importIds.add(s.getImportId()));
        importIds.remove(null);
        Map<Long, ProductContentImportDO> imports = importIds.isEmpty() ? Map.of()
                : importMapper.selectList(new LambdaQueryWrapper<ProductContentImportDO>()
                        .select(ProductContentImportDO::getId, ProductContentImportDO::getFileName,
                                ProductContentImportDO::getConfirmedBy, ProductContentImportDO::getCreateTime)
                        .in(ProductContentImportDO::getId, importIds))
                .stream().collect(Collectors.toMap(ProductContentImportDO::getId, Function.identity()));
        for (String lang : ContentConstants.LANGS) {
            ProductContentVO.LangContent c = new ProductContentVO.LangContent();
            c.setSpecSummary(summaries.get(lang));
            ProductSeoDO s = seo.get(lang);
            if (s != null) {
                c.setSeoTitle(s.getSeoTitle());
                c.setMetaDescription(s.getMetaDescription());
                c.setLongDescription(s.getLongDescription());
                c.setGeoAnswer(s.getGeoAnswer());
                ProductContentImportDO imp = s.getImportId() == null ? null : imports.get(s.getImportId());
                if (imp != null) {
                    c.setFileName(imp.getFileName());
                    c.setConfirmedBy(imp.getConfirmedBy());
                    c.setConfirmedAt(imp.getCreateTime());
                }
            }
            for (ProductSeoFaqDO f : faqs) {
                if (lang.equals(f.getLang())) {
                    ProductContentVO.Faq faq = new ProductContentVO.Faq();
                    faq.setQuestion(f.getQuestion());
                    faq.setAnswer(f.getAnswer());
                    c.getFaqs().add(faq);
                }
            }
            vo.getLangs().put(lang, c);
        }
        return vo;
    }

    Map<Long, String> brandNames(Set<Long> ids) {
        ids.remove(null);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (ProductBrandDO b : brandMapper.selectBatchIds(ids)) {
            result.put(b.getId(), b.getBrandName());
        }
        return result;
    }

    Map<Long, String> categoryNames(Set<Long> ids) {
        ids.remove(null);
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> result = new HashMap<>();
        for (ProductCategoryDO c : categoryMapper.selectBatchIds(ids)) {
            result.put(c.getId(), StringUtils.hasText(c.getCategoryNameZh()) ? c.getCategoryNameZh() : c.getCategoryName());
        }
        return result;
    }

    private static String keyword(String raw) {
        return StringUtils.hasText(raw) ? LikeUtils.escape(raw.trim()) : null;
    }

    private static String mpn(String raw) {
        String n = StringUtils.hasText(raw) ? MpnNormalizer.normalize(raw) : "";
        return n.isEmpty() ? null : n;
    }
}
