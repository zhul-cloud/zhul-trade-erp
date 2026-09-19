package com.zhul.erp.modules.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.CustomsVO;
import com.zhul.erp.modules.product.dto.SaveCustomsRequest;
import com.zhul.erp.modules.product.entity.ProductCustomsDO;
import com.zhul.erp.modules.product.repository.ProductCustomsMapper;
import com.zhul.erp.modules.product.service.ProductCustomsService;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.ProductFinder;
import com.zhul.erp.modules.product.support.TextRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 精度规则：出口退税率 0~100，按 HALF_UP 保留 2 位小数。
 */
@Service
@RequiredArgsConstructor
public class ProductCustomsServiceImpl implements ProductCustomsService {

    private static final Pattern HS_CODE = Pattern.compile("^\\d{6,10}$");
    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final ProductCustomsMapper customsMapper;
    private final ProductFinder productFinder;
    private final PlatformScopeGuard platformScopeGuard;

    @Override
    public CustomsVO get(Long productId) {
        productFinder.active(productId);
        ProductCustomsDO row = customsMapper.selectOne(new LambdaQueryWrapper<ProductCustomsDO>()
                .eq(ProductCustomsDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductCustomsDO::getProductId, productId)
                .isNull(ProductCustomsDO::getDeletedAt));
        return row == null ? empty(productId) : toVO(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CustomsVO save(Long productId, SaveCustomsRequest req) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);

        String hsCode = hsCode(req.getHsCode());
        String country = country(req.getOriginCountry());
        BigDecimal rebate = rebate(req.getExportRebateRate());
        String nameCn = TextRules.optional(req.getCustomsNameCn(), "申报品名（中文）", 128);
        String nameEn = TextRules.optional(req.getCustomsNameEn(), "申报品名（英文）", 128);
        String elements = TextRules.optional(req.getDeclarationElements(), "申报要素", 500);
        String supervision = TextRules.optional(req.getSupervisionConditions(), "监管条件", 32);

        ProductCustomsDO existing = customsMapper.selectOne(new LambdaQueryWrapper<ProductCustomsDO>()
                .eq(ProductCustomsDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductCustomsDO::getProductId, productId));
        if (existing == null) {
            ProductCustomsDO row = new ProductCustomsDO();
            row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            row.setProductId(productId);
            row.setHsCode(hsCode);
            row.setCustomsNameCn(nameCn);
            row.setCustomsNameEn(nameEn);
            row.setOriginCountry(country);
            row.setDeclarationElements(elements);
            row.setSupervisionConditions(supervision);
            row.setExportRebateRate(rebate);
            customsMapper.insert(row);
        } else {
            customsMapper.update(new ProductCustomsDO(), new LambdaUpdateWrapper<ProductCustomsDO>()
                    .eq(ProductCustomsDO::getId, existing.getId())
                    .set(ProductCustomsDO::getHsCode, hsCode)
                    .set(ProductCustomsDO::getCustomsNameCn, nameCn)
                    .set(ProductCustomsDO::getCustomsNameEn, nameEn)
                    .set(ProductCustomsDO::getOriginCountry, country)
                    .set(ProductCustomsDO::getDeclarationElements, elements)
                    .set(ProductCustomsDO::getSupervisionConditions, supervision)
                    .set(ProductCustomsDO::getExportRebateRate, rebate)
                    .set(ProductCustomsDO::getDeletedAt, null));
        }
        return get(productId);
    }

    /** 去掉点和空格后须为 6~10 位数字，只存数字；空表示未维护 */
    private static String hsCode(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[.\\s]", "");
        if (digits.isEmpty()) {
            return "";
        }
        if (!HS_CODE.matcher(digits).matches()) {
            throw BizException.of(ProductErrorCodes.HS_CODE_INVALID, "HS 编码格式不合法，去掉点和空格后应为 6 到 10 位数字");
        }
        return digits;
    }

    private static String country(String raw) {
        String code = raw == null ? "" : raw.trim();
        if (code.isEmpty()) {
            return "";
        }
        if (!ISO_COUNTRIES.contains(code)) {
            throw BizException.of(ProductErrorCodes.COUNTRY_CODE_INVALID, "原产国必须是两位大写国家码，如 DE、CN");
        }
        return code;
    }

    private static BigDecimal rebate(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() < 0 || value.compareTo(HUNDRED) > 0) {
            throw BizException.of(ProductErrorCodes.PARAM_INVALID, "出口退税率必须在 0 到 100 之间");
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static CustomsVO empty(Long productId) {
        CustomsVO vo = new CustomsVO();
        vo.setProductId(productId);
        vo.setHsCode("");
        vo.setCustomsNameCn("");
        vo.setCustomsNameEn("");
        vo.setOriginCountry("");
        vo.setDeclarationElements("");
        vo.setSupervisionConditions("");
        return vo;
    }

    private static CustomsVO toVO(ProductCustomsDO row) {
        CustomsVO vo = new CustomsVO();
        vo.setId(row.getId());
        vo.setProductId(row.getProductId());
        vo.setHsCode(row.getHsCode());
        vo.setCustomsNameCn(row.getCustomsNameCn());
        vo.setCustomsNameEn(row.getCustomsNameEn());
        vo.setOriginCountry(row.getOriginCountry());
        vo.setDeclarationElements(row.getDeclarationElements());
        vo.setSupervisionConditions(row.getSupervisionConditions());
        vo.setExportRebateRate(row.getExportRebateRate());
        return vo;
    }
}
