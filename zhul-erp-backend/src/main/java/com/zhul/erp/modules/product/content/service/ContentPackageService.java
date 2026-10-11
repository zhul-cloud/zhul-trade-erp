package com.zhul.erp.modules.product.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.candidate.entity.ProductCandidateDO;
import com.zhul.erp.modules.product.candidate.repository.ProductCandidateMapper;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.content.entity.ProductFactNoteDO;
import com.zhul.erp.modules.product.content.entity.ProductKeywordDO;
import com.zhul.erp.modules.product.content.entity.ProductRelationshipNoteDO;
import com.zhul.erp.modules.product.content.repository.ProductFactNoteMapper;
import com.zhul.erp.modules.product.content.repository.ProductKeywordMapper;
import com.zhul.erp.modules.product.content.repository.ProductRelationshipNoteMapper;
import com.zhul.erp.modules.product.content.support.MarketingPlatform;
import com.zhul.erp.modules.product.entity.ProductApplicationDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductDocumentDO;
import com.zhul.erp.modules.product.entity.ProductFaqDO;
import com.zhul.erp.modules.product.entity.ProductRelationshipDO;
import com.zhul.erp.modules.product.entity.ProductSeriesDO;
import com.zhul.erp.modules.product.entity.ProductSpecificationDO;
import com.zhul.erp.modules.product.repository.ProductApplicationMapper;
import com.zhul.erp.modules.product.repository.ProductDocumentMapper;
import com.zhul.erp.modules.product.repository.ProductFaqMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.repository.ProductRelationshipMapper;
import com.zhul.erp.modules.product.repository.ProductSeriesMapper;
import com.zhul.erp.modules.product.repository.ProductSpecificationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 任务包（spec product/product-content「任务包下载」）：商品已有信息（生成输入）+ 上传模版，一个商品一个 Markdown，多个打成 zip。
 * 模版按《商品内容规范 v2》生成（frontmatter 带 schema: 2）。资料包用中文模版；营销包以中文资料为输入，
 * 默认包含本公司启用的全部平台（关键词库 + 各平台章节 + 内部附录），也可以只下载一个平台，或只含独立站的俄文文件。
 */
@Service
@RequiredArgsConstructor
public class ContentPackageService {

    /** 共享 FAQ 来源：AI 辅助生成待审核（不作为营销包的依据） */
    private static final int FAQ_SOURCE_PENDING = 3;

    private static final Map<Integer, String> LIFECYCLE = Map.of(1, "在产", 2, "现行", 3, "旧款", 4, "已停产",
            5, "停产无替代", 6, "未知");

    private final ProductMapper productMapper;
    private final ProductSeriesMapper seriesMapper;
    private final ProductSpecificationMapper specificationMapper;
    private final ProductCandidateMapper candidateMapper;
    private final ContentTaskService taskService;
    private final FactTaskService factTaskService;
    private final ProductApplicationMapper applicationMapper;
    private final ProductRelationshipMapper relationshipMapper;
    private final ProductRelationshipNoteMapper relationshipNoteMapper;
    private final ProductFaqMapper faqMapper;
    private final ProductDocumentMapper documentMapper;
    private final PlatformSettingService platformSettings;
    private final ProductFactNoteMapper factNoteMapper;
    private final ProductKeywordMapper keywordMapper;

    public record Package(String fileName, String contentType, byte[] body) {
    }

    /** 独立站俄文：营销包的 platform 参数取这个值时只含独立站章节，lang: ru */
    public static final String SITE_RU = "site-ru";

    /** 营销包的内容范围：包含哪些平台、语言、文件名后缀；资料包为空 */
    record Variant(List<MarketingPlatform> platforms, String lang, String suffix, boolean keywords) {
    }

    /** platform 为空：本公司启用的全部平台；site-ru：独立站俄文；其他：该平台（须已启用） */
    Variant variant(String platformCode) {
        if (!StringUtils.hasText(platformCode)) {
            List<MarketingPlatform> all = List.copyOf(platformSettings.enabled());
            if (all.isEmpty()) {
                throw BizException.of(ProductErrorCodes.PARAM_INVALID, "本公司还没有启用任何平台，请先在平台设置里启用");
            }
            return new Variant(all, "en", "marketing", true);
        }
        if (SITE_RU.equals(platformCode)) {
            platformSettings.requireEnabled(MarketingPlatform.SITE.code());
            return new Variant(List.of(MarketingPlatform.SITE), "ru", "site.ru", false);
        }
        MarketingPlatform platform = platformSettings.requireEnabled(platformCode);
        boolean keywords = platform == MarketingPlatform.ALIBABA || platform == MarketingPlatform.MIC;
        return new Variant(List.of(platform), "en", platform.code(), keywords);
    }

    /** platform 只对营销包有效，必须是本公司启用的平台 */
    public Package build(List<Long> productIds, String type, String platformCode) {
        boolean facts = ContentConstants.TYPE_FACTS.equals(type);
        if (!facts && !ContentConstants.TYPE_MARKETING.equals(type)) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "包类型须为 facts 或 marketing");
        }
        Variant variant = facts ? null : variant(platformCode);
        Set<Long> ids = new LinkedHashSet<>(productIds == null ? List.of() : productIds);
        ids.remove(null);
        if (ids.isEmpty()) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "请选择商品");
        }
        if (ids.size() > ContentConstants.MAX_PACKAGE_PRODUCTS) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID,
                    "一次最多下载 " + ContentConstants.MAX_PACKAGE_PRODUCTS + " 个任务包");
        }
        List<ProductDO> products = productMapper.selectList(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .in(ProductDO::getId, ids)
                .isNull(ProductDO::getDeletedAt)
                .orderByAsc(ProductDO::getId));
        if (products.size() != ids.size()) {
            throw BizException.of(ProductErrorCodes.PRODUCT_NOT_FOUND, "商品不存在");
        }
        String template = template(variant);
        Map<Long, String> brands = taskService.brandNames(new HashSet<>(products.stream().map(ProductDO::getBrandId).toList()));
        Map<Long, String> categories = taskService.categoryNames(new HashSet<>(products.stream().map(ProductDO::getCategoryId).toList()));

        if (facts) {
            factTaskService.markDownloaded(ids);
        } else {
            taskService.markDownloaded(ids);
        }
        if (products.size() == 1) {
            ProductDO p = products.get(0);
            return new Package(fileName(p, variant), "text/markdown; charset=UTF-8",
                    markdown(p, variant, brands, categories, template).getBytes(StandardCharsets.UTF_8));
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out, StandardCharsets.UTF_8)) {
            Set<String> names = new HashSet<>();
            for (ProductDO p : products) {
                String name = fileName(p, variant);
                if (!names.add(name)) {
                    name = name.replace(".md", "-" + p.getId() + ".md");
                }
                zip.putNextEntry(new ZipEntry(name));
                zip.write(markdown(p, variant, brands, categories, template).getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("任务包打包失败", e);
        }
        return new Package((facts ? "facts-" : variant.platforms().size() > 1 ? "marketing-"
                : "marketing-" + variant.suffix().replace('.', '-') + "-") + products.size() + ".zip", "application/zip",
                out.toByteArray());
    }

    String markdown(ProductDO p, Variant variant, Map<Long, String> brands, Map<Long, String> categories,
                    String template) {
        boolean facts = variant == null;
        String brand = brands.getOrDefault(p.getBrandId(), "");
        StringBuilder sb = new StringBuilder();
        sb.append("---\n")
                .append("schema: 2\n")
                .append("type: ").append(facts ? ContentConstants.TYPE_FACTS : ContentConstants.TYPE_MARKETING).append('\n')
                .append("brand: ").append(brand).append('\n')
                .append("model: ").append(p.getMpnRaw()).append('\n')
                .append(facts ? "lang: zh          # 资料包只能是中文\n"
                        : "ru".equals(variant.lang()) ? "lang: ru          # 俄文只写独立站\n"
                        : "lang: en          # 营销包用英文；独立站俄文另下载「独立站俄文」\n")
                .append("---\n\n");
        sb.append(facts ? "<!-- 商品信息（生成资料的输入，上传时可以保留或删除）\n"
                : "<!-- 商品资料（生成营销内容的依据，规格以此为准，上传时可以保留或删除）\n");
        sb.append("品牌：").append(brand).append('\n');
        sb.append("型号：").append(p.getMpnRaw());
        if (StringUtils.hasText(p.getMpnDisplay()) && !p.getMpnDisplay().equals(p.getMpnRaw())) {
            sb.append("（展示型号 ").append(p.getMpnDisplay()).append('）');
        }
        sb.append('\n');
        line(sb, "名称", p.getProductName());
        line(sb, "品类", categories.get(p.getCategoryId()));
        if (p.getSeriesId() != null) {
            ProductSeriesDO series = seriesMapper.selectById(p.getSeriesId());
            line(sb, "系列", series == null ? null : series.getSeriesName());
        }
        line(sb, "生命周期", LIFECYCLE.get(p.getLifecycleStatus()));
        line(sb, "简短描述", p.getShortDescription());
        line(sb, "规格摘要", p.getSpecSummary());
        List<ProductSpecificationDO> specs = specificationMapper.selectList(new LambdaQueryWrapper<ProductSpecificationDO>()
                .eq(ProductSpecificationDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductSpecificationDO::getProductId, p.getId())
                .eq(ProductSpecificationDO::getLang, ContentConstants.FACTS_LANG)
                .isNull(ProductSpecificationDO::getDeletedAt)
                .orderByAsc(ProductSpecificationDO::getSortOrder));
        if (!specs.isEmpty()) {
            sb.append("已有规格：\n");
            for (ProductSpecificationDO s : specs) {
                sb.append("- ").append(clean(s.getSpecLabel())).append(": ").append(clean(s.getSpecValue()));
                if (StringUtils.hasText(s.getSpecUnit())) {
                    sb.append(' ').append(clean(s.getSpecUnit()));
                }
                if (StringUtils.hasText(s.getConditionText())) {
                    sb.append("；条件：").append(clean(s.getConditionText()));
                }
                if (StringUtils.hasText(s.getEvidence())) {
                    sb.append("；证据：").append(clean(s.getEvidence()));
                }
                if (StringUtils.hasText(s.getSourceNote())) {
                    sb.append("；来源：").append(clean(s.getSourceNote()));
                }
                sb.append(s.getVerified() != null && s.getVerified() == 1 ? "（已核实）" : "").append('\n');
            }
        }
        if (facts) {
            appendInquiryDescriptions(sb, p.getId());
            appendFactNotes(sb, p.getId());
        } else {
            appendFacts(sb, p.getId());
            if (variant.keywords()) {
                appendKeywords(sb, p.getId());
            }
        }
        sb.append("-->\n\n");
        sb.append("# ").append(brand).append(' ').append(p.getMpnRaw()).append("\n\n");
        sb.append(template);
        return sb.toString();
    }

    private void appendInquiryDescriptions(StringBuilder sb, Long productId) {
        List<ProductCandidateDO> candidates = candidateMapper.selectList(new LambdaQueryWrapper<ProductCandidateDO>()
                .select(ProductCandidateDO::getDescription, ProductCandidateDO::getDescriptionEn)
                .eq(ProductCandidateDO::getProductId, productId)
                .isNull(ProductCandidateDO::getDeletedAt));
        Set<String> descriptions = new LinkedHashSet<>();
        for (ProductCandidateDO c : candidates) {
            if (StringUtils.hasText(c.getDescription())) {
                descriptions.add(c.getDescription().trim());
            }
            if (StringUtils.hasText(c.getDescriptionEn())) {
                descriptions.add(c.getDescriptionEn().trim());
            }
        }
        if (!descriptions.isEmpty()) {
            sb.append("询盘描述：\n");
            descriptions.forEach(d -> sb.append("- ").append(clean(d)).append('\n'));
        }
    }

    /** 营销包的生成依据：中文应用场景、兼容型号、已确认的技术问答、技术资料 */
    private void appendFacts(StringBuilder sb, Long productId) {
        List<ProductApplicationDO> apps = applicationMapper.selectList(new LambdaQueryWrapper<ProductApplicationDO>()
                .eq(ProductApplicationDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductApplicationDO::getProductId, productId)
                .eq(ProductApplicationDO::getLang, ContentConstants.FACTS_LANG)
                .isNull(ProductApplicationDO::getDeletedAt)
                .orderByAsc(ProductApplicationDO::getSortOrder));
        if (!apps.isEmpty()) {
            sb.append("应用场景：\n");
            apps.forEach(a -> sb.append("- ").append(clean(a.getTitle()))
                    .append(StringUtils.hasText(a.getDescription()) ? "：" + clean(a.getDescription()) : "").append('\n'));
        }
        List<ProductRelationshipDO> rels = relationshipMapper.selectList(new LambdaQueryWrapper<ProductRelationshipDO>()
                .eq(ProductRelationshipDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductRelationshipDO::getProductId, productId)
                .eq(ProductRelationshipDO::getRelationshipType, ContentConstants.RELATIONSHIP_COMPATIBLE)
                .isNull(ProductRelationshipDO::getDeletedAt)
                .orderByAsc(ProductRelationshipDO::getSortOrder));
        if (!rels.isEmpty()) {
            Map<Long, String> notes = new HashMap<>();
            relationshipNoteMapper.selectList(new LambdaQueryWrapper<ProductRelationshipNoteDO>()
                            .in(ProductRelationshipNoteDO::getRelationshipId, rels.stream().map(ProductRelationshipDO::getId).toList())
                            .eq(ProductRelationshipNoteDO::getLang, ContentConstants.FACTS_LANG))
                    .forEach(n -> notes.put(n.getRelationshipId(), n.getNote()));
            sb.append("兼容型号：\n");
            rels.forEach(r -> sb.append("- ").append(clean(r.getRelatedMpn()))
                    .append(StringUtils.hasText(notes.get(r.getId())) ? "：" + clean(notes.get(r.getId())) : "").append('\n'));
        }
        List<ProductFaqDO> faqs = faqMapper.selectList(new LambdaQueryWrapper<ProductFaqDO>()
                .eq(ProductFaqDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductFaqDO::getProductId, productId)
                .ne(ProductFaqDO::getSource, FAQ_SOURCE_PENDING)
                .isNull(ProductFaqDO::getDeletedAt)
                .orderByAsc(ProductFaqDO::getSortOrder));
        if (!faqs.isEmpty()) {
            sb.append("技术问答：\n");
            faqs.forEach(f -> sb.append("- 问：").append(clean(f.getQuestion())).append(" 答：").append(clean(f.getAnswer()))
                    .append('\n'));
        }
        List<ProductDocumentDO> docs = documentMapper.selectList(new LambdaQueryWrapper<ProductDocumentDO>()
                .eq(ProductDocumentDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductDocumentDO::getProductId, productId)
                .isNull(ProductDocumentDO::getDeletedAt)
                .orderByAsc(ProductDocumentDO::getSortOrder));
        if (!docs.isEmpty()) {
            sb.append("技术资料：\n");
            docs.forEach(d -> sb.append("- ").append(clean(d.getTitle())).append("：").append(clean(d.getFileUrl())).append('\n'));
        }
    }

    /** 资料包的生成输入：已有的资料待确认项 */
    private void appendFactNotes(StringBuilder sb, Long productId) {
        List<ProductFactNoteDO> notes = factNoteMapper.selectList(new LambdaQueryWrapper<ProductFactNoteDO>()
                .eq(ProductFactNoteDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductFactNoteDO::getProductId, productId)
                .isNull(ProductFactNoteDO::getDeletedAt)
                .orderByAsc(ProductFactNoteDO::getKind)
                .orderByAsc(ProductFactNoteDO::getSortOrder));
        if (!notes.isEmpty()) {
            sb.append("已有待确认项：\n");
            notes.forEach(n -> sb.append("- ").append(n.getKind() == 1 ? "[未知] " : "[冲突] ").append(clean(n.getItem()))
                    .append(StringUtils.hasText(n.getStatusText()) ? "：" + clean(n.getStatusText()) : "")
                    .append(StringUtils.hasText(n.getImpact()) ? "（" + clean(n.getImpact()) + "）" : "").append('\n'));
        }
    }

    private static final String[] TIER_CODES = {"", "P1", "P2", "P3"};

    /** 营销包的生成输入：本公司已有的关键词总库（参考，新文件的关键词库会整份替换它） */
    private void appendKeywords(StringBuilder sb, Long productId) {
        List<ProductKeywordDO> keywords = keywordMapper.selectList(new LambdaQueryWrapper<ProductKeywordDO>()
                .eq(ProductKeywordDO::getTenantId, TenantContext.getTenantId())
                .eq(ProductKeywordDO::getProductId, productId)
                .isNull(ProductKeywordDO::getDeletedAt)
                .orderByAsc(ProductKeywordDO::getTier)
                .orderByAsc(ProductKeywordDO::getSortOrder));
        if (!keywords.isEmpty()) {
            sb.append("已有关键词库（本公司）：\n");
            keywords.forEach(k -> sb.append("- ").append(TIER_CODES[k.getTier()]).append(' ').append(clean(k.getKeyword()))
                    .append(StringUtils.hasText(k.getCategory()) ? "（" + clean(k.getCategory()) + "）" : "").append('\n'));
        }
    }

    /** 写进 HTML 注释的文本不能提前结束注释 */
    private static String clean(String value) {
        return value == null ? "" : value.replace("-->", "—>");
    }

    private static void line(StringBuilder sb, String label, String value) {
        if (StringUtils.hasText(value)) {
            sb.append(label).append('：').append(value.replace("-->", "—>")).append('\n');
        }
    }

    /** 资料包「型号.facts.md」；营销包「型号.marketing.md」（全部平台）、「型号.平台编码.md」、「型号.site.ru.md」 */
    private static String fileName(ProductDO p, Variant variant) {
        return p.getMpnRaw().replaceAll("[\\\\/:*?\"<>|\\s]+", "_")
                + (variant == null ? ".facts.md" : "." + variant.suffix() + ".md");
    }

    /** 资料包模版；营销包按范围拼：关键词库（阿里、中制用到时）+ 各平台章节 + 内部附录 */
    private static String template(Variant variant) {
        if (variant == null) {
            return resource("product-content/facts-template.md");
        }
        StringBuilder sb = new StringBuilder();
        if (variant.keywords()) {
            sb.append(resource("product-content/marketing-keywords.md"));
        }
        for (MarketingPlatform platform : variant.platforms()) {
            sb.append(resource("product-content/marketing-" + platform.code() + ".md"));
        }
        sb.append(resource("product-content/marketing-appendix.md"));
        return sb.toString();
    }

    private static String resource(String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("任务包模版读取失败", e);
        }
    }
}
