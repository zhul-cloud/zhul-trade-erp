package com.zhul.erp.modules.product.content.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.content.constants.ContentConstants;
import com.zhul.erp.modules.product.content.dto.ContentConfirmVO;
import com.zhul.erp.modules.product.content.dto.ContentFileRequest;
import com.zhul.erp.modules.product.content.dto.ContentPreviewVO;
import com.zhul.erp.modules.product.content.entity.ProductSeoFaqDO;
import com.zhul.erp.modules.product.content.repository.ProductSeoFaqMapper;
import com.zhul.erp.modules.product.content.support.ContentDraft;
import com.zhul.erp.modules.product.content.support.ContentMarkdownParser;
import com.zhul.erp.modules.product.content.support.ContentSection;
import com.zhul.erp.modules.product.entity.ProductApplicationDO;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.entity.ProductDocumentDO;
import com.zhul.erp.modules.product.entity.ProductRelationshipDO;
import com.zhul.erp.modules.product.entity.ProductSpecificationDO;
import com.zhul.erp.modules.product.repository.ProductApplicationMapper;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductDocumentMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.repository.ProductRelationshipMapper;
import com.zhul.erp.modules.product.repository.ProductSpecificationMapper;
import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 内容包上传：解析预览（不落库）与确认写入（逐个文件独立事务） */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContentImportService {

    private final BrandResolver brandResolver;
    private final ProductBrandMapper brandMapper;
    private final ProductMapper productMapper;
    private final ProductSpecificationMapper specificationMapper;
    private final ProductApplicationMapper applicationMapper;
    private final ProductDocumentMapper documentMapper;
    private final ProductRelationshipMapper relationshipMapper;
    private final ProductSeoFaqMapper faqMapper;
    private final ContentWriter writer;
    private final PlatformTransactionManager transactionManager;

    /** 预览结果 + 写入需要的解析块（只在服务端内部使用） */
    private record Analyzed(ContentPreviewVO vo, Map<ContentSection, ContentDraft.Block> writable) {
    }

    public List<ContentPreviewVO> preview(ContentFileRequest req) {
        return analyze(req).stream().map(Analyzed::vo).toList();
    }

    public ContentConfirmVO confirm(ContentFileRequest req) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        ContentConfirmVO result = new ContentConfirmVO();
        List<ContentFileRequest.ContentFile> files = req.getFiles();
        List<Analyzed> analyzed = analyze(req);
        for (int i = 0; i < analyzed.size(); i++) {
            Analyzed a = analyzed.get(i);
            ContentPreviewVO vo = a.vo();
            ContentConfirmVO.Item item = new ContentConfirmVO.Item();
            item.setFileName(vo.getFileName());
            item.setProductId(vo.getProductId());
            item.setProductLabel(vo.getProductLabel());
            item.setLang(vo.getLang());
            if (!vo.isConfirmable()) {
                item.setMessage(firstProblem(vo));
            } else {
                String content = files.get(i).getContent();
                try {
                    item.setMessage(tx.execute(s -> writer.write(vo.getProductId(), vo.getLang(), a.writable(),
                            vo.getFileName(), content)));
                    item.setSuccess(true);
                } catch (BizException e) {
                    item.setMessage(e.getMessage());
                } catch (DataAccessException e) {
                    log.error("内容包写入失败，fileName={}, productId={}", vo.getFileName(), vo.getProductId(), e);
                    item.setMessage("写入失败，请稍后重试");
                }
            }
            if (item.isSuccess()) {
                result.setSucceeded(result.getSucceeded() + 1);
            } else {
                result.setFailed(result.getFailed() + 1);
            }
            result.getItems().add(item);
        }
        return result;
    }

    private List<Analyzed> analyze(ContentFileRequest req) {
        List<Analyzed> result = new ArrayList<>();
        Map<String, String> seen = new HashMap<>();
        for (ContentFileRequest.ContentFile file : req.getFiles()) {
            Analyzed a = analyzeFile(file);
            ContentPreviewVO vo = a.vo();
            if (vo.getProductId() != null && vo.getLang() != null) {
                String key = vo.getProductId() + ":" + vo.getLang();
                String first = seen.putIfAbsent(key, vo.getFileName());
                if (first != null) {
                    vo.getErrors().add("与「" + first + "」是同一商品同一语言，只能保留一个");
                    vo.setConfirmable(false);
                }
            }
            result.add(a);
        }
        return result;
    }

    private Analyzed analyzeFile(ContentFileRequest.ContentFile file) {
        ContentPreviewVO vo = new ContentPreviewVO();
        vo.setFileName(file.getFileName());
        String content = file.getContent() == null ? "" : file.getContent();
        if (content.getBytes(StandardCharsets.UTF_8).length > ContentConstants.MAX_FILE_BYTES) {
            vo.getErrors().add("文件超过 1MB");
            return new Analyzed(vo, Map.of());
        }
        ContentDraft draft = ContentMarkdownParser.parse(content);
        applyEdits(draft, file.getEdits());
        vo.setBrand(draft.getBrand());
        vo.setModel(draft.getModel());
        vo.setLang(draft.getLang());
        vo.getErrors().addAll(draft.getErrors());
        vo.getNotes().addAll(draft.getNotes());

        ProductDO product = draft.getErrors().isEmpty() ? match(draft, vo) : null;
        Set<ContentSection> skip = skipSet(file.getSkip());
        Map<ContentSection, ContentDraft.Block> writable = new EnumMap<>(ContentSection.class);
        boolean blockErrors = false;
        for (ContentDraft.Block b : draft.getBlocks().values()) {
            ContentPreviewVO.BlockVO bv = toBlockVO(b);
            bv.setSkipped(skip.contains(b.getSection()));
            bv.setEdited(file.getEdits() != null && file.getEdits().containsKey(b.getSection().name()));
            if (product != null) {
                bv.getHints().addAll(hints(b, product, draft.getLang()));
            }
            if (!bv.isSkipped()) {
                if (b.getErrors().isEmpty()) {
                    writable.put(b.getSection(), b);
                } else {
                    blockErrors = true;
                }
            }
            vo.getBlocks().add(bv);
        }
        if (vo.getErrors().isEmpty() && !blockErrors && writable.isEmpty()) {
            vo.getErrors().add("没有可写入的内容");
        }
        vo.setConfirmable(product != null && vo.getErrors().isEmpty() && !blockErrors);
        return new Analyzed(vo, writable);
    }

    private static void applyEdits(ContentDraft draft, Map<String, String> edits) {
        if (edits == null) {
            return;
        }
        for (Map.Entry<String, String> e : edits.entrySet()) {
            ContentSection section = section(e.getKey());
            if (section == null) {
                continue;
            }
            ContentDraft.Block block = ContentMarkdownParser.parseBlock(section, e.getValue());
            if (block.count() == 0 && block.getErrors().isEmpty()) {
                draft.getBlocks().remove(section);
                draft.getNotes().add("「" + section.label() + "」改成了空，已跳过");
            } else {
                draft.getBlocks().put(section, block);
            }
        }
    }

    /** 按品牌（名称或别名）+ 归一化型号找商品 */
    private ProductDO match(ContentDraft draft, ContentPreviewVO vo) {
        Long brandId = brandResolver.resolve(draft.getBrand());
        if (brandId == null) {
            vo.getErrors().add("找不到品牌「" + draft.getBrand() + "」");
            return null;
        }
        String mpn = MpnNormalizer.normalize(draft.getModel());
        ProductDO product = mpn.isEmpty() ? null : productMapper.selectOne(new LambdaQueryWrapper<ProductDO>()
                .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductDO::getBrandId, brandId)
                .eq(ProductDO::getMpnNormalized, mpn)
                .isNull(ProductDO::getDeletedAt)
                .last("LIMIT 1"));
        if (product == null) {
            vo.getErrors().add("找不到商品：" + draft.getBrand() + " / " + draft.getModel());
            return null;
        }
        ProductBrandDO brand = brandMapper.selectById(brandId);
        vo.setProductId(product.getId());
        vo.setProductLabel((brand == null ? draft.getBrand() : brand.getBrandName()) + " · "
                + (StringUtils.hasText(product.getMpnDisplay()) ? product.getMpnDisplay() : product.getMpnRaw()));
        return product;
    }

    private static ContentPreviewVO.BlockVO toBlockVO(ContentDraft.Block b) {
        ContentSection s = b.getSection();
        ContentPreviewVO.BlockVO bv = new ContentPreviewVO.BlockVO();
        bv.setSection(s.name());
        bv.setLabel(s.label());
        bv.setTarget(s.shared() ? ContentConstants.TARGET_SHARED : ContentConstants.TARGET_COMPANY);
        bv.setRaw(b.getRaw());
        bv.setText(b.getText());
        bv.setCount(b.count());
        bv.getErrors().addAll(b.getErrors());
        switch (s) {
            case SPECS -> {
                bv.setColumns(List.of("字段", "内容", "单位"));
                b.getSpecs().forEach(r -> bv.getRows().add(List.of(r.label(), r.value(), r.unit())));
            }
            case APPS -> {
                bv.setColumns(List.of("图标", "标题", "描述"));
                b.getApps().forEach(r -> bv.getRows().add(List.of(r.icon(), r.title(), r.description())));
            }
            case COMPAT -> {
                bv.setColumns(List.of("原型号", "兼容说明"));
                b.getCompat().forEach(r -> bv.getRows().add(List.of(r.model(), r.note())));
            }
            case FAQ -> {
                bv.setColumns(List.of("问题", "答案"));
                b.getFaqs().forEach(r -> bv.getRows().add(List.of(r.question(), r.answer())));
            }
            case DOCS -> {
                bv.setColumns(List.of("标题", "地址"));
                b.getDocs().forEach(r -> bv.getRows().add(List.of(r.title(), r.url())));
            }
            default -> {
                // 文本块用 text 展示
            }
        }
        return bv;
    }

    /** 写入方式说明：对照商品现有数据，告诉运营哪些会保留、哪些会替换 */
    private List<String> hints(ContentDraft.Block b, ProductDO product, String lang) {
        List<String> hints = new ArrayList<>();
        Long productId = product.getId();
        switch (b.getSection()) {
            case SPECS -> {
                Set<String> verified = new HashSet<>();
                specificationMapper.selectList(new LambdaQueryWrapper<ProductSpecificationDO>()
                                .select(ProductSpecificationDO::getSpecLabel)
                                .eq(ProductSpecificationDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                                .eq(ProductSpecificationDO::getProductId, productId)
                                .eq(ProductSpecificationDO::getLang, lang)
                                .eq(ProductSpecificationDO::getVerified, 1)
                                .isNull(ProductSpecificationDO::getDeletedAt))
                        .forEach(s -> verified.add(ContentWriter.key(s.getSpecLabel())));
                hints.add("替换该语言未核实的规格");
                b.getSpecs().stream().filter(r -> verified.contains(ContentWriter.key(r.label())))
                        .forEach(r -> hints.add("「" + r.label() + "」已核实，保留原值"));
            }
            case APPS -> {
                Set<String> verified = new HashSet<>();
                applicationMapper.selectList(new LambdaQueryWrapper<ProductApplicationDO>()
                                .select(ProductApplicationDO::getTitle)
                                .eq(ProductApplicationDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                                .eq(ProductApplicationDO::getProductId, productId)
                                .eq(ProductApplicationDO::getLang, lang)
                                .eq(ProductApplicationDO::getVerified, 1)
                                .isNull(ProductApplicationDO::getDeletedAt))
                        .forEach(s -> verified.add(ContentWriter.key(s.getTitle())));
                hints.add("替换该语言未核实的应用场景");
                b.getApps().stream().filter(r -> verified.contains(ContentWriter.key(r.title())))
                        .forEach(r -> hints.add("「" + r.title() + "」已核实，保留原值"));
            }
            case DOCS -> {
                Set<String> urls = new HashSet<>();
                documentMapper.selectList(new LambdaQueryWrapper<ProductDocumentDO>()
                                .select(ProductDocumentDO::getFileUrl)
                                .eq(ProductDocumentDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                                .eq(ProductDocumentDO::getProductId, productId)
                                .isNull(ProductDocumentDO::getDeletedAt))
                        .forEach(d -> urls.add(d.getFileUrl().trim()));
                long dup = b.getDocs().stream().filter(d -> urls.contains(d.url().trim())).count();
                hints.add("按地址合并，不删除已有资料" + (dup > 0 ? "；" + dup + " 条地址已存在，不重复写入" : ""));
            }
            case COMPAT -> {
                Set<String> keys = new HashSet<>();
                relationshipMapper.selectList(new LambdaQueryWrapper<ProductRelationshipDO>()
                                .select(ProductRelationshipDO::getRelatedMpn)
                                .eq(ProductRelationshipDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                                .eq(ProductRelationshipDO::getProductId, productId)
                                .eq(ProductRelationshipDO::getRelationshipType, ContentConstants.RELATIONSHIP_COMPATIBLE)
                                .isNull(ProductRelationshipDO::getDeletedAt))
                        .forEach(r -> keys.add(ContentWriter.compatKey(r.getRelatedMpn())));
                long dup = b.getCompat().stream().filter(c -> keys.contains(ContentWriter.compatKey(c.model()))).count();
                hints.add("写为「兼容」型号关系，不删除已有关系" + (dup > 0 ? "；" + dup + " 条已有，只更新说明" : ""));
            }
            case FAQ -> {
                Long existing = faqMapper.selectCount(new LambdaQueryWrapper<ProductSeoFaqDO>()
                        .eq(ProductSeoFaqDO::getTenantId, TenantContext.getTenantId())
                        .eq(ProductSeoFaqDO::getProductId, productId)
                        .eq(ProductSeoFaqDO::getLang, lang)
                        .isNull(ProductSeoFaqDO::getDeletedAt));
                hints.add(existing > 0 ? "替换本公司该语言现有 " + existing + " 条 FAQ" : "写入本公司 FAQ");
            }
            default -> hints.add("替换");
        }
        return hints;
    }

    private static Set<ContentSection> skipSet(List<String> names) {
        Set<ContentSection> set = new HashSet<>();
        if (names != null) {
            for (String n : names) {
                ContentSection s = section(n);
                if (s != null) {
                    set.add(s);
                }
            }
        }
        return set;
    }

    private static ContentSection section(String name) {
        for (ContentSection s : ContentSection.values()) {
            if (s.name().equals(name)) {
                return s;
            }
        }
        return null;
    }

    private static String firstProblem(ContentPreviewVO vo) {
        if (!vo.getErrors().isEmpty()) {
            return vo.getErrors().get(0);
        }
        for (ContentPreviewVO.BlockVO b : vo.getBlocks()) {
            if (!b.isSkipped() && !b.getErrors().isEmpty()) {
                return b.getLabel() + "：" + b.getErrors().get(0) + "（修改或跳过后再确认）";
            }
        }
        return "不能确认";
    }
}
