package com.zhul.erp.modules.crm.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.crm.entity.OpportunityStageDO;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 对应 specs/crm/opportunity/spec.md「阶段流转」 */
class OpportunityStageRulesTest {

    private static final OpportunityStageRules.Stages STAGES = stages();

    private static OpportunityStageRules.Stages stages() {
        Map<String, OpportunityStageDO> m = new LinkedHashMap<>();
        String[][] rows = {
            {"S1", "新商机", "1", "0"}, {"S2", "需求确认", "1", "0"}, {"S3", "有效商机", "1", "1"},
            {"S4", "已报价", "1", "1"}, {"S5", "报价反馈", "1", "1"}, {"S6", "商务谈判", "1", "1"},
            {"S7", "成交推进", "1", "1"}, {"WON", "赢单", "2", "1"}, {"LOST", "输单", "3", "0"},
            {"INVALID", "无效", "4", "0"}};
        int sort = 10;
        for (String[] r : rows) {
            OpportunityStageDO s = new OpportunityStageDO();
            s.setCode(r[0]);
            s.setName(r[1]);
            s.setCategory(Integer.valueOf(r[2]));
            s.setCountsAsValid(Integer.valueOf(r[3]));
            s.setSortOrder(sort);
            sort += 10;
            m.put(r[0], s);
        }
        return new OpportunityStageRules.Stages(m);
    }

    private static String message(Runnable r) {
        return assertThrows(BizException.class, r::run).getMessage();
    }

    @Test
    void change_betweenActiveStages_forwardAndBackward() {
        assertDoesNotThrow(() -> STAGES.checkChange("S1", "S5"));
        assertDoesNotThrow(() -> STAGES.checkChange("S5", "S2"));
        assertThat(message(() -> STAGES.checkChange("S3", "S3"))).isEqualTo("已经是该阶段");
        assertThat(message(() -> STAGES.checkChange("S3", "WON"))).isEqualTo("只能切换到进行中的阶段");
        assertThat(message(() -> STAGES.checkChange("LOST", "S3"))).isEqualTo("商机已结束，请先重新打开");
    }

    @Test
    void invalid_onlyBeforeValidStages_withReason() {
        assertDoesNotThrow(() -> STAGES.checkClose("S2", "INVALID", 1));
        assertThat(message(() -> STAGES.checkClose("S4", "INVALID", 1))).isEqualTo("S3 及以后请标记为输单");
        assertThat(message(() -> STAGES.checkClose("S1", "INVALID", null))).isEqualTo("请选择无效原因");
        assertThat(message(() -> STAGES.checkClose("S1", "INVALID", 11))).as("输单原因不能用于无效").isEqualTo("请选择无效原因");
    }

    @Test
    void lost_onlyFromValidStages_withReason() {
        assertDoesNotThrow(() -> STAGES.checkClose("S3", "LOST", 15));
        assertThat(message(() -> STAGES.checkClose("S2", "LOST", 15))).isEqualTo("S3 之前的商机请标记为无效");
        assertThat(message(() -> STAGES.checkClose("S5", "LOST", 1))).isEqualTo("请选择输单原因");
    }

    @Test
    void won_onlyFromLastActiveStage() {
        assertDoesNotThrow(() -> STAGES.checkClose("S7", "WON", null));
        assertThat(message(() -> STAGES.checkClose("S5", "WON", null))).isEqualTo("请先推进到 S7 成交推进");
        assertThat(message(() -> STAGES.checkClose("S7", "S3", null))).isEqualTo("只能标记为赢单、输单或无效");
        assertThat(message(() -> STAGES.checkClose("WON", "LOST", 11))).isEqualTo("商机已结束，请先重新打开");
    }

    @Test
    void firstStageAndValidFlags() {
        assertThat(STAGES.first()).isEqualTo("S1");
        assertThat(STAGES.countsAsValid("S2")).isFalse();
        assertThat(STAGES.countsAsValid("S3")).isTrue();
        assertThat(STAGES.countsAsValid("WON")).isTrue();
        assertThat(OpportunityStageRules.Stages.reasonLabel(15)).isEqualTo("选择了竞争对手");
        assertThat(OpportunityStageRules.Stages.reasonLabel(0)).isEmpty();
    }
}
