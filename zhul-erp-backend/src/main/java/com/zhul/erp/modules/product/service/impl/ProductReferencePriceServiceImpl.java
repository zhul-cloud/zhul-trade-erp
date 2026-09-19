package com.zhul.erp.modules.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.ReferencePriceVO;
import com.zhul.erp.modules.product.dto.SaveReferencePriceRequest;
import com.zhul.erp.modules.product.entity.ProductReferencePriceDO;
import com.zhul.erp.modules.product.repository.ProductReferencePriceMapper;
import com.zhul.erp.modules.product.service.ProductReferencePriceService;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.modules.product.support.ProductFinder;
import com.zhul.erp.modules.product.support.TextRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Currency;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 精度规则（财务）：金额与本位币金额按 HALF_UP 保留 2 位，汇率保留 6 位；
 * 本位币金额 = 原币金额 × 已舍入的 6 位汇率，最后一步才舍入到 2 位。
 * 参考价是平台层面的参考信息，不是任何租户的报价或售价。
 */
@Service
@RequiredArgsConstructor
public class ProductReferencePriceServiceImpl implements ProductReferencePriceService {

    static final String BASE_CURRENCY = "CNY";
    private static final int AMOUNT_SCALE = 2;
    private static final int RATE_SCALE = 6;
    /** decimal(18,2) 的整数部分最多 16 位；decimal(18,6) 的整数部分最多 12 位 */
    private static final BigDecimal AMOUNT_LIMIT = new BigDecimal("10000000000000000");
    private static final BigDecimal RATE_LIMIT = new BigDecimal("1000000000000");
    private static final Set<String> ISO_CURRENCIES = Currency.getAvailableCurrencies().stream()
            .map(Currency::getCurrencyCode).collect(Collectors.toUnmodifiableSet());

    private final ProductReferencePriceMapper priceMapper;
    private final ProductFinder productFinder;
    private final PlatformScopeGuard platformScopeGuard;

    @Override
    public ReferencePriceVO get(Long productId) {
        productFinder.active(productId);
        ProductReferencePriceDO row = activeRow(productId);
        return row == null ? empty(productId) : toVO(row);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ReferencePriceVO save(Long productId, SaveReferencePriceRequest req) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);

        String currency = currency(req.getCurrencyCode());
        BigDecimal original = amount(req.getPriceOriginal());
        BigDecimal rate;
        BigDecimal cny;
        if (BASE_CURRENCY.equals(currency)) {
            rate = BigDecimal.ONE.setScale(RATE_SCALE);
            cny = original;
        } else {
            rate = rate(req.getExchangeRate());
            // 汇率未维护：本位币留空，界面显示"未计算"，不能存 0
            cny = rate == null ? null : original.multiply(rate).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
            if (cny != null && cny.compareTo(AMOUNT_LIMIT) >= 0) {
                throw BizException.of(ProductErrorCodes.PRICE_INVALID, "折算后的本位币金额过大");
            }
        }
        String source = TextRules.optional(req.getPriceSource(), "价格来源", 128);
        LocalDate priceDate = req.getPriceDate();

        // 唯一键含已软删除的行：清除后再次保存复用原行
        ProductReferencePriceDO existing = priceMapper.selectOne(new LambdaQueryWrapper<ProductReferencePriceDO>()
                .eq(ProductReferencePriceDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductReferencePriceDO::getProductId, productId));
        if (existing == null) {
            ProductReferencePriceDO row = new ProductReferencePriceDO();
            row.setTenantId(ProductConstants.PLATFORM_TENANT_ID);
            row.setProductId(productId);
            row.setPriceOriginal(original);
            row.setCurrencyCode(currency);
            row.setExchangeRate(rate);
            row.setPriceCny(cny);
            row.setPriceSource(source);
            row.setPriceDate(priceDate);
            priceMapper.insert(row);
        } else {
            priceMapper.update(new ProductReferencePriceDO(), new LambdaUpdateWrapper<ProductReferencePriceDO>()
                    .eq(ProductReferencePriceDO::getId, existing.getId())
                    .set(ProductReferencePriceDO::getPriceOriginal, original)
                    .set(ProductReferencePriceDO::getCurrencyCode, currency)
                    .set(ProductReferencePriceDO::getExchangeRate, rate)
                    .set(ProductReferencePriceDO::getPriceCny, cny)
                    .set(ProductReferencePriceDO::getPriceSource, source)
                    .set(ProductReferencePriceDO::getPriceDate, priceDate)
                    .set(ProductReferencePriceDO::getDeletedAt, null));
        }
        return get(productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clear(Long productId) {
        platformScopeGuard.requirePlatform();
        productFinder.lockActive(productId);
        ProductReferencePriceDO row = activeRow(productId);
        if (row == null) {
            return;
        }
        ProductReferencePriceDO change = new ProductReferencePriceDO();
        change.setId(row.getId());
        change.setDeletedAt(LocalDateTime.now());
        priceMapper.updateById(change);
    }

    private ProductReferencePriceDO activeRow(Long productId) {
        return priceMapper.selectOne(new LambdaQueryWrapper<ProductReferencePriceDO>()
                .eq(ProductReferencePriceDO::getTenantId, ProductConstants.PLATFORM_TENANT_ID)
                .eq(ProductReferencePriceDO::getProductId, productId)
                .isNull(ProductReferencePriceDO::getDeletedAt));
    }

    private static String currency(String raw) {
        String code = raw == null ? "" : raw.trim();
        if (code.isEmpty()) {
            throw BizException.of(ProductErrorCodes.CURRENCY_REQUIRED, "请选择币种");
        }
        if (!ISO_CURRENCIES.contains(code)) {
            throw BizException.of(ProductErrorCodes.PRICE_INVALID, "币种必须是三位大写 ISO 4217 代码，如 USD、CNY");
        }
        return code;
    }

    /** 大于 0，按 HALF_UP 保留 2 位后仍大于 0 */
    private static BigDecimal amount(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw BizException.of(ProductErrorCodes.PRICE_INVALID, "参考价必须大于 0");
        }
        BigDecimal rounded = value.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        if (rounded.signum() <= 0) {
            throw BizException.of(ProductErrorCodes.PRICE_INVALID, "参考价必须大于 0");
        }
        if (rounded.compareTo(AMOUNT_LIMIT) >= 0) {
            throw BizException.of(ProductErrorCodes.PRICE_INVALID, "参考价数值过大");
        }
        return rounded;
    }

    /** 未提供返回 null；提供的必须大于 0，保留 6 位 */
    private static BigDecimal rate(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal rounded = value.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        if (value.signum() <= 0 || rounded.signum() <= 0) {
            throw BizException.of(ProductErrorCodes.PRICE_INVALID, "汇率必须大于 0");
        }
        if (rounded.compareTo(RATE_LIMIT) >= 0) {
            throw BizException.of(ProductErrorCodes.PRICE_INVALID, "汇率数值过大");
        }
        return rounded;
    }

    private static ReferencePriceVO empty(Long productId) {
        ReferencePriceVO vo = new ReferencePriceVO();
        vo.setProductId(productId);
        return vo;
    }

    private static ReferencePriceVO toVO(ProductReferencePriceDO row) {
        ReferencePriceVO vo = new ReferencePriceVO();
        vo.setId(row.getId());
        vo.setProductId(row.getProductId());
        vo.setPriceOriginal(row.getPriceOriginal());
        vo.setCurrencyCode(row.getCurrencyCode());
        vo.setExchangeRate(row.getExchangeRate());
        vo.setPriceCny(row.getPriceCny());
        vo.setPriceSource(row.getPriceSource());
        vo.setPriceDate(row.getPriceDate());
        return vo;
    }
}
