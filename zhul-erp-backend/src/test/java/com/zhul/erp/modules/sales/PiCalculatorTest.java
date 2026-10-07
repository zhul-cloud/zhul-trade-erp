package com.zhul.erp.modules.sales;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PiFeeDO;
import com.zhul.erp.modules.sales.entity.PiItemDO;
import com.zhul.erp.modules.sales.entity.PiVersionDO;
import com.zhul.erp.modules.sales.support.PiCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** spec sales/proforma-invoice「PI 内容」：整单折扣、合计、折扣后的净利润与毛利率 */
class PiCalculatorTest {

    private static final BigDecimal USD = new BigDecimal("7.150000");

    private static PiItemDO item(String cost, int qty, String price) {
        PiItemDO i = new PiItemDO();
        i.setCostPrice(cost == null ? null : new BigDecimal(cost));
        i.setQuantity(qty);
        i.setUnitPrice(new BigDecimal(price));
        PiCalculator.applyLine(i, USD);
        return i;
    }

    private static PiFeeDO fee(String amount) {
        PiFeeDO f = new PiFeeDO();
        f.setAmount(new BigDecimal(amount));
        return f;
    }

    /** 原型与规格的例子：型号小计 1,340.68 + 运费 60 − 5% 折扣 */
    @Test
    void fivePercentDiscount() {
        List<PiItemDO> items = List.of(item("4500", 1, "740.44"), item("1230", 2, "180.00"), item("420", 2, "65.73"), item("350", 2, "54.39"));
        PiVersionDO v = new PiVersionDO();
        v.setDiscountType(SalesConstants.DISCOUNT_PERCENT);
        v.setDiscountValue(new BigDecimal("5"));
        PiCalculator.applyTotals(v, items, List.of(fee("60"), fee("0")), USD);
        assertThat(v.getItemAmount()).isEqualByComparingTo("1340.68");
        assertThat(v.getDiscountAmount()).isEqualByComparingTo("67.03");
        assertThat(v.getTotalAmount()).isEqualByComparingTo("1333.65");
        assertThat(v.getTotalAmountCny()).isEqualByComparingTo("9535.60");
        assertThat(v.getNetProfit()).isEqualByComparingTo("84.84");
        assertThat(v.getNetProfitCny()).isEqualByComparingTo("606.60");
        assertThat(v.getMarginRate().setScale(1, java.math.RoundingMode.HALF_UP)).isEqualByComparingTo("6.7");
    }

    @Test
    void noDiscount_profitEqualsLines() {
        List<PiItemDO> items = List.of(item("520", 2, "85.56"));
        PiVersionDO v = new PiVersionDO();
        PiCalculator.applyTotals(v, items, List.of(), USD);
        assertThat(v.getDiscountAmount()).isEqualByComparingTo("0");
        assertThat(v.getNetProfit()).isEqualByComparingTo("25.67");
        assertThat(v.getNetProfitCny()).isEqualByComparingTo("183.51");
    }

    @Test
    void discountRules() {
        assertThatThrownBy(() -> PiCalculator.discount(SalesConstants.DISCOUNT_AMOUNT, new BigDecimal("2000"), new BigDecimal("1340.68")))
                .isInstanceOf(BizException.class).hasMessage("折扣不能超过型号小计");
        assertThatThrownBy(() -> PiCalculator.discount(SalesConstants.DISCOUNT_PERCENT, new BigDecimal("101"), BigDecimal.TEN))
                .hasMessageContaining("0–100%");
        assertThatThrownBy(() -> PiCalculator.discount(SalesConstants.DISCOUNT_AMOUNT, new BigDecimal("-1"), BigDecimal.TEN))
                .hasMessage("折扣不能为负");
        assertThat(PiCalculator.discount(SalesConstants.DISCOUNT_AMOUNT, new BigDecimal("42.555"), new BigDecimal("100"))).isEqualByComparingTo("42.56");
        assertThat(PiCalculator.discount(SalesConstants.DISCOUNT_PERCENT, new BigDecimal("100"), new BigDecimal("100"))).isEqualByComparingTo("100");
    }

    @Test
    void noCostLinesHaveNoProfit() {
        List<PiItemDO> items = List.of(item(null, 1, "120"));
        PiVersionDO v = new PiVersionDO();
        v.setDiscountType(SalesConstants.DISCOUNT_AMOUNT);
        v.setDiscountValue(new BigDecimal("20"));
        PiCalculator.applyTotals(v, items, List.of(fee("60")), USD);
        assertThat(v.getTotalAmount()).isEqualByComparingTo("160");
        assertThat(v.getNetProfit()).isNull();
        assertThat(v.getMarginRate()).isNull();
    }
}
