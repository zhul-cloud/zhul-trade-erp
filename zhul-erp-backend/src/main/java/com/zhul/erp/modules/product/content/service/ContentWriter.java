package com.zhul.erp.modules.product.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.modules.product.content.entity.ProductContentImportDO;
import com.zhul.erp.modules.product.content.entity.ProductContentTaskDO;
import com.zhul.erp.modules.product.content.entity.ProductFactTaskDO;
import com.zhul.erp.modules.product.content.entity.ProductLocaleDO;
import com.zhul.erp.modules.product.content.entity.ProductPlatformContentDO;
import com.zhul.erp.modules.product.content.entity.ProductFactNoteDO;
import com.zhul.erp.modules.product.content.entity.ProductKeywordDO;
import com.zhul.erp.modules.product.content.entity.ProductKeywordUseDO;
import com.zhul.erp.modules.product.content.entity.ProductRelationshipNoteDO;
import com.zhul.erp.modules.product.content.entity.ProductSeoDO;
import com.zhul.erp.modules.product.content.entity.ProductSeoFaqDO;
import com.zhul.erp.modules.product.content.repository.ProductContentImportMapper;
import com.zhul.erp.modules.product.content.repository.ProductLocaleMapper;
import com.zhul.erp.modules.product.content.repository.ProductPlatformContentMapper;
import com.zhul.erp.modules.product.content.repository.ProductFactNoteMapper;
import com.zhul.erp.modules.product.content.repository.ProductKeywordMapper;
import com.zhul.erp.modules.product.content.repository.ProductKeywordUseMapper;
import com.zhul.erp.modules.product.content.repository.ProductRelationshipNoteMapper;
import com.zhul.erp.modules.product.content.repository.ProductSeoFaqMapper;
import com.zhul.erp.modules.product.content.repository.ProductSeoMapper;
import com.zhul.erp.modules.product.content.support.ContentDraft;
import com.zhul.erp.modules.product.content.support.ContentSection;
import com.zhul.erp.modules.product.content.support.ContentV2Parser;
import com.zhul.erp.modules.product.content.support.FactFields;
import com.zhul.erp.modules.product.content.support.MarketingPlatform;
import com.zhul.erp.modules.product.entity.ProductListingPriceDO;
import com.zhul.erp.modules.product.entity.ProductApplicationDO;
import com.zhul.erp.modules.product.entity.ProductCustomsDO;
import com.zhul.erp.modules.product.entity.ProductLogisticsDO;
import com.zhul.erp.modules.product.entity.ProductReferencePriceDO;
import com.zhul.erp.modules.product.entity.ProductSeriesDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductDocumentDO;
import com.zhul.erp.modules.product.entity.ProductFaqDO;
import com.zhul.erp.modules.product.entity.ProductRelationshipDO;
import com.zhul.erp.modules.product.entity.ProductSpecificationDO;
import com.zhul.erp.modules.product.repository.ProductListingPriceMapper;
import com.zhul.erp.modules.product.repository.ProductApplicationMapper;
import com.zhul.erp.modules.product.repository.ProductCustomsMapper;
import com.zhul.erp.modules.product.repository.ProductLogisticsMapper;
import com.zhul.erp.modules.product.repository.ProductReferencePriceMapper;
import com.zhul.erp.modules.product.repository.ProductSeriesMapper;
import com.zhul.erp.modules.product.repository.ProductDocumentMapper;
import com.zhul.erp.modules.product.repository.ProductFaqMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.repository.ProductRelationshipMapper;
import com.zhul.erp.modules.product.repository.ProductSpecificationMapper;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.product.support.ProductFinder;
import com.zhul.erp.modules.system.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 确认写入一个文件（一个商品的一个包），调用方负责事务（spec product/product-content「确认写入」）。
 * 资料包（中文，写共享商品库）：规格摘要替换；规格、应用场景替换未核实的，已核实的保留且同名不重复；
 * 技术资料按地址合并只增不删；兼容型号按归一化型号合并，只更新中文说明或新增「兼容」关系；
 * 技术问答写入共享 FAQ（待审核），按问题去重；写完记录资料导入。
 * 营销包（写本公司该平台）：独立站的 SEO/GEO 与 FAQ 按语言写 product_seo；阿里国际站、中国制造网、社媒按块写
 * product_platform_content，跳过的块保留原值；写完标记本公司该平台（独立站按语言）已写入。
 * 关键词（商品内容规范 v2）：本公司每个商品一份总库 product_keyword，各平台的选用 product_keyword_use 引用总库；
 * v2 文件的关键词库项整份替换总库（同词保留原 ID，去掉的词连同选用一起去掉），平台项的选用整份替换，不在总库的补为主要档；
 * 旧格式平台包的关键词库合并进总库（同词档位取最高）并整份替换该平台的选用。
 * v2 资料包另写规格证据、产品识别（只补空）、来源链接（并入技术资料），并整份替换资料待确认项。
 */
@Component
@RequiredArgsConstructor
public class ContentWriter {

    static final String SOURCE = "内容包";
    /** 共享 FAQ 来源：AI 辅助生成待审核 */
    static final int FAQ_SOURCE_PENDING = 3;

    private final ProductFinder productFinder;
    private final ProductMapper productMapper;
    private final ProductSpecificationMapper specificationMapper;
    private final ProductApplicationMapper applicationMapper;
    private final ProductDocumentMapper documentMapper;
    private final ProductRelationshipMapper relationshipMapper;
    private final ProductRelationshipNoteMapper relationshipNoteMapper;
    private final ProductLocaleMapper localeMapper;
    private final ProductSeoMapper seoMapper;
    private final ProductSeoFaqMapper faqMapper;
    private final ProductFaqMapper productFaqMapper;
    private final ProductContentImportMapper importMapper;
    private final ContentTaskService taskService;
    private final FactTaskService factTaskService;
    private final ProductSeriesMapper seriesMapper;
    private final ProductLogisticsMapper logisticsMapper;
    private final ProductCustomsMapper customsMapper;
    private final ProductPlatformContentMapper platformContentMapper;
    private final ProductKeywordMapper keywordMapper;
    private final ProductKeywordUseMapper keywordUseMapper;
    private final ProductFactNoteMapper factNoteMapper;
    private final ObjectMapper objectMapper;
    private final ProductListingPriceMapper listingPriceMapper;
    private final ProductReferencePriceMapper referencePriceMapper;
    private final ExchangeRateService exchangeRateService;

    /**
     * 返回写入摘要。draft 给出包类型、语言、规范版本与拆出的部分；blocks 为要写入的块（跳过与有错的块已去掉）。
     * 资料包 platform 为空；营销包为该平台，v2 营销文件的关键词库项 platform 为空。
     */
    public String write(Long productId, MarketingPlatform platform, ContentDraft draft,
                        Map<ContentSection, ContentDraft.Block> blocks, String fileName, String rawText) {
        boolean facts = ContentConstants.TYPE_FACTS.equals(draft.getType());
        boolean keywordPart = ContentV2Parser.PART_KEYWORDS.equals(draft.getPart());
        String lang = draft.getLang();
        ProductDO product = productFinder.lockActive(productId);
        ProductFactTaskDO fact = facts ? factTaskService.lockOrCreate(productId) : null;
        ProductContentTaskDO task = taskService.lockOrCreate(productId);

        ProductContentImportDO imp = new ProductContentImportDO();
        imp.setTenantId(TenantContext.getTenantId());
        imp.setTaskId(task.getId());
        imp.setProductId(productId);
        imp.setPackageType(facts ? ContentConstants.TYPE_FACTS : ContentConstants.TYPE_MARKETING);
        imp.setPlatform(platform == null ? "" : platform.code());
        imp.setLang(lang);
        imp.setFileName(fileName.length() > 255 ? fileName.substring(0, 255) : fileName);
        imp.setRawText(rawText);
        imp.setConfirmedBy(SecurityUtils.getCurrentUsername());
        importMapper.insert(imp);

        List<String> summary = new ArrayList<>();
        for (Map.Entry<ContentSection, ContentDraft.Block> e : blocks.entrySet()) {
            ContentDraft.Block b = e.getValue();
            switch (e.getKey()) {
                case SUMMARY -> summary.add(writeSummary(product, lang, b.getText()));
                case SPECS -> summary.add(writeSpecs(productId, lang, b.getSpecs()));
                case APPS -> summary.add(writeApps(productId, lang, b.getApps()));
                case DOCS -> summary.add(writeDocs(productId, b.getDocs()));
                case COMPAT -> summary.add(writeCompat(product, lang, b.getCompat()));
                case FAQ -> {
                    if (platform == MarketingPlatform.SITE) {
                        summary.add(writeFaqs(productId, lang, b.getFaqs(), imp.getId()));
                    }
                }
                case IDENTITY -> summary.add(writeIdentity(product, b.getFields()));
                case SOURCES -> summary.add(writeSourceLinks(productId, b.getSources()));
                case KEYWORD_LIBRARY -> summary.add(writeLibrary(productId, lang, b.getKeywords(), imp.getId()));
                case TECH_FAQ -> summary.add(writeTechFaqs(productId, b.getFaqs()));
                case SERIES -> summary.add(writeSeries(product, b.getText()));
                case LOGISTICS -> summary.add(writeLogistics(productId, b.getFields()));
                case CUSTOMS -> summary.add(writeCustoms(productId, b.getFields()));
                case LISTING_PRICES -> {
                    summary.add(writeListings(productId, b.getListings(), draft.getUpdated(), imp.getId()));
                    // 没有单独写「商品平台参考价」时，用挂牌价生成商品卡片的平台参考价
                    ContentDraft.Block card = blocks.get(ContentSection.CARD_PRICE);
                    ContentDraft.CardPriceRow derived = card != null && card.getCardPrice() != null ? null
                            : cardPriceFromListings(b.getListings(), usdRate());
                    if (derived != null) {
                        summary.add(writeCardPrice(productId, derived, draft.getUpdated()));
                    }
                }
                case CARD_PRICE -> summary.add(writeCardPrice(productId, b.getCardPrice(), draft.getUpdated()));
                default -> {
                    // SEO/GEO 文本块统一在下面写
                }
            }
        }
        if (facts && Integer.valueOf(2).equals(draft.getSchema())) {
            summary.add(writeFactNotes(productId, draft, blocks, imp.getId()));
        }
        if (platform == MarketingPlatform.SITE) {
            String seo = writeSeo(productId, lang, blocks, imp.getId());
            if (seo != null) {
                summary.add(seo);
            }
        } else if (platform != null) {
            summary.add(writePlatform(productId, platform, lang, blocks, imp.getId()));
        }

        String text = String.join(" · ", summary);
        importMapper.update(null, new LambdaUpdateWrapper<ProductContentImportDO>()
                .eq(ProductContentImportDO::getId, imp.getId())
                .set(ProductContentImportDO::getSummary, text.length() > 1000 ? text.substring(0, 1000) : text));
        if (facts) {
            factTaskService.markImported(fact);
        } else if (!keywordPart) {
            taskService.markWritten(task, platform, lang);
        }
        return text;
    }

    private String writeSummary(ProductDO product, String lang, String text) {
        ProductLocaleDO row = localeMapper.selectOne(new LambdaQueryWrapper<ProductLocaleDO>()
                .eq(ProductLocaleDO::getProductId, product.getId())
                .eq(ProductLocaleDO::getLang, lang));
        if (row == null) {
            row = new ProductLocaleDO();
            row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            row.setProductId(product.getId());
            row.setLang(lang);
            row.setSpecSummary(text);
            localeMapper.insert(row);
        } else {
            row.setSpecSummary(text);
            localeMapper.updateById(row);
        }
        if (ContentConstants.FACTS_LANG.equals(lang)) {
            // 商品资料只存中文：中文摘要同步写回 product.spec_summary（商品列表展示）
            productMapper.update(null, new LambdaUpdateWrapper<ProductDO>()
                    .eq(ProductDO::getId, product.getId())
                    .set(ProductDO::getSpecSummary, text)
                    .set(ProductDO::getUpdateTime, LocalDateTime.now())
                    .set(ProductDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
        }
        return "规格摘要";
    }

    private String writeSpecs(Long productId, String lang, List<ContentDraft.SpecRow> rows) {
        LocalDateTime now = LocalDateTime.now();
        specificationMapper.update(null, new LambdaUpdateWrapper<ProductSpecificationDO>()
                .eq(ProductSpecificationDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductSpecificationDO::getProductId, productId)
                .eq(ProductSpecificationDO::getLang, lang)
                .eq(ProductSpecificationDO::getVerified, 0)
                .isNull(ProductSpecificationDO::getDeletedAt)
                .set(ProductSpecificationDO::getDeletedAt, now)
                .set(ProductSpecificationDO::getUpdateTime, now)
                .set(ProductSpecificationDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
        List<ProductSpecificationDO> kept = specificationMapper.selectList(new LambdaQueryWrapper<ProductSpecificationDO>()
                .eq(ProductSpecificationDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductSpecificationDO::getProductId, productId)
                .eq(ProductSpecificationDO::getLang, lang)
                .isNull(ProductSpecificationDO::getDeletedAt));
        Set<String> labels = new HashSet<>();
        Set<String> keys = new HashSet<>();
        for (ProductSpecificationDO k : kept) {
            labels.add(key(k.getSpecLabel()));
            keys.add(k.getSpecKey());
        }
        int written = 0;
        int skipped = 0;
        int sort = kept.size();
        for (ContentDraft.SpecRow r : rows) {
            if (!labels.add(key(r.label()))) {
                skipped++;
                continue;
            }
            ProductSpecificationDO row = new ProductSpecificationDO();
            row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            row.setProductId(productId);
            row.setLang(lang);
            row.setSpecKey(specKey(r.label(), keys));
            row.setSpecLabel(r.label());
            row.setSpecValue(r.value());
            row.setSpecUnit(r.unit());
            row.setConditionText(cut(r.condition(), 256));
            row.setEvidence(cut(r.evidence(), 64));
            row.setSourceNote(cut(r.sourceNote(), 256));
            row.setSource(SOURCE);
            row.setVerified(0);
            row.setSortOrder(sort++);
            specificationMapper.insert(row);
            written++;
        }
        return "规格 " + written + (skipped > 0 ? "（已核实或重复跳过 " + skipped + "）" : "");
    }

    private String writeApps(Long productId, String lang, List<ContentDraft.AppRow> rows) {
        LocalDateTime now = LocalDateTime.now();
        applicationMapper.update(null, new LambdaUpdateWrapper<ProductApplicationDO>()
                .eq(ProductApplicationDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductApplicationDO::getProductId, productId)
                .eq(ProductApplicationDO::getLang, lang)
                .eq(ProductApplicationDO::getVerified, 0)
                .isNull(ProductApplicationDO::getDeletedAt)
                .set(ProductApplicationDO::getDeletedAt, now)
                .set(ProductApplicationDO::getUpdateTime, now)
                .set(ProductApplicationDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
        List<ProductApplicationDO> kept = applicationMapper.selectList(new LambdaQueryWrapper<ProductApplicationDO>()
                .eq(ProductApplicationDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductApplicationDO::getProductId, productId)
                .eq(ProductApplicationDO::getLang, lang)
                .isNull(ProductApplicationDO::getDeletedAt));
        Set<String> titles = new HashSet<>();
        kept.forEach(k -> titles.add(key(k.getTitle())));
        int written = 0;
        int skipped = 0;
        int sort = kept.size();
        for (ContentDraft.AppRow r : rows) {
            if (!titles.add(key(r.title()))) {
                skipped++;
                continue;
            }
            ProductApplicationDO row = new ProductApplicationDO();
            row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            row.setProductId(productId);
            row.setLang(lang);
            row.setTitle(r.title());
            row.setDescription(r.description());
            row.setIcon(r.icon());
            row.setVerified(0);
            row.setSortOrder(sort++);
            applicationMapper.insert(row);
            written++;
        }
        return "应用场景 " + written + (skipped > 0 ? "（已核实或重复跳过 " + skipped + "）" : "");
    }

    private String writeDocs(Long productId, List<ContentDraft.DocRow> rows) {
        List<ProductDocumentDO> existing = documentMapper.selectList(new LambdaQueryWrapper<ProductDocumentDO>()
                .select(ProductDocumentDO::getFileUrl)
                .eq(ProductDocumentDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductDocumentDO::getProductId, productId)
                .isNull(ProductDocumentDO::getDeletedAt));
        Set<String> urls = new HashSet<>();
        existing.forEach(d -> urls.add(d.getFileUrl().trim()));
        int written = 0;
        int sort = existing.size();
        for (ContentDraft.DocRow r : rows) {
            if (!urls.add(r.url().trim())) {
                continue;
            }
            ProductDocumentDO row = new ProductDocumentDO();
            row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            row.setProductId(productId);
            row.setDocumentType(documentType(r.title()));
            row.setTitle(r.title());
            row.setFileUrl(r.url().trim());
            row.setSource(SOURCE);
            row.setVerified(0);
            row.setSortOrder(sort++);
            documentMapper.insert(row);
            written++;
        }
        int skipped = rows.size() - written;
        return "技术资料 " + written + (skipped > 0 ? "（地址已存在 " + skipped + "）" : "");
    }

    private String writeCompat(ProductDO product, String lang, List<ContentDraft.CompatRow> rows) {
        Map<String, ProductRelationshipDO> existing = new HashMap<>();
        for (ProductRelationshipDO r : relationshipMapper.selectList(new LambdaQueryWrapper<ProductRelationshipDO>()
                .eq(ProductRelationshipDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductRelationshipDO::getProductId, product.getId())
                .eq(ProductRelationshipDO::getRelationshipType, ContentConstants.RELATIONSHIP_COMPATIBLE)
                .isNull(ProductRelationshipDO::getDeletedAt))) {
            existing.putIfAbsent(compatKey(r.getRelatedMpn()), r);
        }
        int added = 0;
        int updated = 0;
        int sort = existing.size();
        for (ContentDraft.CompatRow r : rows) {
            String k = compatKey(r.model());
            ProductRelationshipDO rel = existing.get(k);
            if (rel == null) {
                rel = new ProductRelationshipDO();
                rel.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
                rel.setProductId(product.getId());
                rel.setRelatedMpn(r.model());
                rel.setRelatedProductId(relatedProduct(product, r.model()));
                rel.setRelationshipType(ContentConstants.RELATIONSHIP_COMPATIBLE);
                rel.setConfidence(ContentConstants.CONFIDENCE_UNKNOWN);
                rel.setNote(ContentConstants.FACTS_LANG.equals(lang) ? r.note() : "");
                rel.setSortOrder(sort++);
                relationshipMapper.insert(rel);
                existing.put(k, rel);
                added++;
            } else {
                if (ContentConstants.FACTS_LANG.equals(lang)) {
                    relationshipMapper.update(null, new LambdaUpdateWrapper<ProductRelationshipDO>()
                            .eq(ProductRelationshipDO::getId, rel.getId())
                            .set(ProductRelationshipDO::getNote, r.note())
                            .set(ProductRelationshipDO::getUpdateTime, LocalDateTime.now())
                            .set(ProductRelationshipDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
                }
                updated++;
            }
            upsertNote(rel.getId(), lang, r.note());
        }
        return "兼容型号 新增 " + added + (updated > 0 ? "、更新说明 " + updated : "");
    }

    private void upsertNote(Long relationshipId, String lang, String note) {
        ProductRelationshipNoteDO row = relationshipNoteMapper.selectOne(new LambdaQueryWrapper<ProductRelationshipNoteDO>()
                .eq(ProductRelationshipNoteDO::getRelationshipId, relationshipId)
                .eq(ProductRelationshipNoteDO::getLang, lang));
        if (row == null) {
            row = new ProductRelationshipNoteDO();
            row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            row.setRelationshipId(relationshipId);
            row.setLang(lang);
            row.setNote(note);
            relationshipNoteMapper.insert(row);
        } else {
            row.setNote(note);
            relationshipNoteMapper.updateById(row);
        }
    }

    /** 关联型号在库内（同品牌、归一化型号相同、不是本商品）时返回其 ID */
    private Long relatedProduct(ProductDO product, String model) {
        String mpn = MpnNormalizer.normalize(model);
        if (mpn.isEmpty()) {
            return null;
        }
        ProductDO hit = productMapper.selectOne(new LambdaQueryWrapper<ProductDO>()
                .select(ProductDO::getId)
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductDO::getBrandId, product.getBrandId())
                .eq(ProductDO::getMpnNormalized, mpn)
                .ne(ProductDO::getId, product.getId())
                .isNull(ProductDO::getDeletedAt)
                .last("LIMIT 1"));
        return hit == null ? null : hit.getId();
    }

    private String writeFaqs(Long productId, String lang, List<ContentDraft.FaqRow> rows, Long importId) {
        Integer tenantId = TenantContext.getTenantId();
        LocalDateTime now = LocalDateTime.now();
        faqMapper.update(null, new LambdaUpdateWrapper<ProductSeoFaqDO>()
                .eq(ProductSeoFaqDO::getTenantId, tenantId)
                .eq(ProductSeoFaqDO::getProductId, productId)
                .eq(ProductSeoFaqDO::getLang, lang)
                .isNull(ProductSeoFaqDO::getDeletedAt)
                .set(ProductSeoFaqDO::getDeletedAt, now)
                .set(ProductSeoFaqDO::getUpdateTime, now)
                .set(ProductSeoFaqDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
        int sort = 0;
        for (ContentDraft.FaqRow r : rows) {
            ProductSeoFaqDO row = new ProductSeoFaqDO();
            row.setTenantId(tenantId);
            row.setProductId(productId);
            row.setLang(lang);
            row.setQuestion(r.question());
            row.setAnswer(r.answer());
            row.setSortOrder(sort++);
            row.setImportId(importId);
            faqMapper.insert(row);
        }
        return "FAQ " + rows.size();
    }

    /** 技术问答写入共享 FAQ：来源为待审核，问题已存在（含待审核、已确认）的不重复写入 */
    private String writeTechFaqs(Long productId, List<ContentDraft.FaqRow> rows) {
        List<ProductFaqDO> existing = productFaqMapper.selectList(new LambdaQueryWrapper<ProductFaqDO>()
                .select(ProductFaqDO::getQuestion)
                .eq(ProductFaqDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductFaqDO::getProductId, productId)
                .isNull(ProductFaqDO::getDeletedAt));
        Set<String> questions = new HashSet<>();
        existing.forEach(f -> questions.add(key(f.getQuestion())));
        int written = 0;
        int sort = existing.size();
        for (ContentDraft.FaqRow r : rows) {
            if (!questions.add(key(r.question()))) {
                continue;
            }
            ProductFaqDO row = new ProductFaqDO();
            row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            row.setProductId(productId);
            row.setQuestion(r.question().trim());
            row.setAnswer(r.answer());
            row.setSource(FAQ_SOURCE_PENDING);
            row.setSortOrder(sort++);
            productFaqMapper.insert(row);
            written++;
        }
        int skipped = rows.size() - written;
        return "技术问答 " + written + "（待平台审核）" + (skipped > 0 ? "，已有 " + skipped + " 条不重复写入" : "");
    }

    /** SEO 标题、描述、长描述、首屏定义块：有哪块写哪块，没上传或跳过的保留原值 */
    private String writeSeo(Long productId, String lang, Map<ContentSection, ContentDraft.Block> blocks, Long importId) {
        ContentDraft.Block title = blocks.get(ContentSection.SEO_TITLE);
        ContentDraft.Block desc = blocks.get(ContentSection.SEO_DESC);
        ContentDraft.Block longDesc = blocks.get(ContentSection.LONG_DESC);
        ContentDraft.Block geo = blocks.get(ContentSection.GEO);
        ContentDraft.Block fields = blocks.get(ContentSection.SEO_FIELDS);
        ContentDraft.Block alts = blocks.get(ContentSection.ALT_TEXTS);
        if (title == null && desc == null && longDesc == null && geo == null && fields == null && alts == null) {
            return null;
        }
        Integer tenantId = TenantContext.getTenantId();
        ProductSeoDO row = seoMapper.selectOne(new LambdaQueryWrapper<ProductSeoDO>()
                .eq(ProductSeoDO::getTenantId, tenantId)
                .eq(ProductSeoDO::getProductId, productId)
                .eq(ProductSeoDO::getLang, lang));
        boolean create = row == null;
        if (create) {
            row = new ProductSeoDO();
            row.setTenantId(tenantId);
            row.setProductId(productId);
            row.setLang(lang);
        }
        List<String> parts = new ArrayList<>();
        if (title != null) {
            row.setSeoTitle(title.getText());
            parts.add("SEO 标题");
        }
        if (desc != null) {
            row.setMetaDescription(desc.getText());
            parts.add("SEO 描述");
        }
        if (longDesc != null) {
            row.setLongDescription(longDesc.getText());
            parts.add("长描述");
        }
        if (geo != null) {
            row.setGeoAnswer(geo.getText());
            parts.add("首屏定义块");
        }
        if (fields != null) {
            Map<String, String> f = fields.getFields();
            if (f.containsKey(ContentV2Parser.SEO_TITLE)) {
                row.setSeoTitle(f.get(ContentV2Parser.SEO_TITLE));
            }
            if (f.containsKey(ContentV2Parser.META_DESCRIPTION)) {
                row.setMetaDescription(f.get(ContentV2Parser.META_DESCRIPTION));
            }
            if (f.containsKey(ContentV2Parser.H1)) {
                row.setH1(f.get(ContentV2Parser.H1));
            }
            if (f.containsKey(ContentV2Parser.URL_SLUG)) {
                row.setUrlSlug(f.get(ContentV2Parser.URL_SLUG));
            }
            parts.add("SEO 字段 " + f.size());
        }
        if (alts != null) {
            row.setAltTexts(json(alts.getPairs().stream()
                    .map(a -> Map.of("image", a.name(), "alt", a.value())).toList()));
            parts.add("图片说明文字 " + alts.getPairs().size());
        }
        row.setImportId(importId);
        if (create) {
            seoMapper.insert(row);
        } else {
            seoMapper.updateById(row);
        }
        return String.join("、", parts);
    }

    /** 系列：商品还没有系列、且系列库里有同名系列（同品牌，见 seriesKey）时写入 */
    private String writeSeries(ProductDO product, String name) {
        if (product.getSeriesId() != null) {
            return "系列已有，保留";
        }
        ProductSeriesDO series = findSeries(product.getBrandId(), name);
        if (series == null) {
            return "系列库里没有「" + name + "」，未写入";
        }
        productMapper.update(null, new LambdaUpdateWrapper<ProductDO>()
                .eq(ProductDO::getId, product.getId())
                .set(ProductDO::getSeriesId, series.getId())
                .set(ProductDO::getUpdateTime, LocalDateTime.now())
                .set(ProductDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
        return "系列 " + series.getSeriesName();
    }

    ProductSeriesDO findSeries(Long brandId, String name) {
        return seriesMapper.selectList(new LambdaQueryWrapper<ProductSeriesDO>()
                        .eq(ProductSeriesDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                        .eq(ProductSeriesDO::getBrandId, brandId)
                        .isNull(ProductSeriesDO::getDeletedAt))
                .stream().filter(x -> seriesKey(x.getSeriesName()).equals(seriesKey(name))).findFirst().orElse(null);
    }

    /** 系列名比较：不分大小写、去空格，忽略末尾的「Series」「系列」（DOP-100 与 DOP-100 Series 视为同一系列） */
    static String seriesKey(String name) {
        return key(name).replaceAll("\\s+", "").replaceAll("(series|系列)$", "");
    }

    /** 物流：只补为空的字段，已有值保留 */
    private String writeLogistics(Long productId, Map<String, String> fields) {
        ProductLogisticsDO row = logisticsMapper.selectOne(new LambdaQueryWrapper<ProductLogisticsDO>()
                .eq(ProductLogisticsDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductLogisticsDO::getProductId, productId));
        boolean create = row == null;
        boolean revive = row != null && row.getDeletedAt() != null;
        if (create || revive) {
            ProductLogisticsDO fresh = new ProductLogisticsDO();
            fresh.setId(create ? null : row.getId());
            fresh.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            fresh.setProductId(productId);
            fresh.setIsDangerous(0);
            row = fresh;
        }
        final ProductLogisticsDO target = row;
        List<String> written = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            String v = e.getValue();
            boolean empty = switch (e.getKey()) {
                case FactFields.NET_WEIGHT -> fill(target.getNetWeightKg(), () -> target.setNetWeightKg(new BigDecimal(v)));
                case FactFields.GROSS_WEIGHT -> fill(target.getGrossWeightKg(), () -> target.setGrossWeightKg(new BigDecimal(v)));
                case FactFields.LENGTH -> fill(target.getLengthMm(), () -> target.setLengthMm(new BigDecimal(v)));
                case FactFields.WIDTH -> fill(target.getWidthMm(), () -> target.setWidthMm(new BigDecimal(v)));
                case FactFields.HEIGHT -> fill(target.getHeightMm(), () -> target.setHeightMm(new BigDecimal(v)));
                case FactFields.PACKAGE_TYPE -> fill(target.getPackageType(), () -> target.setPackageType(v));
                case FactFields.PACKAGE_QUANTITY -> fill(target.getPackageQuantity(), () -> target.setPackageQuantity(Integer.valueOf(v)));
                case FactFields.SHIPPING_NOTE -> fill(target.getShippingNote(), () -> target.setShippingNote(v));
                default -> false;
            };
            (empty ? written : kept).add(FactFields.LABELS.get(e.getKey()));
        }
        if (create) {
            logisticsMapper.insert(row);
        } else {
            if (revive) {
                logisticsMapper.update(null, new LambdaUpdateWrapper<ProductLogisticsDO>()
                        .eq(ProductLogisticsDO::getId, row.getId())
                        .set(ProductLogisticsDO::getDeletedAt, null));
            }
            logisticsMapper.updateById(row);
        }
        return "物流 " + written.size() + (kept.isEmpty() ? "" : "（已有值保留 " + kept.size() + "）");
    }

    /** 海关：只补为空的字段，已有值保留 */
    private String writeCustoms(Long productId, Map<String, String> fields) {
        ProductCustomsDO row = customsMapper.selectOne(new LambdaQueryWrapper<ProductCustomsDO>()
                .eq(ProductCustomsDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductCustomsDO::getProductId, productId));
        boolean create = row == null;
        boolean revive = row != null && row.getDeletedAt() != null;
        if (create || revive) {
            ProductCustomsDO fresh = new ProductCustomsDO();
            fresh.setId(create ? null : row.getId());
            fresh.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            fresh.setProductId(productId);
            row = fresh;
        }
        final ProductCustomsDO target = row;
        List<String> written = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            String v = e.getValue();
            boolean empty = switch (e.getKey()) {
                case FactFields.HS_CODE -> fill(target.getHsCode(), () -> target.setHsCode(v));
                case FactFields.ORIGIN -> fill(target.getOriginCountry(), () -> target.setOriginCountry(v));
                case FactFields.NAME_CN -> fill(target.getCustomsNameCn(), () -> target.setCustomsNameCn(v));
                case FactFields.NAME_EN -> fill(target.getCustomsNameEn(), () -> target.setCustomsNameEn(v));
                default -> false;
            };
            (empty ? written : kept).add(FactFields.LABELS.get(e.getKey()));
        }
        if (create) {
            customsMapper.insert(row);
        } else {
            if (revive) {
                customsMapper.update(null, new LambdaUpdateWrapper<ProductCustomsDO>()
                        .eq(ProductCustomsDO::getId, row.getId())
                        .set(ProductCustomsDO::getDeletedAt, null));
            }
            customsMapper.updateById(row);
        }
        return "海关 " + written.size() + (kept.isEmpty() ? "" : "（已有值保留 " + kept.size() + "）");
    }

    /** 当前值为空时执行写入并返回 true，否则返回 false（保留已有值） */
    private static boolean fill(Object current, Runnable setter) {
        if (current == null || current instanceof String str && str.isBlank()) {
            setter.run();
            return true;
        }
        return false;
    }

    /** 阿里国际站、中国制造网、社媒：有哪块写哪块，跳过的保留原值；关键词库整份替换 */
    private String writePlatform(Long productId, MarketingPlatform platform, String lang,
                                 Map<ContentSection, ContentDraft.Block> blocks, Long importId) {
        Integer tenantId = TenantContext.getTenantId();
        ProductPlatformContentDO row = platformContentMapper.selectOne(new LambdaQueryWrapper<ProductPlatformContentDO>()
                .eq(ProductPlatformContentDO::getTenantId, tenantId)
                .eq(ProductPlatformContentDO::getProductId, productId)
                .eq(ProductPlatformContentDO::getPlatform, platform.code())
                .eq(ProductPlatformContentDO::getLang, lang));
        boolean create = row == null;
        if (create) {
            row = new ProductPlatformContentDO();
            row.setTenantId(tenantId);
            row.setProductId(productId);
            row.setPlatform(platform.code());
            row.setLang(lang);
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<ContentSection, ContentDraft.Block> e : blocks.entrySet()) {
            ContentDraft.Block b = e.getValue();
            switch (e.getKey()) {
                case TITLE -> {
                    row.setTitle(b.getText());
                    if (!b.getTitles().isEmpty()) {
                        row.setTitleAlternates(json(b.getTitles().subList(1, b.getTitles().size())));
                    }
                    parts.add(b.getTitles().size() > 1 ? "标题（备选 " + (b.getTitles().size() - 1) + "）" : "标题");
                }
                case SELLING_POINTS -> {
                    row.setSellingPoints(String.join("\n", b.getPoints()));
                    parts.add("卖点 " + b.getPoints().size());
                }
                case DETAIL -> {
                    row.setDetail(b.getText());
                    parts.add("详情");
                }
                case ATTRIBUTES -> {
                    row.setAttributes(json(b.getPairs()));
                    parts.add("属性 " + b.getPairs().size());
                }
                case FAQ -> {
                    row.setFaqs(json(b.getFaqs()));
                    parts.add("FAQ " + b.getFaqs().size());
                }
                case CTA -> {
                    row.setCta(cut(b.getText(), 500));
                    parts.add("引导语");
                }
                case LINKEDIN -> {
                    row.setLinkedinPost(b.getText());
                    parts.add("LinkedIn");
                }
                case FACEBOOK -> {
                    row.setFacebookPost(b.getText());
                    parts.add("Facebook");
                }
                case YOUTUBE -> {
                    row.setYoutubeDescription(b.getText());
                    parts.add("YouTube");
                }
                case POSTS -> {
                    row.setPosts(json(b.getPosts()));
                    parts.add("帖子 " + b.getPosts().size());
                }
                case VIDEOS -> {
                    row.setVideos(json(b.getPosts().stream()
                            .map(v -> Map.of("label", v.label(), "markdown", v.text())).toList()));
                    parts.add("视频方案 " + b.getPosts().size());
                }
                case KEYWORD_PICKS -> parts.add(writePicks(productId, platform, lang, b.getPicks(), importId));
                case KEYWORDS -> parts.add(mergeLegacyKeywords(productId, platform, lang, b.getKeywords(), importId));
                default -> {
                    // 其他块不属于平台内容
                }
            }
        }
        row.setImportId(importId);
        if (create) {
            platformContentMapper.insert(row);
        } else {
            platformContentMapper.updateById(row);
        }
        return platform.label() + "：" + String.join("、", parts);
    }

    private List<ProductKeywordDO> library(Long productId, String lang) {
        return keywordMapper.selectList(new LambdaQueryWrapper<ProductKeywordDO>()
                .eq(ProductKeywordDO::getTenantId, TenantContext.getTenantId())
                .eq(ProductKeywordDO::getProductId, productId)
                .eq(ProductKeywordDO::getLang, lang)
                .isNull(ProductKeywordDO::getDeletedAt)
                .orderByAsc(ProductKeywordDO::getSortOrder));
    }

    private ProductKeywordDO newKeyword(Long productId, String lang, ContentDraft.KeywordRow r, int sort, Long importId) {
        ProductKeywordDO k = new ProductKeywordDO();
        k.setTenantId(TenantContext.getTenantId());
        k.setProductId(productId);
        k.setLang(lang);
        k.setKeyword(r.keyword().strip());
        k.setKeywordKey(key(r.keyword()));
        k.setTier(r.tier());
        k.setCategory(cut(r.category(), 64));
        k.setIntent(cut(r.intent(), 128));
        k.setBasis(cut(r.basis(), 128));
        k.setSortOrder(sort);
        k.setImportId(importId);
        keywordMapper.insert(k);
        return k;
    }

    /** v2 关键词库：整份替换总库。同词保留原 ID（选用不受影响），不在新库里的词连同各平台对它的选用一起去掉 */
    private String writeLibrary(Long productId, String lang, List<ContentDraft.KeywordRow> rows, Long importId) {
        Map<String, ProductKeywordDO> existing = new LinkedHashMap<>();
        library(productId, lang).forEach(k -> existing.putIfAbsent(k.getKeywordKey(), k));
        Set<String> keep = new HashSet<>();
        int sort = 0;
        int added = 0;
        for (ContentDraft.KeywordRow r : rows) {
            String k = key(r.keyword());
            if (!keep.add(k)) {
                continue;
            }
            sort++;
            ProductKeywordDO row = existing.get(k);
            if (row == null) {
                newKeyword(productId, lang, r, sort, importId);
                added++;
                continue;
            }
            row.setKeyword(r.keyword().strip());
            row.setTier(r.tier());
            row.setCategory(cut(r.category(), 64));
            row.setIntent(cut(r.intent(), 128));
            row.setBasis(cut(r.basis(), 128));
            row.setSortOrder(sort);
            row.setImportId(importId);
            keywordMapper.updateById(row);
        }
        List<Long> removed = existing.values().stream().filter(k -> !keep.contains(k.getKeywordKey()))
                .map(ProductKeywordDO::getId).toList();
        if (!removed.isEmpty()) {
            LocalDateTime now = LocalDateTime.now();
            String user = SecurityUtils.getCurrentUsername();
            keywordMapper.update(null, new LambdaUpdateWrapper<ProductKeywordDO>()
                    .in(ProductKeywordDO::getId, removed)
                    .set(ProductKeywordDO::getDeletedAt, now)
                    .set(ProductKeywordDO::getUpdateTime, now)
                    .set(ProductKeywordDO::getUpdateBy, user));
            keywordUseMapper.update(null, new LambdaUpdateWrapper<ProductKeywordUseDO>()
                    .eq(ProductKeywordUseDO::getTenantId, TenantContext.getTenantId())
                    .in(ProductKeywordUseDO::getKeywordId, removed)
                    .isNull(ProductKeywordUseDO::getDeletedAt)
                    .set(ProductKeywordUseDO::getDeletedAt, now)
                    .set(ProductKeywordUseDO::getUpdateTime, now)
                    .set(ProductKeywordUseDO::getUpdateBy, user));
        }
        return "关键词库 " + keep.size() + "（新增 " + added + (removed.isEmpty() ? "" : "、去掉 " + removed.size()) + "）";
    }

    /** v2 平台选用：整份替换该平台的选用；不在总库里的词按主要档补进总库 */
    private String writePicks(Long productId, MarketingPlatform platform, String lang, List<String> picks, Long importId) {
        List<ProductKeywordDO> lib = library(productId, lang);
        Map<String, ProductKeywordDO> byKey = new HashMap<>();
        lib.forEach(k -> byKey.putIfAbsent(k.getKeywordKey(), k));
        int sort = lib.stream().mapToInt(ProductKeywordDO::getSortOrder).max().orElse(0);
        int added = 0;
        Set<Long> ids = new LinkedHashSet<>();
        for (String p : picks) {
            ProductKeywordDO k = byKey.get(key(p));
            if (k == null) {
                k = newKeyword(productId, lang, new ContentDraft.KeywordRow(p, ContentConstants.TIER_MAIN), ++sort, importId);
                byKey.put(k.getKeywordKey(), k);
                added++;
            }
            ids.add(k.getId());
        }
        replaceUses(productId, platform, ids);
        return "选用关键词 " + ids.size() + (added > 0 ? "（补进总库 " + added + "）" : "");
    }

    /** 旧格式平台包的关键词库：合并进总库（同词档位取最高，新词追加），并整份替换该平台的选用 */
    private String mergeLegacyKeywords(Long productId, MarketingPlatform platform, String lang,
                                       List<ContentDraft.KeywordRow> rows, Long importId) {
        List<ProductKeywordDO> lib = library(productId, lang);
        Map<String, ProductKeywordDO> byKey = new HashMap<>();
        lib.forEach(k -> byKey.putIfAbsent(k.getKeywordKey(), k));
        int sort = lib.stream().mapToInt(ProductKeywordDO::getSortOrder).max().orElse(0);
        int added = 0;
        Set<Long> ids = new LinkedHashSet<>();
        for (ContentDraft.KeywordRow r : rows) {
            ProductKeywordDO k = byKey.get(key(r.keyword()));
            if (k == null) {
                k = newKeyword(productId, lang, r, ++sort, importId);
                byKey.put(k.getKeywordKey(), k);
                added++;
            } else if (r.tier() < k.getTier()) {
                k.setTier(r.tier());
                keywordMapper.updateById(k);
            }
            ids.add(k.getId());
        }
        replaceUses(productId, platform, ids);
        return "关键词 " + ids.size() + "（合并进总库，新增 " + added + "）";
    }

    private void replaceUses(Long productId, MarketingPlatform platform, Set<Long> keywordIds) {
        Integer tenantId = TenantContext.getTenantId();
        LocalDateTime now = LocalDateTime.now();
        keywordUseMapper.update(null, new LambdaUpdateWrapper<ProductKeywordUseDO>()
                .eq(ProductKeywordUseDO::getTenantId, tenantId)
                .eq(ProductKeywordUseDO::getProductId, productId)
                .eq(ProductKeywordUseDO::getPlatform, platform.code())
                .isNull(ProductKeywordUseDO::getDeletedAt)
                .set(ProductKeywordUseDO::getDeletedAt, now)
                .set(ProductKeywordUseDO::getUpdateTime, now)
                .set(ProductKeywordUseDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
        int order = 0;
        for (Long id : keywordIds) {
            ProductKeywordUseDO u = new ProductKeywordUseDO();
            u.setTenantId(tenantId);
            u.setProductId(productId);
            u.setKeywordId(id);
            u.setPlatform(platform.code());
            u.setSortOrder(++order);
            keywordUseMapper.insert(u);
        }
    }

    /** 产品识别：商品名称为空时补中文名称（没有中文时用英文）；系列按系列库匹配，商品没有系列时写入 */
    private String writeIdentity(ProductDO product, Map<String, String> fields) {
        List<String> parts = new ArrayList<>();
        String name = fields.getOrDefault(ContentV2Parser.NAME_ZH, fields.get(ContentV2Parser.NAME_EN));
        if (name != null) {
            if (product.getProductName() == null || product.getProductName().isBlank()) {
                productMapper.update(null, new LambdaUpdateWrapper<ProductDO>()
                        .eq(ProductDO::getId, product.getId())
                        .set(ProductDO::getProductName, cut(name, 255))
                        .set(ProductDO::getUpdateTime, LocalDateTime.now())
                        .set(ProductDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
                product.setProductName(name);
                parts.add("名称");
            } else {
                parts.add("名称已有，保留");
            }
        }
        String series = fields.get(ContentV2Parser.SERIES);
        if (series != null) {
            parts.add(writeSeries(product, series));
        }
        return "产品识别：" + (parts.isEmpty() ? "无可写字段" : String.join("、", parts));
    }

    /** 来源索引：http(s) 链接按地址并入技术资料（标题为发布者及范围）；本地路径不导入 */
    private String writeSourceLinks(Long productId, List<ContentDraft.SourceRow> sources) {
        List<ContentDraft.DocRow> docs = sources.stream()
                .filter(s -> isWebLink(s.link()))
                .map(s -> new ContentDraft.DocRow(cut(s.name(), 128), s.link().trim())).toList();
        return writeDocs(productId, docs).replace("技术资料", "来源链接")
                + (docs.size() < sources.size() ? "（本地路径 " + (sources.size() - docs.size()) + " 条不导入）" : "");
    }

    static boolean isWebLink(String link) {
        String l = link == null ? "" : link.trim().toLowerCase(Locale.ROOT);
        return (l.startsWith("http://") || l.startsWith("https://")) && l.length() <= 512;
    }

    /**
     * 资料待确认项：两类分别整份替换。文件里有该章节的，按新内容替换；文件里没有的，清空；
     * 有但被跳过的，保留原有。
     */
    /** 平台参考价：整份替换该商品的挂牌参考（共享商品库）；取价日期取文件的 updated，没有时为今天 */
    private String writeListings(Long productId, List<ContentDraft.ListingRow> rows, String updated, Long importId) {
        LocalDateTime now = LocalDateTime.now();
        listingPriceMapper.update(null, new LambdaUpdateWrapper<ProductListingPriceDO>()
                .eq(ProductListingPriceDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductListingPriceDO::getProductId, productId)
                .isNull(ProductListingPriceDO::getDeletedAt)
                .set(ProductListingPriceDO::getDeletedAt, now));
        LocalDate date;
        try {
            date = updated == null || updated.isBlank() ? LocalDate.now() : LocalDate.parse(updated.strip());
        } catch (DateTimeParseException e) {
            date = LocalDate.now();
        }
        int sort = 0;
        for (ContentDraft.ListingRow r : rows) {
            ProductListingPriceDO row = new ProductListingPriceDO();
            row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            row.setProductId(productId);
            row.setCategory(r.category());
            row.setPlatform(r.platform());
            row.setItemCondition(r.condition());
            row.setPriceUsd(r.priceUsd());
            row.setSourcePrice(r.sourcePrice());
            row.setLinkTitle(r.linkTitle());
            row.setLinkUrl(r.linkUrl());
            row.setPriceDate(date);
            row.setSort(sort++);
            row.setImportId(importId);
            listingPriceMapper.insert(row);
        }
        return "平台参考价 " + rows.size() + " 条";
    }

    /**
     * 从平台参考价（挂牌价）生成商品卡片的平台参考价：不看类目参考行；成色为全新的行里取最低价，没有全新时取所有行的最低价；
     * 币种 USD，汇率为系统当前美元汇率（没有时为空，显示未计算），价格来源写平台、成色与价格类别，
     * 取价日期留空（写入时取 frontmatter 的 updated）。没有行时返回 null
     */
    /** 同类别可比商品的挂牌价（价格类别写「类目参考、不代表目标型号价格」）不当作本商品的参考价 */
    static final String NOT_TARGET = "不代表目标型号";

    public static ContentDraft.CardPriceRow cardPriceFromListings(List<ContentDraft.ListingRow> rows, BigDecimal usdRate) {
        List<ContentDraft.ListingRow> priced = rows == null ? List.of()
                : rows.stream().filter(r -> r.priceUsd() != null && r.priceUsd().signum() > 0)
                .filter(r -> r.category() == null || !r.category().contains(NOT_TARGET)).toList();
        if (priced.isEmpty()) {
            return null;
        }
        List<ContentDraft.ListingRow> fresh = priced.stream().filter(r -> isNewCondition(r.condition())).toList();
        ContentDraft.ListingRow pick = (fresh.isEmpty() ? priced : fresh).stream()
                .min(Comparator.comparing(ContentDraft.ListingRow::priceUsd)).orElseThrow();
        String source = String.join(" ", Stream.of(pick.platform(), pick.condition(), pick.category())
                .filter(v -> v != null && !v.isBlank()).map(String::strip).toList());
        if (source.length() > 128) {
            source = source.substring(0, 128);
        }
        return new ContentDraft.CardPriceRow("USD", pick.priceUsd().setScale(2, RoundingMode.HALF_UP),
                usdRate == null ? null : usdRate.setScale(6, RoundingMode.HALF_UP), source.isEmpty() ? null : source, null);
    }

    /** 成色是否为全新：New、全新、新品（不分大小写，New 开头的如 New other 也算） */
    static boolean isNewCondition(String condition) {
        if (condition == null) {
            return false;
        }
        String c = condition.strip().toLowerCase(Locale.ROOT);
        return c.startsWith("new") || c.contains("全新") || c.startsWith("新品");
    }

    /** 系统当前美元汇率（当前租户）；没有设置时为 null */
    BigDecimal usdRate() {
        ExchangeRateService.Snapshot r = exchangeRateService.find("USD");
        return r == null ? null : r.rate();
    }

    /**
     * 商品平台参考价合并已有值后要写入的内容。未知值不覆盖：汇率未知时沿用原汇率（币种不变时），否则为空（未计算）；
     * 来源未知沿用原值；取价日期 表内 → frontmatter updated → 原值。
     * 精度：人民币参考价 = 原币参考价 × 6 位汇率，最后 HALF_UP 保留 2 位；人民币按汇率 1。
     */
    public record CardPrice(String currency, BigDecimal priceOriginal, BigDecimal exchangeRate, BigDecimal priceCny,
                            String priceSource, LocalDate priceDate, boolean rateKept, boolean sourceKept, String dateFrom) {
    }

    public static CardPrice resolveCardPrice(ContentDraft.CardPriceRow r, ProductReferencePriceDO existing, String updated) {
        boolean sameCurrency = existing != null && r.currency().equals(existing.getCurrencyCode());
        BigDecimal rate = r.exchangeRate();
        boolean rateKept = false;
        if (rate == null && sameCurrency && existing.getExchangeRate() != null) {
            rate = existing.getExchangeRate();
            rateKept = true;
        }
        BigDecimal cny = "CNY".equals(r.currency()) ? r.priceOriginal()
                : rate == null ? null : r.priceOriginal().multiply(rate).setScale(2, RoundingMode.HALF_UP);
        String source = r.priceSource();
        boolean sourceKept = source == null && existing != null && existing.getPriceSource() != null;
        if (source == null && existing != null) {
            source = existing.getPriceSource();
        }
        LocalDate date = r.priceDate();
        String dateFrom = "表内";
        if (date == null) {
            date = parseDate(updated);
            dateFrom = "updated";
        }
        if (date == null) {
            date = existing == null ? null : existing.getPriceDate();
            dateFrom = date == null ? "" : "现有";
        }
        return new CardPrice(r.currency(), r.priceOriginal(), rate, cny, source, date, rateKept, sourceKept, dateFrom);
    }

    private static LocalDate parseDate(String v) {
        if (v == null || v.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(v.strip());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** 商品平台参考价：写商品卡片的平台参考价（一个商品一条，含已软删除的行时复用） */
    private String writeCardPrice(Long productId, ContentDraft.CardPriceRow r, String updated) {
        ProductReferencePriceDO row = referencePriceMapper.selectOne(new LambdaQueryWrapper<ProductReferencePriceDO>()
                .eq(ProductReferencePriceDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductReferencePriceDO::getProductId, productId));
        ProductReferencePriceDO active = row == null || row.getDeletedAt() != null ? null : row;
        CardPrice p = resolveCardPrice(r, active, updated);
        if (p.priceCny() != null && p.priceCny().compareTo(new BigDecimal("10000000000000000")) >= 0) {
            throw new BizException("折算后的人民币参考价过大");
        }
        if (row == null) {
            ProductReferencePriceDO n = new ProductReferencePriceDO();
            n.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            n.setProductId(productId);
            n.setPriceOriginal(p.priceOriginal());
            n.setCurrencyCode(p.currency());
            n.setExchangeRate(p.exchangeRate());
            n.setPriceCny(p.priceCny());
            n.setPriceSource(p.priceSource());
            n.setPriceDate(p.priceDate());
            referencePriceMapper.insert(n);
        } else {
            referencePriceMapper.update(new ProductReferencePriceDO(), new LambdaUpdateWrapper<ProductReferencePriceDO>()
                    .eq(ProductReferencePriceDO::getId, row.getId())
                    .set(ProductReferencePriceDO::getPriceOriginal, p.priceOriginal())
                    .set(ProductReferencePriceDO::getCurrencyCode, p.currency())
                    .set(ProductReferencePriceDO::getExchangeRate, p.exchangeRate())
                    .set(ProductReferencePriceDO::getPriceCny, p.priceCny())
                    .set(ProductReferencePriceDO::getPriceSource, p.priceSource())
                    .set(ProductReferencePriceDO::getPriceDate, p.priceDate())
                    .set(ProductReferencePriceDO::getDeletedAt, null));
        }
        return "商品平台参考价 " + p.currency() + " " + p.priceOriginal().toPlainString();
    }

    private String writeFactNotes(Long productId, ContentDraft draft, Map<ContentSection, ContentDraft.Block> blocks,
                                  Long importId) {
        int total = 0;
        List<String> kept = new ArrayList<>();
        int[] kinds = {1, 2};
        ContentSection[] sections = {ContentSection.UNKNOWNS, ContentSection.CONFLICTS};
        for (int i = 0; i < kinds.length; i++) {
            ContentDraft.Block b = blocks.get(sections[i]);
            if (b == null && draft.getBlocks().containsKey(sections[i])) {
                kept.add(sections[i].label());
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            factNoteMapper.update(null, new LambdaUpdateWrapper<ProductFactNoteDO>()
                    .eq(ProductFactNoteDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                    .eq(ProductFactNoteDO::getProductId, productId)
                    .eq(ProductFactNoteDO::getKind, kinds[i])
                    .isNull(ProductFactNoteDO::getDeletedAt)
                    .set(ProductFactNoteDO::getDeletedAt, now)
                    .set(ProductFactNoteDO::getUpdateTime, now)
                    .set(ProductFactNoteDO::getUpdateBy, SecurityUtils.getCurrentUsername()));
            if (b == null) {
                continue;
            }
            int sort = 0;
            for (ContentDraft.NoteRow r : b.getNotesRows()) {
                ProductFactNoteDO n = new ProductFactNoteDO();
                n.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
                n.setProductId(productId);
                n.setKind(kinds[i]);
                n.setItem(cut(r.item(), 256));
                n.setStatusText(cut(r.status(), 500));
                n.setImpact(cut(r.impact(), 500));
                n.setSortOrder(++sort);
                n.setImportId(importId);
                factNoteMapper.insert(n);
                total++;
            }
        }
        return "待确认项 " + total + (kept.isEmpty() ? "" : "（跳过的保留：" + String.join("、", kept) + "）");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("内容序列化失败", e);
        }
    }

    private static String cut(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) : s;
    }

    static String key(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    /** 兼容型号合并键：归一化型号，归一化为空（如纯中文说明）时用原文 */
    static String compatKey(String model) {
        String n = MpnNormalizer.normalize(model);
        return n.isEmpty() ? key(model) : n;
    }

    /** 规格编码：英文字段名转小写蛇形，非英文（中文、俄文）用 spec_序号；与已有编码不重复 */
    static String specKey(String label, Set<String> used) {
        String base = label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty() || !Character.isLetter(base.charAt(0))) {
            base = "spec";
        }
        if (base.length() > 56) {
            base = base.substring(0, 56);
        }
        String candidate = base;
        int n = 2;
        while (!used.add(candidate) || "spec".equals(candidate)) {
            candidate = base + "_" + n++;
        }
        return candidate;
    }

    static int documentType(String title) {
        String t = key(title);
        if (t.contains("user manual") || t.contains("用户手册")) {
            return 4;
        }
        if (t.contains("install") || t.contains("安装")) {
            return 3;
        }
        if (t.contains("manual") || t.contains("手册")) {
            return 2;
        }
        if (t.contains("cad") || t.contains("3d")) {
            return 5;
        }
        if (t.contains("drawing") || t.contains("dimension") || t.contains("图纸") || t.contains("尺寸")) {
            return 6;
        }
        if (t.contains("brochure") || t.contains("catalog") || t.contains("样本") || t.contains("画册")) {
            return 7;
        }
        if (t.contains("certificate") || t.contains("证书") || t.contains("认证")) {
            return 8;
        }
        return 1;
    }
}
