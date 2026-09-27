package com.zhul.erp.modules.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.FaqVO;
import com.zhul.erp.modules.product.dto.SaveFaqRequest;
import com.zhul.erp.modules.product.entity.ProductFaqDO;
import com.zhul.erp.modules.product.repository.ProductFaqMapper;
import com.zhul.erp.modules.product.service.ProductFaqService;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.ProductFinder;
import com.zhul.erp.modules.product.support.TextRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ProductFaqServiceImpl implements ProductFaqService {

    /** 来源：1-品类通用模板生成、2-人工撰写或已人工审核确认、3-AI 辅助生成待审核 */
    private static final int SOURCE_MANUAL = 2;
    private static final int SOURCE_PENDING = 3;
    private static final int ANSWER_MAX = 5000;

    private final ProductFaqMapper faqMapper;
    private final ProductFinder productFinder;
    private final PlatformScopeGuard platformScopeGuard;

    @Override
    public List<FaqVO> list(Long productId) {
        productFinder.active(productId);
        // 待审核的 FAQ 只有平台账号能看到，租户账号在这里就被过滤掉，不依赖前端
        boolean platform = platformScopeGuard.isPlatform();
        List<FaqVO> result = new ArrayList<>();
        for (ProductFaqDO row : activeRows(productId)) {
            if (platform || !Objects.equals(row.getSource(), SOURCE_PENDING)) {
                result.add(toVO(row));
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FaqVO create(Long productId, SaveFaqRequest req) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);
        String question = TextRules.required(req.getQuestion(), "问题", 256);
        String answer = TextRules.required(req.getAnswer(), "答案", ANSWER_MAX);
        assertQuestionFree(productId, question, null);

        ProductFaqDO row = new ProductFaqDO();
        row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
        row.setProductId(productId);
        row.setQuestion(question);
        row.setAnswer(answer);
        row.setSource(SOURCE_MANUAL);
        row.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        faqMapper.insert(row);
        return toVO(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FaqVO update(Long productId, Long itemId, SaveFaqRequest req) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);
        ProductFaqDO current = owned(productId, itemId);
        String question = TextRules.required(req.getQuestion(), "问题", 256);
        String answer = TextRules.required(req.getAnswer(), "答案", ANSWER_MAX);
        assertQuestionFree(productId, question, itemId);

        // 来源不在更新字段里：修改待审核 FAQ 的答案后它仍是待审核
        ProductFaqDO change = new ProductFaqDO();
        change.setId(itemId);
        change.setQuestion(question);
        change.setAnswer(answer);
        change.setSortOrder(req.getSortOrder() == null ? current.getSortOrder() : req.getSortOrder());
        faqMapper.updateById(change);
        return toVO(owned(productId, itemId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long productId, Long itemId) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);
        owned(productId, itemId);
        ProductFaqDO change = new ProductFaqDO();
        change.setId(itemId);
        change.setDeletedAt(LocalDateTime.now());
        faqMapper.updateById(change);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FaqVO approve(Long productId, Long itemId) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);
        ProductFaqDO current = owned(productId, itemId);
        if (!Objects.equals(current.getSource(), SOURCE_PENDING)) {
            throw BizException.of(ProductErrorCodes.FAQ_NOT_PENDING, "该 FAQ 不是待审核状态，无需审核");
        }
        ProductFaqDO change = new ProductFaqDO();
        change.setId(itemId);
        change.setSource(SOURCE_MANUAL);
        change.setReviewedBy(SecurityUtils.getCurrentUsername());
        change.setReviewedAt(LocalDateTime.now());
        faqMapper.updateById(change);
        return toVO(owned(productId, itemId));
    }

    /** 同一商品下问题忽略大小写和首尾空格不可重复（含待审核的） */
    private void assertQuestionFree(Long productId, String question, Long excludeId) {
        String normalized = question.toLowerCase(Locale.ROOT);
        for (ProductFaqDO row : activeRows(productId)) {
            if (!Objects.equals(row.getId(), excludeId)
                    && row.getQuestion().trim().toLowerCase(Locale.ROOT).equals(normalized)) {
                throw BizException.of(ProductErrorCodes.CONTENT_DUPLICATE, "该 FAQ 已存在", Map.of("existingId", row.getId()));
            }
        }
    }

    private List<ProductFaqDO> activeRows(Long productId) {
        return faqMapper.selectList(new LambdaQueryWrapper<ProductFaqDO>()
                .eq(ProductFaqDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductFaqDO::getProductId, productId)
                .isNull(ProductFaqDO::getDeletedAt)
                .orderByAsc(ProductFaqDO::getSortOrder)
                .orderByAsc(ProductFaqDO::getId));
    }

    private ProductFaqDO owned(Long productId, Long itemId) {
        ProductFaqDO row = itemId == null ? null : faqMapper.selectOne(new LambdaQueryWrapper<ProductFaqDO>()
                .eq(ProductFaqDO::getId, itemId)
                .eq(ProductFaqDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductFaqDO::getProductId, productId)
                .isNull(ProductFaqDO::getDeletedAt));
        if (row == null) {
            throw BizException.of(ProductErrorCodes.CONTENT_NOT_FOUND, "FAQ 不存在");
        }
        return row;
    }

    private static FaqVO toVO(ProductFaqDO row) {
        FaqVO vo = new FaqVO();
        vo.setId(row.getId());
        vo.setProductId(row.getProductId());
        vo.setQuestion(row.getQuestion());
        vo.setAnswer(row.getAnswer());
        vo.setSource(row.getSource());
        vo.setReviewedBy(row.getReviewedBy());
        vo.setReviewedAt(row.getReviewedAt());
        vo.setSortOrder(row.getSortOrder());
        return vo;
    }
}
