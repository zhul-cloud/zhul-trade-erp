package com.zhul.erp.modules.product.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import com.zhul.erp.modules.product.candidate.constants.CandidateConstants;
import com.zhul.erp.modules.product.candidate.dto.CandidateImportPreviewVO;
import com.zhul.erp.modules.product.candidate.dto.CandidateImportRequest;
import com.zhul.erp.modules.product.candidate.dto.CandidateImportResultVO;
import com.zhul.erp.modules.product.candidate.entity.ProductCandidateDO;
import com.zhul.erp.modules.product.candidate.repository.ProductCandidateMapper;
import com.zhul.erp.modules.product.candidate.support.ModelRules;
import com.zhul.erp.modules.product.candidate.support.WikiProductParser;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.support.CategoryMatcher;
import com.zhul.erp.modules.product.support.MpnNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从商品文件导入候选（spec product/product-candidate「从商品文件导入候选」）：预览不落库，
 * 确认时重新解析与查重，只写新候选与已在候选池两类，逐个文件独立事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CandidateImportService {

    static final String NEW = "NEW";
    static final String IN_POOL = "IN_POOL";
    static final String IN_PRODUCT = "IN_PRODUCT";
    static final String REJECTED = "REJECTED";
    static final String DUPLICATE = "DUPLICATE";
    static final String INVALID = "INVALID";
    private static final Map<String, String> RESULT_NAMES = Map.of(NEW, "新候选", IN_POOL, "已在候选池",
            IN_PRODUCT, "已在商品库", REJECTED, "曾被驳回", DUPLICATE, "重复", INVALID, "不能导入");
    private static final int MAX_FILE_BYTES = 1024 * 1024;

    private final PriceKeys priceKeys;
    private final ProductMapper productMapper;
    private final ProductBrandMapper brandMapper;
    private final ProductCandidateMapper candidateMapper;
    private final CategoryMatcher categoryMatcher;
    private final ProductArchiver archiver;
    private final PlatformTransactionManager transactionManager;

    /** 预览结果 + 写入需要的内容（只在服务端内部使用） */
    private record Analyzed(CandidateImportPreviewVO vo, ProductArchiver.ImportItem item) {
    }

    public List<CandidateImportPreviewVO> preview(CandidateImportRequest req) {
        return analyze(req).stream().map(Analyzed::vo).toList();
    }

    public CandidateImportResultVO confirm(CandidateImportRequest req) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        CandidateImportResultVO result = new CandidateImportResultVO();
        for (Analyzed a : analyze(req)) {
            CandidateImportPreviewVO vo = a.vo();
            CandidateImportResultVO.Item item = new CandidateImportResultVO.Item();
            item.setFileName(vo.getFileName());
            item.setModel(vo.getModel());
            if (a.item() == null) {
                item.setOutcome("SKIPPED");
                item.setMessage(vo.getResultName() + (vo.getNote() == null ? "" : "：" + vo.getNote()));
                result.setSkipped(result.getSkipped() + 1);
            } else {
                try {
                    boolean created = Boolean.TRUE.equals(tx.execute(s -> archiver.importSource(a.item())));
                    item.setOutcome(created ? "CREATED" : "APPENDED");
                    item.setMessage(created ? "新建候选" : "已在候选池，追加一次导入来源");
                    if (created) {
                        result.setCreated(result.getCreated() + 1);
                    } else {
                        result.setAppended(result.getAppended() + 1);
                    }
                } catch (BizException e) {
                    item.setOutcome("FAILED");
                    item.setMessage(e.getMessage());
                    result.setFailed(result.getFailed() + 1);
                } catch (DataAccessException e) {
                    log.error("候选导入写入失败，fileName={}", vo.getFileName(), e);
                    item.setOutcome("FAILED");
                    item.setMessage("写入失败，请稍后重试");
                    result.setFailed(result.getFailed() + 1);
                }
            }
            result.getItems().add(item);
        }
        return result;
    }

    private List<Analyzed> analyze(CandidateImportRequest req) {
        List<ProductCategoryDO> categories = categoryMatcher.enabledCategories();
        Map<Long, String> categoryNames = new HashMap<>();
        categories.forEach(c -> categoryNames.put(c.getId(),
                StringUtils.hasText(c.getCategoryNameZh()) ? c.getCategoryNameZh() : c.getCategoryName()));
        Map<String, String> seen = new HashMap<>();
        List<Analyzed> result = new ArrayList<>(req.getFiles().size());
        for (CandidateImportRequest.ImportFile f : req.getFiles()) {
            result.add(analyzeFile(f, categories, categoryNames, seen));
        }
        return result;
    }

    private Analyzed analyzeFile(CandidateImportRequest.ImportFile f, List<ProductCategoryDO> categories,
                                 Map<Long, String> categoryNames, Map<String, String> seen) {
        CandidateImportPreviewVO vo = new CandidateImportPreviewVO();
        vo.setFileName(f.getFileName());
        String content = f.getContent() == null ? "" : f.getContent();
        if (content.getBytes(StandardCharsets.UTF_8).length > MAX_FILE_BYTES) {
            return invalid(vo, "文件超过 1MB");
        }
        WikiProductParser.Draft d = WikiProductParser.parse(content);
        vo.setBrand(d.brand());
        vo.setModel(d.model());
        vo.setProductName(d.productName());
        vo.setDescription(d.description());
        vo.setDescriptionEn(d.descriptionEn());
        if (!d.errors().isEmpty()) {
            vo.getErrors().addAll(d.errors());
            return invalid(vo, d.errors().get(0));
        }
        String key = MpnNormalizer.normalize(d.model());
        if (key.isEmpty() || !ModelRules.looksLikeModel(d.model())) {
            return invalid(vo, "「" + d.model() + "」不像型号");
        }
        CategoryMatcher.Result match = CategoryMatcher.match(d.categoryText(), categories);
        Long categoryId = match.ambiguous() ? null : match.categoryId();
        vo.setCategoryId(categoryId);
        vo.setCategoryName(categoryId == null ? null : categoryNames.get(categoryId));
        PriceKeys.BrandRef brand = priceKeys.brand(d.brand());
        vo.setBrandId(brand.brandId());
        if (brand.brandId() != null) {
            ProductBrandDO b = brandMapper.selectById(brand.brandId());
            vo.setBrandName(b == null ? d.brand() : b.getBrandName());
        }

        String first = seen.putIfAbsent(brand.brandKey() + "|" + key, f.getFileName());
        if (first != null) {
            return skip(vo, DUPLICATE, "与「" + first + "」是同一型号");
        }
        if (brand.brandId() != null) {
            ProductDO product = productMapper.selectOne(new LambdaQueryWrapper<ProductDO>()
                    .select(ProductDO::getId)
                    .eq(ProductDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                    .eq(ProductDO::getBrandId, brand.brandId())
                    .eq(ProductDO::getMpnNormalized, key)
                    .isNull(ProductDO::getDeletedAt)
                    .last("LIMIT 1"));
            if (product != null) {
                vo.setProductId(product.getId());
                return skip(vo, IN_PRODUCT, null);
            }
        }
        ProductCandidateDO c = candidateMapper.selectOne(new LambdaQueryWrapper<ProductCandidateDO>()
                .eq(ProductCandidateDO::getBrandKey, brand.brandKey())
                .eq(ProductCandidateDO::getMpnNormalized, key)
                .isNull(ProductCandidateDO::getDeletedAt));
        if (c != null) {
            vo.setCandidateId(c.getId());
            if (c.getStatus() == CandidateConstants.STATUS_REJECTED) {
                return skip(vo, REJECTED, "可在商品候选页重新打开");
            }
            if (c.getStatus() != CandidateConstants.STATUS_PENDING) {
                vo.setProductId(c.getProductId());
                return skip(vo, IN_PRODUCT, null);
            }
            vo.setResult(IN_POOL);
            vo.setNote("已有 " + c.getSourceCount() + " 次来源，计数加一，只补空字段");
        } else {
            vo.setResult(NEW);
        }
        vo.setResultName(RESULT_NAMES.get(vo.getResult()));
        return new Analyzed(vo, new ProductArchiver.ImportItem(brand.brandId(), brand.brandKey(), d.brand(), d.model(),
                key, categoryId, d.categoryText(), d.productName(), d.description(), d.descriptionEn(), f.getFileName()));
    }

    private static Analyzed invalid(CandidateImportPreviewVO vo, String reason) {
        return skip(vo, INVALID, reason);
    }

    private static Analyzed skip(CandidateImportPreviewVO vo, String result, String note) {
        vo.setResult(result);
        vo.setResultName(RESULT_NAMES.get(result));
        vo.setNote(note);
        return new Analyzed(vo, null);
    }
}
