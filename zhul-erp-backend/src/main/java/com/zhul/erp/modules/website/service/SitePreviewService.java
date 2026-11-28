package com.zhul.erp.modules.website.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.listing.constants.ListingConstants;
import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import com.zhul.erp.modules.listing.repository.ListingTaskMapper;
import com.zhul.erp.modules.product.content.entity.ProductFactTaskDO;
import com.zhul.erp.modules.product.content.service.FactTaskService;
import com.zhul.erp.modules.product.content.support.MarketingPlatform;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.website.constants.WebsiteConstants;
import com.zhul.erp.modules.website.dto.PreviewVO;
import com.zhul.erp.modules.website.dto.SiteLanguage;
import com.zhul.erp.modules.website.entity.WebsiteProductDO;
import com.zhul.erp.modules.website.entity.WebsiteSiteDO;
import com.zhul.erp.modules.website.support.SiteEntry;
import com.zhul.erp.modules.website.support.SitePrice;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 发布预览与检查（spec website-publish「发布预览与检查」）：逐个商品生成站点数据，列出阻断项、提醒项和与上次发布的差异。
 * 预览会建立或刷新站点商品（链接别名、计算售价），不复制素材。
 */
@Service
@RequiredArgsConstructor
public class SitePreviewService {

    private static final Map<String, String> LANG_NAMES = Map.of("en", "英文", "ru", "俄文");

    private final WebsiteSiteService sites;
    private final SiteProductService siteProducts;
    private final SiteDataService siteData;
    private final ListingTaskMapper taskMapper;
    private final FactTaskService factTasks;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public List<PreviewVO> preview(Long siteId, Collection<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            throw new BizException("请选择要发布的商品");
        }
        WebsiteSiteDO site = sites.get(siteId);
        if (!Objects.equals(site.getStatus(), WebsiteConstants.STATUS_ENABLED)) {
            throw new BizException("站点已停用");
        }
        List<PreviewVO> out = new ArrayList<>();
        Map<String, Integer> slugCount = new HashMap<>();
        for (Long id : new LinkedHashSet<>(taskIds)) {
            PreviewVO vo = one(site, task(siteId, id));
            slugCount.merge(vo.getSlug(), 1, Integer::sum);
            out.add(vo);
        }
        for (PreviewVO vo : out) {
            if (slugCount.getOrDefault(vo.getSlug(), 0) > 1 && !vo.getBlockers().contains(conflict(vo.getSlug()))) {
                vo.getBlockers().add(conflict(vo.getSlug()));
            }
        }
        return out;
    }

    private ListingTaskDO task(Long siteId, Long id) {
        ListingTaskDO t = id == null ? null : taskMapper.selectById(id);
        if (t == null || t.getDeletedAt() != null || !MarketingPlatform.SITE.code().equals(t.getPlatform())
                || !Objects.equals(t.getSiteId(), siteId)) {
            throw new BizException("发品任务不属于这个站点");
        }
        if (Objects.equals(t.getStatus(), ListingConstants.STATUS_CANCELLED)) {
            throw new BizException("发品任务已取消");
        }
        return t;
    }

    private PreviewVO one(WebsiteSiteDO site, ListingTaskDO task) {
        WebsiteProductDO wp = siteProducts.ensure(site, task);
        SiteDataService.Entry e = siteData.build(site, task, wp, null, false);
        Map<String, Object> data = e.data();
        data.remove("release");
        ProductDO p = e.product();

        PreviewVO vo = new PreviewVO();
        vo.setTaskId(task.getId());
        vo.setWebsiteProductId(wp.getId());
        vo.setProductId(p.getId());
        vo.setBrand((String) data.get("brand"));
        vo.setModel(p.getMpnRaw());
        vo.setSlug(wp.getSlug());
        vo.setSiteCategory((String) data.get("cat"));
        vo.setSiteStatus((String) data.get("status"));
        vo.setReferenceMedia(e.media().reference());
        vo.setData(data);
        vo.setPrice(price(e.price(), site));
        List<SiteLanguage> langs = sites.languagesOf(site);
        for (SiteLanguage l : langs) {
            vo.getLiveUrls().put(l.code(), liveUrl(site, l, wp.getSlug()));
            vo.getSeoTitles().put(l.code(), "ru".equals(l.code()) ? e.seoTitleRu() : e.seoTitleEn());
        }

        // 阻断项
        List<String> b = vo.getBlockers();
        if (Objects.equals(site.getPublishMode(), WebsiteConstants.MODE_AUTO)
                && !Objects.equals(site.getTakeoverStatus(), WebsiteConstants.TAKEOVER_DONE)) {
            b.add("站点还没有切换为以 ERP 为准");
        }
        if (Objects.equals(task.getStatus(), ListingConstants.STATUS_PUBLISHING)) {
            b.add("正在发布中");
        }
        if (!StringUtils.hasText(vo.getSiteCategory())) {
            b.add("品类没有对照站点品类");
        }
        if (!StringUtils.hasText((String) data.get("spec"))) {
            b.add("缺少一句话规格");
        }
        Map<String, Object> original = siteData.originalOf(wp);
        for (SiteLanguage l : langs) {
            String group = "ru".equals(l.code()) ? "content_ru" : "content_en";
            if (!SiteEntry.useErp(group, e.erpUpdated(), original, wp.getImportedAt(), e.erpGroups())) {
                continue;   // 导入后没改过：沿用站点原有内容
            }
            String suffix = "ru".equals(l.code()) ? "_ru" : "";
            String name = LANG_NAMES.getOrDefault(l.code(), l.code());
            if (!StringUtils.hasText(vo.getSeoTitles().get(l.code()))) {
                b.add(name + "：缺少 SEO 标题");
            }
            if (!StringUtils.hasText((String) e.erp().get("meta_description" + suffix))) {
                b.add(name + "：缺少 SEO 描述");
            }
            if (!StringUtils.hasText((String) e.erp().get("overview" + suffix))) {
                b.add(name + "：缺少首屏定义块");
            }
        }
        if (empty(data.get("photos")) && !StringUtils.hasText((String) data.get("photo"))) {
            b.add("没有任何图片");
        }
        if (siteProducts.slugConflict(site.getId(), wp.getSlug(), wp.getId())) {
            b.add(conflict(wp.getSlug()));
        }
        if (SiteProductService.locked(wp) && !Objects.equals(wp.getSlug(), siteProducts.computedSlug(p))) {
            b.add("已发布商品的品牌、系列或型号改了，链接会变");
        }

        // 提醒项
        List<String> w = vo.getWarnings();
        if (data.get("sell_price") == null) {
            w.add("没有售价（站点显示询价）");
        }
        if (empty(data.get("videos"))) {
            w.add("没有视频");
        }
        if (p.getBrandId() != null && sites.nonGenuineOf(site).contains(p.getBrandId())) {
            w.add("品牌在非正品名单里（页面不出现正品说法）");
        }
        ProductFactTaskDO fact = factTasks.find(p.getId());
        if (fact == null || fact.getVerifiedAt() == null) {
            w.add("商品资料没有核对");
        }

        // 与上次发布的差异
        if (!StringUtils.hasText(wp.getLastSnapshot())) {
            vo.setFirstPublish(true);
        } else {
            vo.setChanges(changes(wp.getLastSnapshot(), data));
            SiteOutdatedService.mark(taskMapper, task, !vo.getChanges().isEmpty());
        }
        return vo;
    }

    /** 逐个字段比较（发布单号不算） */
    List<PreviewVO.Change> changes(String snapshot, Map<String, Object> data) {
        JsonNode before = read(snapshot);
        JsonNode after = objectMapper.valueToTree(data);
        Set<String> fields = new LinkedHashSet<>();
        before.fieldNames().forEachRemaining(fields::add);
        after.fieldNames().forEachRemaining(fields::add);
        fields.remove("release");
        List<PreviewVO.Change> out = new ArrayList<>();
        for (String f : fields) {
            JsonNode a = before.path(f);
            JsonNode c = after.path(f);
            if (!same(a, c)) {
                out.add(new PreviewVO.Change(f, a.isMissingNode() ? NullNode.getInstance() : a,
                        c.isMissingNode() ? NullNode.getInstance() : c));
            }
        }
        return out;
    }

    /** 数字按数值比较（246.10 与 246.1 相同）；没有、null、空字符串都算空 */
    private static boolean same(JsonNode a, JsonNode b) {
        if (blank(a) && blank(b)) {
            return true;
        }
        if (a.isNumber() && b.isNumber()) {
            return a.decimalValue().compareTo(b.decimalValue()) == 0;
        }
        if (a.isObject() && b.isObject()) {
            if (a.size() != b.size()) {
                return false;
            }
            for (Iterator<Map.Entry<String, JsonNode>> it = a.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> en = it.next();
                if (!b.has(en.getKey()) || !same(en.getValue(), b.get(en.getKey()))) {
                    return false;
                }
            }
            return true;
        }
        if (a.isArray() && b.isArray()) {
            if (a.size() != b.size()) {
                return false;
            }
            for (int i = 0; i < a.size(); i++) {
                if (!same(a.get(i), b.get(i))) {
                    return false;
                }
            }
            return true;
        }
        return a.equals(b);
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            return objectMapper.createObjectNode();
        }
    }

    /** 上线后的链接：网址 + 语言前缀 + 路径模板 */
    static String liveUrl(WebsiteSiteDO site, SiteLanguage l, String slug) {
        String prefix = l.prefix() == null ? "" : l.prefix();
        return site.getUrl() + prefix + site.getPathTemplate().replace("{slug}", slug == null ? "" : slug);
    }

    private static PreviewVO.Price price(SitePrice.Result r, WebsiteSiteDO site) {
        PreviewVO.Price p = new PreviewVO.Price();
        p.setAmount(r.amount());
        p.setCurrency(r.amount() == null ? null : site.getCurrencyCode());
        p.setSource(r.source());
        p.setSourceName(SiteProductService.sourceName(r.source()));
        p.setQuoteId(r.quoteId());
        p.setUnitPriceCny(r.unitPriceCny());
        p.setFactor(r.factor());
        p.setRate(r.rate());
        p.setRateDate(r.rateDate());
        return p;
    }

    private static boolean blank(JsonNode n) {
        return n.isMissingNode() || n.isNull() || (n.isTextual() && n.asText().isEmpty());
    }

    private static String conflict(String slug) {
        return "链接别名在站点内冲突：" + slug;
    }

    private static boolean empty(Object v) {
        return v == null || (v instanceof Collection<?> c && c.isEmpty());
    }
}
