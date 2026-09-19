package com.zhul.erp.modules.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.SaveSpecificationsRequest;
import com.zhul.erp.modules.product.dto.SpecificationVO;
import com.zhul.erp.modules.product.entity.ProductSpecificationDO;
import com.zhul.erp.modules.product.repository.ProductSpecificationMapper;
import com.zhul.erp.modules.product.service.ProductSpecificationService;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.ProductFinder;
import com.zhul.erp.modules.product.support.TextRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProductSpecificationServiceImpl implements ProductSpecificationService {

    private static final int MAX_ITEMS = 200;
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9_]*$");

    private final ProductSpecificationMapper specificationMapper;
    private final ProductFinder productFinder;
    private final PlatformScopeGuard platformScopeGuard;

    @Override
    public List<SpecificationVO> list(Long productId) {
        productFinder.active(productId);
        return loadAll(productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<SpecificationVO> replace(Long productId, SaveSpecificationsRequest req) {
        platformScopeGuard.requirePlatform();
        // 锁商品行，让同一商品的并发整体替换串行执行
        productFinder.lockActive(productId);

        List<ProductSpecificationDO> rows = validate(productId, req == null ? null : req.getItems());

        // 先软删除现有规格再插入新集合；提交的任何一条不合法都在这之前就已拒绝，原规格保持不变
        specificationMapper.update(new ProductSpecificationDO(), new LambdaUpdateWrapper<ProductSpecificationDO>()
                .eq(ProductSpecificationDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductSpecificationDO::getProductId, productId)
                .isNull(ProductSpecificationDO::getDeletedAt)
                .set(ProductSpecificationDO::getDeletedAt, LocalDateTime.now()));
        for (ProductSpecificationDO row : rows) {
            specificationMapper.insert(row);
        }
        return loadAll(productId);
    }

    private List<ProductSpecificationDO> validate(Long productId, List<SaveSpecificationsRequest.SpecificationItem> items) {
        List<SaveSpecificationsRequest.SpecificationItem> source = items == null ? List.of() : items;
        if (source.size() > MAX_ITEMS) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "规格最多 " + MAX_ITEMS + " 条");
        }
        Set<String> keys = new HashSet<>(source.size() * 2);
        List<ProductSpecificationDO> rows = new ArrayList<>(source.size());
        int index = 0;
        for (SaveSpecificationsRequest.SpecificationItem item : source) {
            String key = TextRules.required(item.getSpecKey(), "规格编码", 64);
            if (!KEY_PATTERN.matcher(key).matches()) {
                throw BizException.of(ProductErrorCodes.PARAM_INVALID,
                        "规格编码格式不合法：" + key + "，须以小写字母开头，只含小写字母、数字和下划线");
            }
            if (!keys.add(key)) {
                throw BizException.of(ProductErrorCodes.PARAM_INVALID, "规格编码重复：" + key);
            }
            int verified = item.getVerified() == null ? 0 : item.getVerified();
            if (verified != 0 && verified != 1) {
                throw BizException.of(ProductErrorCodes.PARAM_INVALID, "是否已核实只能是 0 或 1");
            }
            ProductSpecificationDO row = new ProductSpecificationDO();
            row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            row.setProductId(productId);
            row.setSpecKey(key);
            row.setSpecLabel(TextRules.required(item.getSpecLabel(), "规格名称", 64));
            row.setSpecValue(TextRules.required(item.getSpecValue(), "规格值", 256));
            row.setSpecUnit(TextRules.optional(item.getSpecUnit(), "规格单位", 32));
            row.setSource(TextRules.optional(item.getSource(), "规格来源", 128));
            row.setVerified(verified);
            row.setSortOrder(item.getSortOrder() == null ? index : item.getSortOrder());
            rows.add(row);
            index++;
        }
        return rows;
    }

    private List<SpecificationVO> loadAll(Long productId) {
        List<ProductSpecificationDO> rows = specificationMapper.selectList(new LambdaQueryWrapper<ProductSpecificationDO>()
                .eq(ProductSpecificationDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductSpecificationDO::getProductId, productId)
                .isNull(ProductSpecificationDO::getDeletedAt)
                .orderByAsc(ProductSpecificationDO::getSortOrder)
                .orderByAsc(ProductSpecificationDO::getId));
        List<SpecificationVO> result = new ArrayList<>(rows.size());
        for (ProductSpecificationDO row : rows) {
            SpecificationVO vo = new SpecificationVO();
            vo.setId(row.getId());
            vo.setSpecKey(row.getSpecKey());
            vo.setSpecLabel(row.getSpecLabel());
            vo.setSpecValue(row.getSpecValue());
            vo.setSpecUnit(row.getSpecUnit());
            vo.setSource(row.getSource());
            vo.setVerified(row.getVerified());
            vo.setSortOrder(row.getSortOrder());
            result.add(vo);
        }
        return result;
    }
}
