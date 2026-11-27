package com.zhul.erp.modules.website.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.ResultCode;
import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductReferencePriceDO;
import com.zhul.erp.modules.product.entity.ProductSeriesDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.repository.ProductReferencePriceMapper;
import com.zhul.erp.modules.product.repository.ProductSeriesMapper;
import com.zhul.erp.modules.system.service.ExchangeRateService;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.entity.WebsiteProductDO;
import com.zhul.erp.modules.website.entity.WebsiteSiteDO;
import com.zhul.erp.modules.website.repository.WebsiteProductMapper;
import com.zhul.erp.modules.website.support.SitePrice;
import com.zhul.erp.modules.website.support.SiteSlug;
import com.zhul.erp.modules.website.support.SiteStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 站点商品（spec website-publish「站点商品」）：链接别名、售价及来源、站点状态。
 * 链接别名按站点约定由品牌、系列、型号算出（与站点构建时的计算一致，见 SiteSlug），第一次发布成功或导入后锁定；
 * 站内唯一不靠唯一索引，由发布前检查（冲突时阻断）和同一站点同一时间只执行一张发布单保证。
 * 售价精度：BigDecimal 全程计算，最终 HALF_UP 保留 2 位（SitePrice）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SiteProductService {

    /** 询价最低价取最近多少天的回价 */
    public static final int QUOTE_DAYS = 180;
    private static final BigDecimal MAX_PRICE = new BigDecimal("99999999.99");

    private final WebsiteProductMapper mapper;
    private final ProductMapper productMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductSeriesMapper seriesMapper;
    private final ProductReferencePriceMapper referenceMapper;
    private final ExchangeRateService rates;
    private final ObjectMapper objectMapper;

    /** 站点 + 商品的站点商品；没有时为 null */
    public WebsiteProductDO find(Long siteId, Long productId) {
        return mapper.selectOne(new LambdaQueryWrapper<WebsiteProductDO>()
                .eq(WebsiteProductDO::getSiteId, siteId).eq(WebsiteProductDO::getProductId, productId)
                .isNull(WebsiteProductDO::getDeletedAt));
    }

    public WebsiteProductDO get(Long id) {
        WebsiteProductDO wp = id == null ? null : mapper.selectById(id);
        if (wp == null || wp.getDeletedAt() != null) {
            throw new BizException(ResultCode.NOT_FOUND, "站点商品不存在");
        }
        return wp;
    }

    /**
     * 建立或刷新站点商品：没有就新建；没锁定的链接别名按当前品牌、系列、型号重算；售价按当前数据重算。
     * 由预览、发布在各自的事务里调用。
     */
    public WebsiteProductDO ensure(WebsiteSiteDO site, ListingTaskDO task) {
        ProductDO product = productMapper.selectById(task.getProductId());
        if (product == null) {
            throw new BizException(ResultCode.NOT_FOUND, "商品不存在");
        }
        WebsiteProductDO wp = find(site.getId(), product.getId());
        boolean isNew = wp == null;
        if (isNew) {
            wp = new WebsiteProductDO();
            wp.setTenantId(site.getTenantId());
            wp.setSiteId(site.getId());
            wp.setProductId(product.getId());
            wp.setSlugLocked(0);
            wp.setSiteStatus("");
            wp.setLastReleaseId(0L);
        }
        wp.setListingTaskId(task.getId());
        if (!locked(wp)) {
            wp.setSlug(computedSlug(product));
        }
        SitePrice.Result price = price(site, product, wp.getManualPrice());
        wp.setCalcPrice(price.amount());
        wp.setPriceSource(json(priceSource(price)));
        if (isNew) {
            mapper.insert(wp);
        } else {
            mapper.updateById(wp);
        }
        return wp;
    }

    /** 按站点约定算出的链接别名：品牌、系列、型号 */
    public String computedSlug(ProductDO product) {
        ProductBrandDO brand = product.getBrandId() == null ? null : brandMapper.selectById(product.getBrandId());
        ProductSeriesDO series = product.getSeriesId() == null ? null : seriesMapper.selectById(product.getSeriesId());
        return SiteSlug.of(brand == null ? "" : brand.getBrandName(), series == null ? "" : series.getSeriesName(), product.getMpnRaw());
    }

    /** 同一站点里另有商品用了这个链接别名 */
    public boolean slugConflict(Long siteId, String slug, Long selfId) {
        return !StringUtils.hasText(slug) || mapper.selectCount(new LambdaQueryWrapper<WebsiteProductDO>()
                .eq(WebsiteProductDO::getSiteId, siteId).eq(WebsiteProductDO::getSlug, slug)
                .ne(selfId != null, WebsiteProductDO::getId, selfId).isNull(WebsiteProductDO::getDeletedAt)) > 0;
    }

    /** 第一次发布成功或导入后锁定链接别名 */
    public void lockSlug(WebsiteProductDO wp) {
        if (!locked(wp)) {
            wp.setSlugLocked(1);
            mapper.updateById(wp);
        }
    }

    public static boolean locked(WebsiteProductDO wp) {
        return Integer.valueOf(1).equals(wp.getSlugLocked());
    }

    /** 站点状态：手工改的优先，否则按生命周期 */
    public String siteStatus(WebsiteProductDO wp, ProductDO product) {
        return SiteStatus.of(wp.getSiteStatus(), product.getLifecycleStatus());
    }

    // ================================================================ 手工售价、站点状态

    /** 手工售价：为空表示改回按规则计算；金额 HALF_UP 保留 2 位 */
    @Transactional(rollbackFor = Exception.class)
    public WebsiteProductDO setManualPrice(Long id, BigDecimal price) {
        WebsiteProductDO wp = get(id);
        if (price != null && (price.signum() <= 0 || price.compareTo(MAX_PRICE) > 0)) {
            throw new BizException("售价要大于 0");
        }
        wp.setManualPrice(price == null ? null : price.setScale(2, RoundingMode.HALF_UP));
        markErp(wp, "price");
        mapper.updateById(wp);
        log.info("修改站点售价，websiteProductId={}，手工={}", id, wp.getManualPrice() != null);
        return wp;
    }

    /** 站点状态：只能手工改成 legacy，或者清空改回按生命周期 */
    @Transactional(rollbackFor = Exception.class)
    public WebsiteProductDO setSiteStatus(Long id, String status) {
        WebsiteProductDO wp = get(id);
        String v = status == null ? "" : status.trim();
        if (!v.isEmpty() && !SiteStatus.LEGACY.equals(v)) {
            throw new BizException("站点状态只能手工改成 legacy，其他按商品生命周期");
        }
        wp.setSiteStatus(v);
        markErp(wp, "status");
        mapper.updateById(wp);
        return wp;
    }

    /** 已导入的商品手工改了售价、状态：这一组此后以 ERP 为准（没有对应的更新时间可比） */
    private void markErp(WebsiteProductDO wp, String group) {
        if (wp.getImportedAt() == null) {
            return;
        }
        try {
            Set<String> all = new LinkedHashSet<>(wp.getErpFields() == null ? List.of()
                    : objectMapper.readValue(wp.getErpFields(), new TypeReference<List<String>>() { }));
            if (all.add(group)) {
                wp.setErpFields(objectMapper.writeValueAsString(all));
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("改用 ERP 内容的字段读写失败", e);
        }
    }

    // ================================================================ 售价

    public SitePrice.Result price(WebsiteSiteDO site, ProductDO product, BigDecimal manual) {
        SitePrice.Quote quote = lowestQuote(product.getId());
        ExchangeRateService.Snapshot usd = rates.find(WebsiteConstants.CURRENCY);
        SitePrice.Rate rate = usd == null ? null
                : new SitePrice.Rate(usd.rate(), usd.rateTime() == null ? null : usd.rateTime().toLocalDate());
        ProductReferencePriceDO ref = referenceMapper.selectOne(new LambdaQueryWrapper<ProductReferencePriceDO>()
                .eq(ProductReferencePriceDO::getProductId, product.getId()).isNull(ProductReferencePriceDO::getDeletedAt)
                .last("LIMIT 1"));
        SitePrice.Reference reference = ref == null ? null
                : new SitePrice.Reference(ref.getPriceOriginal(), ref.getCurrencyCode(), ref.getPriceCny());
        return SitePrice.calc(manual, quote, site.getPriceFactor(), rate, reference);
    }

    private SitePrice.Quote lowestQuote(Long productId) {
        Map<String, Object> row = mapper.lowestQuote(productId, LocalDateTime.now().minusDays(QUOTE_DAYS));
        if (row == null) {
            return null;
        }
        Object at = row.get("quoted_at");
        LocalDateTime quotedAt = at instanceof Timestamp t ? t.toLocalDateTime() : at instanceof LocalDateTime l ? l : null;
        return new SitePrice.Quote(((Number) row.get("quote_id")).longValue(), (BigDecimal) row.get("unit_price_cny"), quotedAt);
    }

    /** 售价来源 JSON：{"type":"quote","quoteId":1,"unitPriceCny":"760.00","factor":"1.05","rate":"7.100000","rateDate":"2026-11-27"} */
    static Map<String, Object> priceSource(SitePrice.Result r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", r.source());
        if (SitePrice.SOURCE_QUOTE.equals(r.source())) {
            m.put("quoteId", r.quoteId());
            m.put("unitPriceCny", plain(r.unitPriceCny()));
            m.put("factor", plain(r.factor()));
        }
        if (r.rate() != null) {
            m.put("rate", plain(r.rate()));
            m.put("rateDate", r.rateDate() == null ? null : r.rateDate().toString());
        }
        return m;
    }

    /** 来源的中文名，用于预览显示 */
    public static String sourceName(String type) {
        return switch (Objects.toString(type, "")) {
            case SitePrice.SOURCE_MANUAL -> "手工售价";
            case SitePrice.SOURCE_QUOTE -> "询价最低价";
            case SitePrice.SOURCE_REFERENCE -> "平台参考价";
            case SitePrice.SOURCE_IMPORT -> "站点导入";
            default -> "无（站点显示询价）";
        };
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.toPlainString();
    }

    private String json(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("售价来源序列化失败", e);
        }
    }
}
