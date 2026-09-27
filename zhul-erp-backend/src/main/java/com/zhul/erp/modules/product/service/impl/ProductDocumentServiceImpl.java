package com.zhul.erp.modules.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.DocumentVO;
import com.zhul.erp.modules.product.dto.SaveDocumentRequest;
import com.zhul.erp.modules.product.entity.ProductDocumentDO;
import com.zhul.erp.modules.product.repository.ProductDocumentMapper;
import com.zhul.erp.modules.product.service.ProductDocumentService;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.ProductFinder;
import com.zhul.erp.modules.product.support.TextRules;
import com.zhul.erp.modules.product.support.UrlRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductDocumentServiceImpl implements ProductDocumentService {

    private static final int MIN_TYPE = 1;
    private static final int MAX_TYPE = 8;

    private final ProductDocumentMapper documentMapper;
    private final ProductFinder productFinder;
    private final PlatformScopeGuard platformScopeGuard;

    /** 校验后的入参 */
    private record Input(int type, String title, String fileUrl, String language, String version, String source,
                          int verified, Integer sortOrder) {
    }

    @Override
    public List<DocumentVO> list(Long productId) {
        productFinder.active(productId);
        List<ProductDocumentDO> rows = documentMapper.selectList(new LambdaQueryWrapper<ProductDocumentDO>()
                .eq(ProductDocumentDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductDocumentDO::getProductId, productId)
                .isNull(ProductDocumentDO::getDeletedAt)
                .orderByAsc(ProductDocumentDO::getSortOrder)
                .orderByAsc(ProductDocumentDO::getId));
        List<DocumentVO> result = new ArrayList<>(rows.size());
        for (ProductDocumentDO row : rows) {
            result.add(toVO(row));
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVO create(Long productId, SaveDocumentRequest req) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);
        Input in = validate(req);
        assertUrlFree(productId, in.fileUrl(), null);

        ProductDocumentDO row = new ProductDocumentDO();
        row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
        row.setProductId(productId);
        row.setDocumentType(in.type());
        row.setTitle(in.title());
        row.setFileUrl(in.fileUrl());
        row.setLanguage(in.language());
        row.setVersion(in.version());
        row.setSource(in.source());
        row.setVerified(in.verified());
        row.setVerifiedAt(in.verified() == 1 ? LocalDateTime.now() : null);
        row.setSortOrder(in.sortOrder() == null ? 0 : in.sortOrder());
        documentMapper.insert(row);
        return toVO(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentVO update(Long productId, Long itemId, SaveDocumentRequest req) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);
        ProductDocumentDO current = owned(productId, itemId);
        Input in = validate(req);
        assertUrlFree(productId, in.fileUrl(), itemId);

        // 已核实的资料再次保存时保留原核实时间；取消核实则清空。verified_at 可能要写 NULL，所以用 set
        LocalDateTime verifiedAt = null;
        if (in.verified() == 1) {
            verifiedAt = Objects.equals(current.getVerified(), 1) && current.getVerifiedAt() != null
                    ? current.getVerifiedAt() : LocalDateTime.now();
        }
        ProductDocumentDO change = new ProductDocumentDO();
        change.setDocumentType(in.type());
        change.setTitle(in.title());
        change.setFileUrl(in.fileUrl());
        change.setLanguage(in.language());
        change.setVersion(in.version());
        change.setSource(in.source());
        change.setVerified(in.verified());
        change.setSortOrder(in.sortOrder() == null ? current.getSortOrder() : in.sortOrder());
        documentMapper.update(change, new LambdaUpdateWrapper<ProductDocumentDO>()
                .eq(ProductDocumentDO::getId, itemId)
                .set(ProductDocumentDO::getVerifiedAt, verifiedAt));
        return toVO(owned(productId, itemId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long productId, Long itemId) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);
        owned(productId, itemId);
        ProductDocumentDO change = new ProductDocumentDO();
        change.setId(itemId);
        change.setDeletedAt(LocalDateTime.now());
        documentMapper.updateById(change);
    }

    private Input validate(SaveDocumentRequest req) {
        Integer type = req.getDocumentType();
        if (type == null || type < MIN_TYPE || type > MAX_TYPE) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "文档类型不合法");
        }
        String title = TextRules.required(req.getTitle(), "文档标题", 128);
        String url = TextRules.required(req.getFileUrl(), "文件地址", 512);
        if (!UrlRules.isSafe(url)) {
            throw BizException.of(ProductErrorCodes.DOCUMENT_URL_INVALID,
                    "文件地址格式不合法，只允许 http://、https:// 或以 / 开头的站内路径");
        }
        String language = TextRules.optional(req.getLanguage(), "语言", 8);
        int verified = req.getVerified() == null ? 0 : req.getVerified();
        if (verified != 0 && verified != 1) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "是否已核实只能是 0 或 1");
        }
        return new Input(type, title, url, language.isEmpty() ? "en" : language,
                TextRules.optional(req.getVersion(), "文档版本", 32),
                TextRules.optional(req.getSource(), "数据来源", 128), verified, req.getSortOrder());
    }

    /** 同一商品下文件地址相同的未删除资料不可重复 */
    private void assertUrlFree(Long productId, String fileUrl, Long excludeId) {
        Long same = documentMapper.selectCount(new LambdaQueryWrapper<ProductDocumentDO>()
                .eq(ProductDocumentDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductDocumentDO::getProductId, productId)
                .eq(ProductDocumentDO::getFileUrl, fileUrl)
                .ne(excludeId != null, ProductDocumentDO::getId, excludeId)
                .isNull(ProductDocumentDO::getDeletedAt));
        if (same > 0) {
            throw BizException.of(ProductErrorCodes.CONTENT_DUPLICATE, "该技术资料已存在", Map.of("field", "fileUrl"));
        }
    }

    /** 资料必须属于这个商品且未删除，防止用别的商品的资料 ID 越权修改 */
    private ProductDocumentDO owned(Long productId, Long itemId) {
        ProductDocumentDO row = itemId == null ? null : documentMapper.selectOne(new LambdaQueryWrapper<ProductDocumentDO>()
                .eq(ProductDocumentDO::getId, itemId)
                .eq(ProductDocumentDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductDocumentDO::getProductId, productId)
                .isNull(ProductDocumentDO::getDeletedAt));
        if (row == null) {
            throw BizException.of(ProductErrorCodes.CONTENT_NOT_FOUND, "技术资料不存在");
        }
        return row;
    }

    private static DocumentVO toVO(ProductDocumentDO row) {
        DocumentVO vo = new DocumentVO();
        vo.setId(row.getId());
        vo.setProductId(row.getProductId());
        vo.setDocumentType(row.getDocumentType());
        vo.setTitle(row.getTitle());
        vo.setFileUrl(row.getFileUrl());
        vo.setLanguage(row.getLanguage());
        vo.setVersion(row.getVersion());
        vo.setSource(row.getSource());
        vo.setVerified(row.getVerified());
        vo.setVerifiedAt(row.getVerifiedAt());
        vo.setSortOrder(row.getSortOrder());
        return vo;
    }
}
