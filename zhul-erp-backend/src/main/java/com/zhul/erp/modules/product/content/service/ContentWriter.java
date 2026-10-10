package com.zhul.erp.modules.product.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.modules.product.content.entity.ProductContentImportDO;
import com.zhul.erp.modules.product.content.entity.ProductContentTaskDO;
import com.zhul.erp.modules.product.content.entity.ProductLocaleDO;
import com.zhul.erp.modules.product.content.entity.ProductRelationshipNoteDO;
import com.zhul.erp.modules.product.content.entity.ProductSeoDO;
import com.zhul.erp.modules.product.content.entity.ProductSeoFaqDO;
import com.zhul.erp.modules.product.content.repository.ProductContentImportMapper;
import com.zhul.erp.modules.product.content.repository.ProductLocaleMapper;
import com.zhul.erp.modules.product.content.repository.ProductRelationshipNoteMapper;
import com.zhul.erp.modules.product.content.repository.ProductSeoFaqMapper;
import com.zhul.erp.modules.product.content.repository.ProductSeoMapper;
import com.zhul.erp.modules.product.content.support.ContentDraft;
import com.zhul.erp.modules.product.content.support.ContentSection;
import com.zhul.erp.modules.product.entity.ProductApplicationDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductDocumentDO;
import com.zhul.erp.modules.product.entity.ProductRelationshipDO;
import com.zhul.erp.modules.product.entity.ProductSpecificationDO;
import com.zhul.erp.modules.product.repository.ProductApplicationMapper;
import com.zhul.erp.modules.product.repository.ProductDocumentMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.repository.ProductRelationshipMapper;
import com.zhul.erp.modules.product.repository.ProductSpecificationMapper;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import com.zhul.erp.modules.product.support.ProductFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 确认写入一个文件（一个商品的一种语言），调用方负责事务（design.md 决策 4、5）。
 * 共享商品库：规格摘要替换；规格、应用场景替换该语言未核实的，已核实的保留且同名不重复；
 * 技术资料按地址合并只增不删；兼容型号按归一化型号合并，只更新该语言说明或新增「兼容」关系。
 * 本公司：SEO/GEO 与 FAQ 该语言整体替换。
 */
@Component
@RequiredArgsConstructor
public class ContentWriter {

    static final String SOURCE = "内容包";

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
    private final ProductContentImportMapper importMapper;
    private final ContentTaskService taskService;

    /** 返回写入摘要 */
    public String write(Long productId, String lang, Map<ContentSection, ContentDraft.Block> blocks,
                        String fileName, String rawText) {
        ProductDO product = productFinder.lockActive(productId);
        ProductContentTaskDO task = taskService.lockOrCreate(productId);

        ProductContentImportDO imp = new ProductContentImportDO();
        imp.setTenantId(TenantContext.getTenantId());
        imp.setTaskId(task.getId());
        imp.setProductId(productId);
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
                case FAQ -> summary.add(writeFaqs(productId, lang, b.getFaqs(), imp.getId()));
                default -> {
                    // SEO/GEO 文本块统一在下面写
                }
            }
        }
        String seo = writeSeo(productId, lang, blocks, imp.getId());
        if (seo != null) {
            summary.add(seo);
        }

        String text = String.join(" · ", summary);
        importMapper.update(null, new LambdaUpdateWrapper<ProductContentImportDO>()
                .eq(ProductContentImportDO::getId, imp.getId())
                .set(ProductContentImportDO::getSummary, text.length() > 1000 ? text.substring(0, 1000) : text));
        taskService.markWritten(task, lang);
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
        if ("en".equals(lang)) {
            // 英文同步写回 product.spec_summary，现有页面与独立站不受影响
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
                rel.setNote("en".equals(lang) ? r.note() : "");
                rel.setSortOrder(sort++);
                relationshipMapper.insert(rel);
                existing.put(k, rel);
                added++;
            } else {
                if ("en".equals(lang)) {
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

    /** SEO 标题、描述、长描述、首屏定义块：有哪块写哪块，没上传或跳过的保留原值 */
    private String writeSeo(Long productId, String lang, Map<ContentSection, ContentDraft.Block> blocks, Long importId) {
        ContentDraft.Block title = blocks.get(ContentSection.SEO_TITLE);
        ContentDraft.Block desc = blocks.get(ContentSection.SEO_DESC);
        ContentDraft.Block longDesc = blocks.get(ContentSection.LONG_DESC);
        ContentDraft.Block geo = blocks.get(ContentSection.GEO);
        if (title == null && desc == null && longDesc == null && geo == null) {
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
        row.setImportId(importId);
        if (create) {
            seoMapper.insert(row);
        } else {
            seoMapper.updateById(row);
        }
        return String.join("、", parts);
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
