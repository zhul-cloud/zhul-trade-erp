package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.inquiry.constants.InquiryConstants;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingQuoteDO;
import com.zhul.erp.modules.inquiry.sourcing.entity.SourcingTaskDO;
import com.zhul.erp.modules.inquiry.sourcing.service.impl.PriceHistoryServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 纯规则：默认选价、超时判断、型号归一化 */
class InquiryRulesTest {

    private static SourcingQuoteDO quote(long id, String price, int condition, boolean noStock) {
        SourcingQuoteDO q = new SourcingQuoteDO();
        q.setId(id);
        q.setUnitPriceCny(price == null ? null : new BigDecimal(price));
        q.setItemCondition(condition);
        q.setNoStock(noStock ? 1 : 0);
        return q;
    }

    @Test
    void defaultPick_prefersCheapestBrandNew() {
        assertThat(PriceHistoryServiceImpl.pickDefault(List.of(quote(1, "3200", 1, false), quote(2, "1800", 4, false), quote(3, "3100", 1, false))).getId())
                .isEqualTo(3L);
        assertThat(PriceHistoryServiceImpl.pickDefault(List.of(quote(1, "1800", 4, false), quote(2, "900", 3, false))).getId())
                .as("没有全新原装时取最低价").isEqualTo(2L);
        assertThat(PriceHistoryServiceImpl.pickDefault(List.of(quote(1, null, 0, true)))).as("只有无货时没有选定价格").isNull();
        assertThat(PriceHistoryServiceImpl.pickDefault(List.of(quote(1, "0", 1, false))).getId()).as("零元也是有效价格").isEqualTo(1L);
    }

    @Test
    void timeout_normalAndUrgent() {
        TaskTimeout limits = new TaskTimeout(24, 4);
        LocalDateTime now = LocalDateTime.of(2026, 10, 1, 12, 0);
        SourcingTaskDO t = new SourcingTaskDO();
        t.setStatus(InquiryConstants.TASK_SOURCING);
        t.setUrgent(0);
        t.setFirstAssignedAt(now.minusHours(24));
        assertThat(limits.isTimeout(t, now)).as("刚好 24 小时不算超时").isFalse();
        t.setFirstAssignedAt(now.minusHours(25));
        assertThat(limits.isTimeout(t, now)).isTrue();
        assertThat(limits.remainingMinutes(t, now)).isEqualTo(-60L);
        t.setUrgent(1);
        t.setFirstAssignedAt(now.minusHours(5));
        assertThat(limits.isTimeout(t, now)).isTrue();
        t.setStatus(InquiryConstants.TASK_DONE);
        assertThat(limits.isTimeout(t, now)).as("已回价不算超时").isFalse();
        assertThat(limits.remainingMinutes(t, now)).isNull();
    }

    @Test
    void modelKey_sameRuleAsProductMaster() {
        assertThat(PriceKeys.model("6ES7 214-1AG40-0XB0")).isEqualTo(PriceKeys.model("6es7214 1ag40 0xb0"));
        assertThat(PriceKeys.model("ＥＳ７")).isEqualTo("es7");
        assertThat(PriceKeys.model(" - / ")).isEmpty();
    }
}
