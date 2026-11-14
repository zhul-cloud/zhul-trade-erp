package com.zhul.erp.modules.supply.service;

import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.entity.ScDevCandidateDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** 候选阶段与提醒规则（tasks 4.2、4.6） */
class SupplierDevRulesTest {

    @Test
    void targetStage_qualifiedOnlyToAbandoned() {
        assertThat(SupplierDevService.targetStage(SupplyConstants.CAND_TODO, null)).isEqualTo(SupplyConstants.CAND_TODO);
        assertThat(SupplierDevService.targetStage(SupplyConstants.CAND_TODO, SupplyConstants.CAND_QUOTED))
                .isEqualTo(SupplyConstants.CAND_QUOTED);
        assertThat(SupplierDevService.targetStage(SupplyConstants.CAND_QUALIFIED, SupplyConstants.CAND_CONTACTED))
                .as("合格只由系统改").isEqualTo(SupplyConstants.CAND_QUALIFIED);
        assertThat(SupplierDevService.targetStage(SupplyConstants.CAND_QUALIFIED, SupplyConstants.CAND_ABANDONED))
                .isEqualTo(SupplyConstants.CAND_ABANDONED);
        assertThat(SupplierDevService.targetStage(SupplyConstants.CAND_ABANDONED, SupplyConstants.CAND_CONTACTED))
                .as("放弃后可以重新跟进").isEqualTo(SupplyConstants.CAND_CONTACTED);
    }

    @Test
    void stageMatch_gapIsTodoPlusDoing() {
        assertThat(SupplierDevService.stageMatch("gap", "todo")).isTrue();
        assertThat(SupplierDevService.stageMatch("gap", "doing")).isTrue();
        assertThat(SupplierDevService.stageMatch("gap", "done")).isFalse();
        assertThat(SupplierDevService.stageMatch("done", "done")).isTrue();
        assertThat(SupplierDevService.stageMatch("todo", "doing")).isFalse();
        assertThat(SupplierDevService.stageMatch("all", "done")).isTrue();
    }

    @Test
    void linkReminder_afterSevenDaysQuotedAndUnlinked() {
        LocalDate today = LocalDate.of(2026, 11, 14);
        ScDevCandidateDO c = new ScDevCandidateDO();
        c.setStage(SupplyConstants.CAND_QUOTED);
        c.setSupplierId(0L);
        c.setQuotedAt(today.minusDays(6));
        assertThat(SupplierDevService.linkReminder(c, today)).as("第 6 天不提醒").isFalse();
        c.setQuotedAt(today.minusDays(7));
        assertThat(SupplierDevService.linkReminder(c, today)).as("满 7 天提醒").isTrue();
        c.setSupplierId(5L);
        assertThat(SupplierDevService.linkReminder(c, today)).as("已关联不提醒").isFalse();
        c.setSupplierId(0L);
        c.setStage(SupplyConstants.CAND_CONTACTED);
        assertThat(SupplierDevService.linkReminder(c, today)).as("不在已报价阶段不提醒").isFalse();
    }
}
