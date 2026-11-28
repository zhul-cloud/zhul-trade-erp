package com.zhul.erp.modules.website.service;

import org.springframework.context.ApplicationEventPublisher;
import com.zhul.erp.modules.website.support.SiteContentChanged;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.ResultCode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import com.zhul.erp.modules.media.entity.MediaItemDO;
import com.zhul.erp.modules.media.repository.MediaItemMapper;
import com.zhul.erp.modules.media.service.MediaIndexer;
import com.zhul.erp.modules.product.content.entity.ProductContentTaskDO;
import com.zhul.erp.modules.product.content.entity.ProductLocaleDO;
import com.zhul.erp.modules.product.content.entity.ProductRelationshipNoteDO;
import com.zhul.erp.modules.product.content.entity.ProductSeoDO;
import com.zhul.erp.modules.product.content.entity.ProductSeoFaqDO;
import com.zhul.erp.modules.product.content.repository.ProductContentTaskMapper;
import com.zhul.erp.modules.product.content.repository.ProductLocaleMapper;
import com.zhul.erp.modules.product.content.repository.ProductRelationshipNoteMapper;
import com.zhul.erp.modules.product.content.repository.ProductSeoFaqMapper;
import com.zhul.erp.modules.product.content.repository.ProductSeoMapper;
import com.zhul.erp.modules.product.entity.ProductApplicationDO;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductFaqDO;
import com.zhul.erp.modules.product.entity.ProductRelationshipDO;
import com.zhul.erp.modules.product.entity.ProductSeriesDO;
import com.zhul.erp.modules.product.entity.ProductSpecificationDO;
import com.zhul.erp.modules.product.repository.ProductApplicationMapper;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductFaqMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.repository.ProductRelationshipMapper;
import com.zhul.erp.modules.product.repository.ProductSeriesMapper;
import com.zhul.erp.modules.product.repository.ProductSpecificationMapper;
import com.zhul.erp.modules.website.entity.WebsiteProductDO;
import com.zhul.erp.modules.website.entity.WebsiteSiteDO;
import com.zhul.erp.modules.website.repository.WebsiteProductMapper;
import com.zhul.erp.modules.website.support.SitePrice;
import com.zhul.erp.modules.website.support.SiteEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 站点数据里的一个商品（spec website-publish「发布预览与检查」内容取值，website-import「站点原有内容与 ERP 内容的取舍」）。
 * 取值：英文、俄文营销内容取本公司写入的内容（product_seo、product_seo_faq）；一句话规格取多语言摘要；
 * 规格、应用场景、型号关系取共享商品资料；FAQ 先取本公司的英文 FAQ，没有时取商品资料里已审核的；
 * 图片视频见 SiteMediaService；售价、状态、链接别名见 SiteProductService。
 * 站点数据约定第 1 版没有 SEO 标题字段（站点自己生成 title），所以 SEO 标题只做检查不导出。
 */
@Service
@RequiredArgsConstructor
public class SiteDataService {

    /** 站点关系类型（与 product_relationship.relationship_type 1-6 顺序一致） */
    private static final Map<Integer, String> COMPAT_TYPES = Map.of(1, "direct", 2, "successor", 3, "functional",
            4, "compatible", 5, "cross_reference", 6, "same_series");
    /** 置信度低、未知的关系不标类型（不作「推荐替代」类强断言） */
    private static final int WEAK_CONFIDENCE = 4;
    private static final int FAQ_REVIEWED = 2;
    private static final int FAQ_TEMPLATE = 1;

    private final SiteProductService siteProducts;
    private final ApplicationEventPublisher events;
    private final SiteMediaService siteMedia;
    private final WebsiteSiteService sites;
    private final WebsiteProductMapper websiteProductMapper;
    private final ProductMapper productMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductSeriesMapper seriesMapper;
    private final ProductSeoMapper seoMapper;
    private final ProductSeoFaqMapper seoFaqMapper;
    private final ProductLocaleMapper localeMapper;
    private final ProductContentTaskMapper contentTaskMapper;
    private final ProductSpecificationMapper specMapper;
    private final ProductApplicationMapper appMapper;
    private final ProductFaqMapper faqMapper;
    private final ProductRelationshipMapper relMapper;
    private final ProductRelationshipNoteMapper relNoteMapper;
    private final MediaItemMapper mediaItemMapper;
    private final ObjectMapper objectMapper;

    /** 一个商品的站点数据与 ERP 一侧的原始取值（预览检查用） */
    public record Entry(Map<String, Object> data, Map<String, Object> erp, Map<String, LocalDateTime> erpUpdated,
                        Set<String> erpGroups, SiteMediaService.Result media, SitePrice.Result price, ProductDO product,
                        String seoTitleEn, String seoTitleRu) {
    }

    /**
     * 生成站点数据里的一个商品。copyMedia=false 只算素材链接不复制（预览），发布时为 true。
     * release 为这次发布单号，预览时传 null。
     */
    public Entry build(WebsiteSiteDO site, ListingTaskDO task, WebsiteProductDO wp, String release, boolean copyMedia) {
        ProductDO p = productMapper.selectById(task.getProductId());
        if (p == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        Integer tenantId = TenantContext.getTenantId();
        ProductBrandDO brand = p.getBrandId() == null ? null : brandMapper.selectById(p.getBrandId());
        ProductSeriesDO series = p.getSeriesId() == null ? null : seriesMapper.selectById(p.getSeriesId());
        String brandName = brand == null ? "" : brand.getBrandName();

        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("brand", brandName);
        identity.put("model", p.getMpnRaw());
        identity.put("series", series == null ? "" : series.getSeriesName());
        identity.put("cat", sites.siteCategory(site.getId(), p.getCategoryId()));
        identity.put("slug", wp.getSlug());
        identity.put("release", release);

        Map<String, Object> erp = new LinkedHashMap<>();
        Map<String, LocalDateTime> updated = new HashMap<>();

        // 一句话规格（英文、俄文）
        Map<String, ProductLocaleDO> locales = localeMapper.selectList(new LambdaQueryWrapper<ProductLocaleDO>()
                        .eq(ProductLocaleDO::getProductId, p.getId()))
                .stream().collect(Collectors.toMap(ProductLocaleDO::getLang, Function.identity(), (a, b) -> a));
        ProductLocaleDO en = locales.get("en");
        ProductLocaleDO ru = locales.get("ru");
        erp.put("spec", en != null && StringUtils.hasText(en.getSpecSummary()) ? en.getSpecSummary() : p.getSpecSummary());
        updated.put("spec", en == null ? null : en.getUpdateTime());
        erp.put("spec_ru", ru == null ? null : ru.getSpecSummary());
        updated.put("spec_ru", ru == null ? null : ru.getUpdateTime());

        // 营销内容
        ProductContentTaskDO contentTask = contentTaskMapper.selectOne(new LambdaQueryWrapper<ProductContentTaskDO>()
                .eq(ProductContentTaskDO::getTenantId, tenantId).eq(ProductContentTaskDO::getProductId, p.getId())
                .isNull(ProductContentTaskDO::getDeletedAt));
        Map<String, ProductSeoDO> seo = seoMapper.selectList(new LambdaQueryWrapper<ProductSeoDO>()
                        .eq(ProductSeoDO::getTenantId, tenantId).eq(ProductSeoDO::getProductId, p.getId())
                        .isNull(ProductSeoDO::getDeletedAt))
                .stream().collect(Collectors.toMap(ProductSeoDO::getLang, Function.identity(), (a, b) -> a));
        ProductSeoDO seoEn = seo.get("en");
        ProductSeoDO seoRu = seo.get("ru");
        erp.put("overview", seoEn == null ? null : seoEn.getGeoAnswer());
        erp.put("meta_description", seoEn == null ? null : seoEn.getMetaDescription());
        erp.put("overview_ru", seoRu == null ? null : seoRu.getGeoAnswer());
        erp.put("meta_description_ru", seoRu == null ? null : seoRu.getMetaDescription());
        LocalDateTime enAt = contentTask == null ? null : contentTask.getEnAt();
        LocalDateTime ruAt = contentTask == null ? null : contentTask.getRuAt();
        updated.put("content_en", enAt);
        updated.put("content_ru", ruAt);

        // 规格、应用场景（英文、俄文）
        List<ProductSpecificationDO> specs = specMapper.selectList(new LambdaQueryWrapper<ProductSpecificationDO>()
                .eq(ProductSpecificationDO::getProductId, p.getId())
                .orderByAsc(ProductSpecificationDO::getSortOrder).orderByAsc(ProductSpecificationDO::getId));
        List<ProductApplicationDO> apps = appMapper.selectList(new LambdaQueryWrapper<ProductApplicationDO>()
                .eq(ProductApplicationDO::getProductId, p.getId())
                .orderByAsc(ProductApplicationDO::getSortOrder).orderByAsc(ProductApplicationDO::getId));
        erp.put("specs", specRows(specs, "en"));
        erp.put("applications", appRows(apps, "en"));
        updated.put("specs", latest(specs.stream().filter(s -> "en".equals(s.getLang())).map(ProductSpecificationDO::getUpdateTime)));
        updated.put("applications", latest(apps.stream().filter(a -> "en".equals(a.getLang())).map(ProductApplicationDO::getUpdateTime)));

        // FAQ：本公司英文 FAQ 优先，没有时用商品资料里已审核的
        List<ProductSeoFaqDO> seoFaqs = seoFaqMapper.selectList(new LambdaQueryWrapper<ProductSeoFaqDO>()
                .eq(ProductSeoFaqDO::getTenantId, tenantId).eq(ProductSeoFaqDO::getProductId, p.getId())
                .orderByAsc(ProductSeoFaqDO::getSortOrder).orderByAsc(ProductSeoFaqDO::getId));
        List<Map<String, Object>> faqEn = faqRows(seoFaqs, "en");
        if (!faqEn.isEmpty()) {
            erp.put("faq", faqEn);
            updated.put("faq", latest(Stream.concat(Stream.of(enAt),
                    seoFaqs.stream().filter(f -> "en".equals(f.getLang())).map(ProductSeoFaqDO::getUpdateTime))));
        } else {
            List<ProductFaqDO> faqs = faqMapper.selectList(new LambdaQueryWrapper<ProductFaqDO>()
                    .eq(ProductFaqDO::getProductId, p.getId())
                    .orderByAsc(ProductFaqDO::getSortOrder).orderByAsc(ProductFaqDO::getId));
            List<Map<String, Object>> rows = new ArrayList<>();
            for (ProductFaqDO f : faqs) {
                boolean usable = f.getDeletedAt() == null
                        && (Objects.equals(f.getSource(), FAQ_REVIEWED) || Objects.equals(f.getSource(), FAQ_TEMPLATE));
                if (usable) {
                    rows.add(qa(f.getQuestion(), f.getAnswer()));
                }
            }
            erp.put("faq", rows);
            updated.put("faq", latest(faqs.stream().map(ProductFaqDO::getUpdateTime)));
        }

        // 型号关系
        List<ProductRelationshipDO> rels = relMapper.selectList(new LambdaQueryWrapper<ProductRelationshipDO>()
                .eq(ProductRelationshipDO::getProductId, p.getId())
                .orderByAsc(ProductRelationshipDO::getSortOrder).orderByAsc(ProductRelationshipDO::getId));
        List<ProductRelationshipDO> liveRels = rels.stream().filter(r -> r.getDeletedAt() == null).toList();
        List<Map<String, Object>> compat = new ArrayList<>();
        for (ProductRelationshipDO r : liveRels) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("from", r.getRelatedMpn());
            String type = COMPAT_TYPES.get(r.getRelationshipType());
            if (type != null && (r.getConfidence() == null || r.getConfidence() < WEAK_CONFIDENCE)) {
                c.put("type", type);
            }
            c.put("note", r.getNote());
            compat.add(c);
        }
        erp.put("compatibility", compat);
        updated.put("compatibility", latest(rels.stream().map(ProductRelationshipDO::getUpdateTime)));

        // 俄文深度内容
        List<ProductRelationshipNoteDO> notes = liveRels.isEmpty() ? List.of()
                : relNoteMapper.selectList(new LambdaQueryWrapper<ProductRelationshipNoteDO>()
                .in(ProductRelationshipNoteDO::getRelationshipId, liveRels.stream().map(ProductRelationshipDO::getId).toList())
                .eq(ProductRelationshipNoteDO::getLang, "ru"));
        Map<Long, String> ruNotes = notes.stream().collect(Collectors.toMap(ProductRelationshipNoteDO::getRelationshipId,
                ProductRelationshipNoteDO::getNote, (a, b) -> a));
        Map<String, Object> deepRu = new LinkedHashMap<>();
        putIfAny(deepRu, "specs", specRows(specs, "ru"));
        putIfAny(deepRu, "applications", appRows(apps, "ru"));
        List<Map<String, Object>> compatRu = new ArrayList<>();
        for (ProductRelationshipDO r : liveRels) {
            String note = ruNotes.get(r.getId());
            if (StringUtils.hasText(note)) {
                compatRu.add(new LinkedHashMap<>(Map.of("from", r.getRelatedMpn(), "note", note)));
            }
        }
        putIfAny(deepRu, "compatibility", compatRu);
        putIfAny(deepRu, "faq", faqRows(seoFaqs, "ru"));
        erp.put("deep_ru", deepRu);
        updated.put("deep_ru", latest(Stream.of(
                ruAt,
                latest(specs.stream().filter(s -> "ru".equals(s.getLang())).map(ProductSpecificationDO::getUpdateTime)),
                latest(apps.stream().filter(a -> "ru".equals(a.getLang())).map(ProductApplicationDO::getUpdateTime)),
                latest(notes.stream().map(ProductRelationshipNoteDO::getUpdateTime)))));

        // 图片视频
        SiteMediaService.Result media = siteMedia.of(site.getId(), wp.getSlug(), task, brandName + " " + p.getMpnRaw(), copyMedia);
        erp.put("photos", media.photos());
        erp.put("photo", media.photos().isEmpty() ? null : media.photos().get(0).get("src"));
        erp.put("videos", media.videos());
        updated.put("media", mediaUpdated(tenantId, task));

        // 售价、状态
        SitePrice.Result price = siteProducts.price(site, p, wp.getManualPrice());
        if (price.amount() != null) {
            erp.put("sell_price", price.amount());
            erp.put("sell_price_currency", site.getCurrencyCode());
            erp.put("price_source", "erp:" + price.source());
        }
        updated.put("price", SitePrice.SOURCE_QUOTE.equals(price.source()) ? price.quotedAt() : null);
        erp.put("status", siteProducts.siteStatus(wp, p));

        Set<String> erpGroups = erpFields(wp);
        Map<String, Object> original = originalOf(wp);
        Map<String, Object> data = SiteEntry.build(identity, erp, updated, original, wp.getImportedAt(), erpGroups);
        return new Entry(data, erp, updated, erpGroups, media, price, p,
                seoEn == null ? null : seoEn.getSeoTitle(), seoRu == null ? null : seoRu.getSeoTitle());
    }

    // ================================================================ 改用 ERP 内容

    /** 已导入的站点商品：这些字段组此后以 ERP 为准 */
    @Transactional(rollbackFor = Exception.class)
    public WebsiteProductDO useErp(Long websiteProductId, Collection<String> groups) {
        WebsiteProductDO wp = siteProducts.get(websiteProductId);
        for (String g : groups) {
            if (!SiteEntry.groupNames().contains(g)) {
                throw new BizException("没有这个字段：" + g);
            }
        }
        Set<String> all = erpFields(wp);
        all.addAll(groups);
        wp.setErpFields(json(new ArrayList<>(all)));
        websiteProductMapper.updateById(wp);
        events.publishEvent(SiteContentChanged.product(wp.getProductId()));
        return wp;
    }

    public Set<String> erpFields(WebsiteProductDO wp) {
        if (!StringUtils.hasText(wp.getErpFields())) {
            return new LinkedHashSet<>();
        }
        try {
            return new LinkedHashSet<>(objectMapper.readValue(wp.getErpFields(), new TypeReference<List<String>>() { }));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("改用 ERP 内容的字段读取失败", e);
        }
    }

    public Map<String, Object> originalOf(WebsiteProductDO wp) {
        if (!StringUtils.hasText(wp.getOriginalContent())) {
            return null;
        }
        try {
            return objectMapper.readValue(wp.getOriginalContent(), new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("站点原有内容读取失败", e);
        }
    }

    // ================================================================ 工具

    /** 这个任务用的素材最近一次变化（标记、新增、删除） */
    private LocalDateTime mediaUpdated(Integer tenantId, ListingTaskDO task) {
        String key = StringUtils.hasText(task.getRefModel()) ? MediaIndexer.key(task.getRefBrand(), task.getRefModel()) : task.getAssetKey();
        if (!StringUtils.hasText(key)) {
            return null;
        }
        MediaItemDO last = mediaItemMapper.selectOne(new LambdaQueryWrapper<MediaItemDO>()
                .select(MediaItemDO::getUpdateTime)
                .eq(MediaItemDO::getTenantId, tenantId).eq(MediaItemDO::getAssetKey, key)
                .orderByDesc(MediaItemDO::getUpdateTime).last("LIMIT 1"));
        LocalDateTime refAt = StringUtils.hasText(task.getRefModel()) ? task.getRefTime() : null;
        return latest(Stream.of(last == null ? null : last.getUpdateTime(), refAt));
    }

    private static List<List<String>> specRows(List<ProductSpecificationDO> specs, String lang) {
        List<List<String>> out = new ArrayList<>();
        for (ProductSpecificationDO s : specs) {
            if (s.getDeletedAt() == null && lang.equals(s.getLang()) && StringUtils.hasText(s.getSpecValue())) {
                String v = StringUtils.hasText(s.getSpecUnit()) ? s.getSpecValue() + " " + s.getSpecUnit() : s.getSpecValue();
                out.add(List.of(s.getSpecLabel(), v));
            }
        }
        return out;
    }

    private static List<Map<String, Object>> appRows(List<ProductApplicationDO> apps, String lang) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ProductApplicationDO a : apps) {
            if (a.getDeletedAt() == null && lang.equals(a.getLang())) {
                Map<String, Object> m = new LinkedHashMap<>();
                if (StringUtils.hasText(a.getIcon())) {
                    m.put("icon", a.getIcon());
                }
                m.put("title", a.getTitle());
                m.put("desc", a.getDescription());
                out.add(m);
            }
        }
        return out;
    }

    private static List<Map<String, Object>> faqRows(List<ProductSeoFaqDO> faqs, String lang) {
        return faqs.stream().filter(f -> f.getDeletedAt() == null && lang.equals(f.getLang()))
                .map(f -> qa(f.getQuestion(), f.getAnswer())).toList();
    }

    private static Map<String, Object> qa(String q, String a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("q", q);
        m.put("a", a);
        return m;
    }

    private static void putIfAny(Map<String, Object> m, String key, Collection<?> v) {
        if (!v.isEmpty()) {
            m.put(key, v);
        }
    }

    private static LocalDateTime latest(java.util.stream.Stream<LocalDateTime> times) {
        return times.filter(Objects::nonNull).max(LocalDateTime::compareTo).orElse(null);
    }

    private String json(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化失败", e);
        }
    }
}
