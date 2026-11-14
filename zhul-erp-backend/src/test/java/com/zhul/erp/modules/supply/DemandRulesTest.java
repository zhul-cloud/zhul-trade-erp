package com.zhul.erp.modules.supply;

import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.support.DemandRules;
import com.zhul.erp.modules.supply.support.DemandRules.Counts;
import com.zhul.erp.modules.supply.support.DemandRules.Presence;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class DemandRulesTest {

    private static Counts counts(int inquiry, int order, int stockout, int lostSupply) {
        return new Counts(inquiry, order, stockout, lostSupply, lostSupply, 0, 0, 0);
    }

    @Test
    void level_takesHighestTier() {
        assertThat(DemandRules.level(new Presence(false, false, 1, 1))).isEqualTo(SupplyConstants.LEVEL_OBSERVE);
        assertThat(DemandRules.level(new Presence(false, false, 2, 1))).isEqualTo(SupplyConstants.LEVEL_REPEAT);
        assertThat(DemandRules.level(new Presence(false, false, 1, 2))).isEqualTo(SupplyConstants.LEVEL_REPEAT);
        assertThat(DemandRules.level(new Presence(true, false, 0, 0))).isEqualTo(SupplyConstants.LEVEL_VERIFIED);
        assertThat(DemandRules.level(new Presence(true, true, 3, 2))).isEqualTo(SupplyConstants.LEVEL_COMMERCIAL);
    }

    @Test
    void priority_boundaries() {
        assertThat(DemandRules.priority(counts(3, 0, 0, 0)).value()).isEqualTo(SupplyConstants.PRIORITY_HIGH);
        assertThat(DemandRules.priority(counts(3, 0, 0, 0)).rule()).isEqualTo("近 90 天询盘 3 次");
        assertThat(DemandRules.priority(counts(2, 0, 0, 0)).value()).isEqualTo(SupplyConstants.PRIORITY_MID);
        assertThat(DemandRules.priority(counts(1, 0, 0, 0)).value()).isEqualTo(SupplyConstants.PRIORITY_LOW);
        assertThat(DemandRules.priority(counts(0, 0, 0, 0)).rule()).isEmpty();
        assertThat(DemandRules.priority(counts(0, 1, 1, 0)).rule()).isEqualTo("有成交且出现过无货");
        assertThat(DemandRules.priority(counts(0, 1, 1, 0)).value()).isEqualTo(SupplyConstants.PRIORITY_HIGH);
        assertThat(DemandRules.priority(counts(0, 1, 0, 0)).value()).isEqualTo(SupplyConstants.PRIORITY_MID);
        assertThat(DemandRules.priority(counts(0, 0, 1, 0)).value()).isEqualTo(SupplyConstants.PRIORITY_LOW);
        assertThat(DemandRules.priority(counts(0, 0, 0, 2)).value()).isEqualTo(SupplyConstants.PRIORITY_HIGH);
        assertThat(DemandRules.priority(counts(0, 0, 0, 1)).value()).isEqualTo(SupplyConstants.PRIORITY_LOW);
    }

    @Test
    void evidence_day30Valid_day31Expired() {
        LocalDate today = LocalDate.of(2026, 11, 14);
        assertThat(DemandRules.evidenceValid(today.minusDays(30), today, 30)).isTrue();
        assertThat(DemandRules.evidenceValid(today.minusDays(31), today, 30)).isFalse();
        assertThat(DemandRules.evidenceValid(today, today, 30)).isTrue();
        assertThat(DemandRules.evidenceValid(null, today, 30)).isFalse();
    }

    @Test
    void suggestCoverage_fourTiers() {
        LocalDate today = LocalDate.of(2026, 11, 14);
        assertThat(DemandRules.suggestCoverage(today.minusDays(12), today, 30, 0)).isEqualTo(SupplyConstants.COVER_READY);
        assertThat(DemandRules.suggestCoverage(today.minusDays(45), today, 30, 3)).isEqualTo(SupplyConstants.COVER_CONFIRM);
        assertThat(DemandRules.suggestCoverage(null, today, 30, 2)).isEqualTo(SupplyConstants.COVER_LEAD);
        assertThat(DemandRules.suggestCoverage(null, today, 30, 0)).isEqualTo(SupplyConstants.COVER_NO_CHANNEL);
    }

    @Test
    void suggestNextDate() {
        LocalDate today = LocalDate.of(2026, 11, 14);
        assertThat(DemandRules.suggestNextDate(SupplyConstants.COVER_NO_CHANNEL, null, today, 30)).isEqualTo(today.plusDays(3));
        assertThat(DemandRules.suggestNextDate(SupplyConstants.COVER_LEAD, null, today, 30)).isEqualTo(today.plusDays(3));
        assertThat(DemandRules.suggestNextDate(SupplyConstants.COVER_CONFIRM, null, today, 30)).isEqualTo(today.plusDays(2));
        assertThat(DemandRules.suggestNextDate(SupplyConstants.COVER_READY, today.minusDays(10), today, 30))
                .isEqualTo(today.plusDays(15));
        assertThat(DemandRules.suggestNextDate(SupplyConstants.COVER_READY, today.minusDays(28), today, 30))
                .isEqualTo(today.plusDays(1));
    }

    @Test
    void rate_halfUp_zeroDenominator() {
        assertThat(DemandRules.rate(14, 112)).isEqualByComparingTo(new BigDecimal("12.5"));
        assertThat(DemandRules.rate(22, 40)).isEqualByComparingTo(new BigDecimal("55.0"));
        assertThat(DemandRules.rate(31, 40)).isEqualByComparingTo(new BigDecimal("77.5"));
        assertThat(DemandRules.rate(13, 31)).isEqualByComparingTo(new BigDecimal("41.9"));
        assertThat(DemandRules.rate(0, 0)).isNull();
    }
}
