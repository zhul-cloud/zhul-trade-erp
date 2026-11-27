package com.zhul.erp.modules.website.support;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 站点售价（spec website-publish「站点商品」），取第一个有值的：
 * 1. 手工售价；2. 近 180 天询价最低人民币单价 × 系数 ÷ 美元汇率；3. 平台参考价（美元，或按人民币参考价 ÷ 美元汇率）；4. 空（站点显示询价）。
 * 精度：中间全程 BigDecimal，除法保留 10 位，最终 HALF_UP 保留 2 位；不大于 0 的金额视为没有值。
 */
public final class SitePrice {

    public static final String SOURCE_MANUAL = "manual";
    public static final String SOURCE_QUOTE = "quote";
    public static final String SOURCE_REFERENCE = "reference";
    public static final String SOURCE_IMPORT = "import";

    private static final int DIVIDE_SCALE = 10;

    private SitePrice() {
    }

    /** 询价最低价：回价 id、不含税人民币单价、提交时间 */
    public record Quote(Long quoteId, BigDecimal unitPriceCny, LocalDateTime quotedAt) {
    }

    /** 美元汇率：1 美元 = rate 人民币 */
    public record Rate(BigDecimal rate, LocalDate rateDate) {
    }

    /** 平台参考价：原币金额、币种、人民币金额（汇率未维护时为空） */
    public record Reference(BigDecimal priceOriginal, String currencyCode, BigDecimal priceCny) {
    }

    /** 计算结果；amount 为空表示没有售价。依据字段按来源填写，其余为空 */
    public record Result(BigDecimal amount, String source, Long quoteId, BigDecimal unitPriceCny, BigDecimal factor,
                         BigDecimal rate, LocalDate rateDate, LocalDateTime quotedAt) {
        public static final Result NONE = new Result(null, null, null, null, null, null, null, null);
    }

    public static Result calc(BigDecimal manual, Quote quote, BigDecimal factor, Rate usd, Reference reference) {
        if (positive(manual)) {
            return new Result(manual.setScale(2, RoundingMode.HALF_UP), SOURCE_MANUAL, null, null, null, null, null, null);
        }
        boolean hasRate = usd != null && positive(usd.rate());
        if (quote != null && positive(quote.unitPriceCny()) && positive(factor) && hasRate) {
            BigDecimal amount = quote.unitPriceCny().multiply(factor)
                    .divide(usd.rate(), DIVIDE_SCALE, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
            if (positive(amount)) {
                return new Result(amount, SOURCE_QUOTE, quote.quoteId(), quote.unitPriceCny(), factor, usd.rate(), usd.rateDate(),
                        quote.quotedAt());
            }
        }
        if (reference != null) {
            if ("USD".equals(reference.currencyCode()) && positive(reference.priceOriginal())) {
                return new Result(reference.priceOriginal().setScale(2, RoundingMode.HALF_UP), SOURCE_REFERENCE,
                        null, null, null, null, null, null);
            }
            if (hasRate && positive(reference.priceCny())) {
                BigDecimal amount = reference.priceCny().divide(usd.rate(), DIVIDE_SCALE, RoundingMode.HALF_UP)
                        .setScale(2, RoundingMode.HALF_UP);
                if (positive(amount)) {
                    return new Result(amount, SOURCE_REFERENCE, null, null, null, usd.rate(), usd.rateDate(), null);
                }
            }
        }
        return Result.NONE;
    }

    private static boolean positive(BigDecimal v) {
        return v != null && v.signum() > 0;
    }
}
