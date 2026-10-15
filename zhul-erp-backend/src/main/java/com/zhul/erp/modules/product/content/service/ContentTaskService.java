package com.zhul.erp.modules.product.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.modules.product.content.dto.ContentTaskQuery;
import com.zhul.erp.modules.product.content.dto.ContentTaskVO;
import com.zhul.erp.modules.product.content.dto.MarketingSkipVO;
import com.zhul.erp.modules.product.content.dto.ProductContentVO;
import com.zhul.erp.modules.product.content.entity.ProductContentImportDO;
import com.zhul.erp.modules.product.content.entity.ProductContentTaskDO;
import com.zhul.erp.modules.product.content.entity.ProductFactTaskDO;
import com.zhul.erp.modules.product.content.entity.ProductMarketingSkipDO;
import com.zhul.erp.modules.product.content.entity.ProductPlatformContentDO;
import com.zhul.erp.modules.product.content.entity.ProductFactNoteDO;
import com.zhul.erp.modules.product.content.entity.ProductKeywordDO;
import com.zhul.erp.modules.product.content.entity.ProductKeywordUseDO;
import com.zhul.erp.modules.product.content.entity.ProductSeoDO;
import com.zhul.erp.modules.product.content.entity.ProductSeoFaqDO;
import com.zhul.erp.modules.product.content.repository.ContentTaskRow;
import com.zhul.erp.modules.product.content.repository.PlatformFlags;
import com.zhul.erp.modules.product.content.repository.ProductContentImportMapper;
import com.zhul.erp.modules.product.content.repository.ProductContentTaskMapper;
import com.zhul.erp.modules.product.content.repository.ProductPlatformContentMapper;
import com.zhul.erp.modules.product.content.repository.ProductFactNoteMapper;
import com.zhul.erp.modules.product.content.repository.ProductKeywordMapper;
import com.zhul.erp.modules.product.content.repository.ProductKeywordUseMapper;
import com.zhul.erp.modules.product.content.repository.ProductSeoFaqMapper;
import com.zhul.erp.modules.product.content.repository.ProductSeoMapper;
import com.zhul.erp.modules.product.content.support.MarketingPlatform;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.support.LikeUtils;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.product.support.ProductFinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 内容任务：列表（阶段按资料任务与本公司启用平台的营销写入、不发布标记计算）、本公司任务行的按需创建与营销写入状态、商品详情里的内容 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentTaskService {

    private final ProductContentTaskMapper taskMapper;
    private final ProductContentImportMapper importMapper;
    private final ProductSeoMapper seoMapper;
    private final ProductSeoFaqMapper faqMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductFinder productFinder;
    private final FactTaskService factTaskService;
    private final PlatformSettingService platformSettings;
    private final MarketingSkipService skipService;
    private final ProductPlatformContentMapper platformContentMapper;
    private final ProductKeywordMapper keywordMapper;
    private final ProductKeywordUseMapper keywordUseMapper;
    private final ProductFactNoteMapper factNoteMapper;
    private final ObjectMapper objectMapper;

    /** 入口：资料任务看资料阶段，营销任务看营销阶段（spec product/product-content「内容任务」） */
    public enum View { FACTS, MARKETING }

    /** 页签 → 阶段列表；不认识的页签按全部处理 */
    static List<Integer> stagesOf(View view, String tab) {
        if (tab == null || tab.isBlank() || "all".equals(tab)) {
            return null;
        }
        return switch (view) {
            case FACTS -> switch (tab) {
                case "1" -> List.of(ContentConstants.STAGE_FACTS_PENDING);
                case "2" -> List.of(ContentConstants.STAGE_FACTS_REVIEW);
                case "verified" -> List.of(ContentConstants.STAGE_MARKETING, ContentConstants.STAGE_DONE);
                default -> null;
            };
            case MARKETING -> switch (tab) {
                case "unverified" -> List.of(ContentConstants.STAGE_FACTS_PENDING, ContentConstants.STAGE_FACTS_REVIEW);
                case "3" -> List.of(ContentConstants.STAGE_MARKETING);
                case "4" -> List.of(ContentConstants.STAGE_DONE);
                default -> null;
            };
        };
    }

    public PageResult<ContentTaskVO> page(ContentTaskQuery q, View view) {
        int page = q.getPage() == null || q.getPage() < 1 ? 1 : q.getPage();
        int size = q.getPageSize() == null || q.getPageSize() < 1 ? 20 : Math.min(q.getPageSize(), 200);
        String keyword = keyword(q.getKeyword());
        String mpn = mpn(q.getKeyword());
        Integer tenantId = TenantContext.getTenantId();
        PlatformFlags flags = PlatformFlags.of(platformSettings.enabled());
        List<Integer> stages = stagesOf(view, q.getTab());
        long total = taskMapper.count(tenantId, flags, stages, q.getBrandId(), q.getCategoryId(), keyword, mpn);
        List<ContentTaskRow> rows = total == 0 ? List.of()
                : taskMapper.page(tenantId, flags, stages, q.getBrandId(), q.getCategoryId(), keyword, mpn,
                (long) (page - 1) * size, size);
        Map<Long, String> brands = brandNames(rows.stream().map(ContentTaskRow::getBrandId).collect(Collectors.toSet()));
        Map<Long, String> categories = categoryNames(rows.stream().map(ContentTaskRow::getCategoryId).collect(Collectors.toSet()));
        Map<Long, Map<String, ProductMarketingSkipDO>> skips = skipService.skips(
                rows.stream().map(ContentTaskRow::getProductId).toList());
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
            vo.setEnAt(r.getEnAt());
            vo.setEnBy(r.getEnBy());
            vo.setRuAt(r.getRuAt());
            vo.setRuBy(r.getRuBy());
            vo.setAlibabaAt(r.getAlibabaAt());
            vo.setAlibabaBy(r.getAlibabaBy());
            vo.setMicAt(r.getMicAt());
            vo.setMicBy(r.getMicBy());
            vo.setSocialAt(r.getSocialAt());
            vo.setSocialBy(r.getSocialBy());
            vo.setSkips(skipVOs(skips.get(r.getProductId())));
            vo.setDownloadedAt(r.getDownloadedAt());
            vo.setFactImportedAt(r.getFactImportedAt());
            vo.setFactImportedBy(r.getFactImportedBy());
            vo.setFactVerifiedAt(r.getFactVerifiedAt());
            vo.setFactVerifiedBy(r.getFactVerifiedBy());
            vo.setFactDownloadedAt(r.getFactDownloadedAt());
            vo.setCreateTime(r.getCreateTime());
            vo.setCreateBy(r.getCreateBy());
            vo.setUpdateTime(r.getUpdateTime());
            vo.setUpdateBy(r.getUpdateBy());
            records.add(vo);
        }
        return PageResult.of(total, records);
    }

    /** 各页签数量：资料任务 all / 1 / 2 / verified，营销任务 all / unverified / 3 / 4 */
    public Map<String, Long> counts(ContentTaskQuery q, View view) {
        Map<String, Long> byStage = new LinkedHashMap<>();
        for (Map<String, Object> row : taskMapper.countByStatus(TenantContext.getTenantId(),
                PlatformFlags.of(platformSettings.enabled()), q.getBrandId(),
                q.getCategoryId(), keyword(q.getKeyword()), mpn(q.getKeyword()))) {
            byStage.merge(String.valueOf(row.get("status")), ((Number) row.get("cnt")).longValue(), Long::sum);
        }
        long s1 = byStage.getOrDefault("1", 0L);
        long s2 = byStage.getOrDefault("2", 0L);
        long s3 = byStage.getOrDefault("3", 0L);
        long s4 = byStage.getOrDefault("4", 0L);
        Map<String, Long> result = new LinkedHashMap<>();
        result.put("all", s1 + s2 + s3 + s4);
        if (view == View.FACTS) {
            result.put("1", s1);
            result.put("2", s2);
            result.put("verified", s3 + s4);
        } else {
            result.put("unverified", s1 + s2);
            result.put("3", s3);
            result.put("4", s4);
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
            try {
                taskMapper.insert(row);
            } catch (DuplicateKeyException e) {
                // 并发时另一个请求先插入了，锁住它即可
            }
            id = taskMapper.lockByProduct(tenantId, productId);
        }
        return taskMapper.selectById(id);
    }

    /** 记录本公司下载营销包时间（逐个商品独立，不锁商品） */
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
                row.setDownloadedAt(now);
                try {
                    taskMapper.insert(row);
                } catch (DuplicateKeyException e) {
                    // 并发插入，下载时间以另一个请求为准
                }
            }
        }
    }

    /** 标记本公司某平台（独立站按语言）营销内容已写入（调用方已锁住任务行） */
    public void markWritten(ProductContentTaskDO task, MarketingPlatform platform, String lang) {
        LocalDateTime now = LocalDateTime.now();
        String user = SecurityUtils.getCurrentUsername();
        switch (platform) {
            case ALIBABA -> {
                task.setAlibabaAt(now);
                task.setAlibabaBy(user);
            }
            case MIC -> {
                task.setMicAt(now);
                task.setMicBy(user);
            }
            case SOCIAL -> {
                task.setSocialAt(now);
                task.setSocialBy(user);
            }
            case SITE -> {
                if ("en".equals(lang)) {
                    task.setEnAt(now);
                    task.setEnBy(user);
                } else {
                    task.setRuAt(now);
                    task.setRuBy(user);
                }
            }
        }
        taskMapper.updateById(task);
    }

    private static Map<String, MarketingSkipVO> skipVOs(Map<String, ProductMarketingSkipDO> rows) {
        Map<String, MarketingSkipVO> result = new HashMap<>();
        if (rows != null) {
            rows.forEach((platform, r) -> {
                MarketingSkipVO v = new MarketingSkipVO();
                v.setMarkedBy(r.getMarkedBy());
                v.setMarkedAt(r.getMarkedAt());
                result.put(platform, v);
            });
        }
        return result;
    }

    /** 商品详情：任务阶段、资料状态（共享）、本公司启用平台的营销内容、不发布标记和最近一次上传 */
    public ProductContentVO productContent(Long productId) {
        productFinder.active(productId);
        Integer tenantId = TenantContext.getTenantId();
        ProductContentVO vo = new ProductContentVO();
        ProductContentTaskDO task = taskMapper.selectOne(new LambdaQueryWrapper<ProductContentTaskDO>()
                .eq(ProductContentTaskDO::getTenantId, tenantId)
                .eq(ProductContentTaskDO::getProductId, productId)
                .isNull(ProductContentTaskDO::getDeletedAt));
        ProductFactTaskDO fact = factTaskService.find(productId);
        Set<MarketingPlatform> enabled = platformSettings.enabled();
        Map<String, ProductMarketingSkipDO> skips = skipService.skips(List.of(productId))
                .getOrDefault(productId, Map.of());
        vo.setStatus(FactTaskService.stage(fact, task, enabled, skips.keySet()));
        enabled.forEach(p -> vo.getPlatforms().add(p.code()));
        vo.setSkips(skipVOs(skips));
        if (fact != null) {
            vo.setFactImportedAt(fact.getImportedAt());
            vo.setFactImportedBy(fact.getImportedBy());
            vo.setFactVerifiedAt(fact.getVerifiedAt());
            vo.setFactVerifiedBy(fact.getVerifiedBy());
        }
        if (task != null) {
            vo.setEnAt(task.getEnAt());
            vo.setRuAt(task.getRuAt());
            vo.setAlibabaAt(task.getAlibabaAt());
            vo.setMicAt(task.getMicAt());
            vo.setSocialAt(task.getSocialAt());
        }
        Map<String, ProductSeoDO> seo = seoMapper.selectList(new LambdaQueryWrapper<ProductSeoDO>()
                        .eq(ProductSeoDO::getTenantId, tenantId)
                        .eq(ProductSeoDO::getProductId, productId)
                        .isNull(ProductSeoDO::getDeletedAt))
                .stream().collect(Collectors.toMap(ProductSeoDO::getLang, Function.identity(), (a, b) -> a));
        List<ProductSeoFaqDO> faqs = faqMapper.selectList(new LambdaQueryWrapper<ProductSeoFaqDO>()
                .eq(ProductSeoFaqDO::getTenantId, tenantId)
                .eq(ProductSeoFaqDO::getProductId, productId)
                .isNull(ProductSeoFaqDO::getDeletedAt)
                .orderByAsc(ProductSeoFaqDO::getSortOrder)
                .orderByAsc(ProductSeoFaqDO::getId));
        List<ProductPlatformContentDO> platformRows = platformContentMapper.selectList(
                new LambdaQueryWrapper<ProductPlatformContentDO>()
                        .eq(ProductPlatformContentDO::getTenantId, tenantId)
                        .eq(ProductPlatformContentDO::getProductId, productId)
                        .isNull(ProductPlatformContentDO::getDeletedAt));
        Set<Long> importIds = new HashSet<>();
        seo.values().forEach(s -> importIds.add(s.getImportId()));
        platformRows.forEach(r -> importIds.add(r.getImportId()));
        importIds.remove(null);
        Map<Long, ProductContentImportDO> imports = importIds.isEmpty() ? Map.of()
                : importMapper.selectList(new LambdaQueryWrapper<ProductContentImportDO>()
                        .select(ProductContentImportDO::getId, ProductContentImportDO::getFileName,
                                ProductContentImportDO::getConfirmedBy, ProductContentImportDO::getCreateTime)
                        .in(ProductContentImportDO::getId, importIds))
                .stream().collect(Collectors.toMap(ProductContentImportDO::getId, Function.identity()));
        for (String lang : ContentConstants.MARKETING_LANGS) {
            ProductContentVO.LangContent c = new ProductContentVO.LangContent();
            ProductSeoDO s = seo.get(lang);
            if (s != null) {
                c.setSeoTitle(s.getSeoTitle());
                c.setMetaDescription(s.getMetaDescription());
                c.setLongDescription(s.getLongDescription());
                c.setGeoAnswer(s.getGeoAnswer());
                c.setH1(s.getH1());
                c.setUrlSlug(s.getUrlSlug());
                c.setAltTexts(readList(s.getAltTexts(), ProductContentVO.AltText.class));
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
        fillPlatformContents(vo, productId, tenantId, enabled, platformRows, imports);
        for (ProductFactNoteDO n : factNoteMapper.selectList(new LambdaQueryWrapper<ProductFactNoteDO>()
                .eq(ProductFactNoteDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductFactNoteDO::getProductId, productId)
                .isNull(ProductFactNoteDO::getDeletedAt)
                .orderByAsc(ProductFactNoteDO::getKind)
                .orderByAsc(ProductFactNoteDO::getSortOrder))) {
            ProductContentVO.FactNote note = new ProductContentVO.FactNote();
            note.setKind(n.getKind());
            note.setItem(n.getItem());
            note.setStatusText(n.getStatusText());
            note.setImpact(n.getImpact());
            vo.getFactNotes().add(note);
        }
        return vo;
    }

    /** 阿里国际站、中国制造网、社媒的内容，与关键词总库（选用只算启用的平台） */
    private void fillPlatformContents(ProductContentVO vo, Long productId, Integer tenantId,
                                      Set<MarketingPlatform> enabled, List<ProductPlatformContentDO> rows,
                                      Map<Long, ProductContentImportDO> imports) {
        List<ProductKeywordDO> keywords = keywordMapper.selectList(new LambdaQueryWrapper<ProductKeywordDO>()
                .eq(ProductKeywordDO::getTenantId, tenantId)
                .eq(ProductKeywordDO::getProductId, productId)
                .isNull(ProductKeywordDO::getDeletedAt)
                .orderByAsc(ProductKeywordDO::getSortOrder)
                .orderByAsc(ProductKeywordDO::getId));
        List<ProductKeywordUseDO> uses = keywordUseMapper.selectList(new LambdaQueryWrapper<ProductKeywordUseDO>()
                .eq(ProductKeywordUseDO::getTenantId, tenantId)
                .eq(ProductKeywordUseDO::getProductId, productId)
                .isNull(ProductKeywordUseDO::getDeletedAt)
                .orderByAsc(ProductKeywordUseDO::getSortOrder)
                .orderByAsc(ProductKeywordUseDO::getId));
        Map<Long, ProductKeywordDO> byId = keywords.stream()
                .collect(Collectors.toMap(ProductKeywordDO::getId, Function.identity()));
        for (ProductPlatformContentDO r : rows) {
            MarketingPlatform platform = MarketingPlatform.of(r.getPlatform());
            if (platform == null || !enabled.contains(platform)) {
                continue;
            }
            ProductContentVO.PlatformContent c = new ProductContentVO.PlatformContent();
            c.setTitle(r.getTitle());
            c.setTitleAlternates(readList(r.getTitleAlternates(), ProductContentVO.TitleAlternate.class));
            c.setAttributes(readList(r.getAttributes(), ProductContentVO.Attribute.class));
            if (StringUtils.hasText(r.getSellingPoints())) {
                c.getSellingPoints().addAll(List.of(r.getSellingPoints().split("\n")));
            }
            c.setDetail(r.getDetail());
            c.setFaqs(readList(r.getFaqs(), ProductContentVO.Faq.class));
            c.setCta(r.getCta());
            c.setPosts(readList(r.getPosts(), ProductContentVO.Post.class));
            c.setVideos(readList(r.getVideos(), ProductContentVO.Video.class));
            c.setLinkedinPost(r.getLinkedinPost());
            c.setFacebookPost(r.getFacebookPost());
            c.setYoutubeDescription(r.getYoutubeDescription());
            for (ProductKeywordUseDO u : uses) {
                ProductKeywordDO k = byId.get(u.getKeywordId());
                if (k != null && platform.code().equals(u.getPlatform())) {
                    ProductContentVO.Keyword kw = new ProductContentVO.Keyword();
                    kw.setKeyword(k.getKeyword());
                    kw.setTier(k.getTier());
                    c.getKeywords().add(kw);
                }
            }
            ProductContentImportDO imp = r.getImportId() == null ? null : imports.get(r.getImportId());
            if (imp != null) {
                c.setFileName(imp.getFileName());
                c.setConfirmedBy(imp.getConfirmedBy());
                c.setConfirmedAt(imp.getCreateTime());
            }
            vo.getPlatformContents().put(platform.code(), c);
        }
        vo.setKeywordLibrary(keywordLibrary(keywords, uses, enabled));
    }

    /**
     * 关键词总库：全部词按档位、库内顺序排序；每个词标出被哪些启用平台选用（按平台固定顺序），
     * 没启用的平台的选用不算。
     */
    static List<ProductContentVO.LibraryKeyword> keywordLibrary(List<ProductKeywordDO> keywords,
                                                                 List<ProductKeywordUseDO> uses,
                                                                 Set<MarketingPlatform> enabled) {
        Map<Long, Set<MarketingPlatform>> used = new HashMap<>();
        for (ProductKeywordUseDO u : uses) {
            MarketingPlatform platform = MarketingPlatform.of(u.getPlatform());
            if (platform != null && enabled.contains(platform)) {
                used.computeIfAbsent(u.getKeywordId(), x -> EnumSet.noneOf(MarketingPlatform.class)).add(platform);
            }
        }
        List<ProductContentVO.LibraryKeyword> result = new ArrayList<>(keywords.size());
        for (ProductKeywordDO k : keywords) {
            ProductContentVO.LibraryKeyword row = new ProductContentVO.LibraryKeyword();
            row.setKeyword(k.getKeyword());
            row.setTier(k.getTier());
            row.setCategory(k.getCategory());
            row.setIntent(k.getIntent());
            row.setBasis(k.getBasis());
            used.getOrDefault(k.getId(), Set.of()).stream().sorted().forEach(p -> row.getPlatforms().add(p.code()));
            result.add(row);
        }
        // 稳定排序：档位优先，同档保持库内顺序
        result.sort(Comparator.comparingInt(ProductContentVO.LibraryKeyword::getTier));
        return result;
    }

    /** JSON 文本列 → 列表；空或格式不对时为空列表（只影响显示） */
    private <T> List<T> readList(String json, Class<T> type) {
        if (!StringUtils.hasText(json)) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, type));
        } catch (JsonProcessingException e) {
            log.warn("内容 JSON 解析失败，按空处理，type={}", type.getSimpleName(), e);
            return new ArrayList<>();
        }
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
