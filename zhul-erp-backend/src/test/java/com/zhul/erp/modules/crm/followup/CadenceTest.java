package com.zhul.erp.modules.crm.followup;

import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.support.Cadence;
import com.zhul.erp.modules.crm.followup.support.FollowupSettings;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 系统提醒节奏（tasks 5.3）与金额档（tasks 5.2）：纯函数，不读库 */
class CadenceTest {

    private static final FollowupSettings.Params P = FollowupSettings.Params.defaults();
    private static final LocalDate SENT = LocalDate.of(2026, 11, 1);
    private static final Map<String, Map<String, String>> ACTIONS = Map.of(
            "S4", Map.of("d1", "确认客户收到报价", "d3", "问客户对价格、交期还是产品状态有疑问", "d14", "发收尾邮件"),
            "S7", Map.of("pi", "确认付款安排"));

    private static Cadence.Reminder quote(LocalDate today) {
        return Cadence.of(FollowupConstants.GROUP_AFTER_QUOTE, "S4", SENT, null, today, P, ACTIONS);
    }

    @Test
    void quote_beforeFirstNode_remindsDay1() {
        Cadence.Reminder r = quote(SENT);
        assertThat(r.date()).isEqualTo(SENT.plusDays(1));
        assertThat(r.text()).isEqualTo("第 1 天：确认客户收到报价");
    }

    @Test
    void quote_day3_matchesSpecScenario() {
        Cadence.Reminder r = quote(LocalDate.of(2026, 11, 4));
        assertThat(r.date()).isEqualTo(LocalDate.of(2026, 11, 4));
        assertThat(r.text()).isEqualTo("第 3 天：问客户对价格、交期还是产品状态有疑问");
    }

    @Test
    void quote_betweenNodes_keepsLastReachedNodeOverdue() {
        // 第 5 天：第 3 天的节点没处理，保持逾期显示，第 7 天到了才换
        assertThat(quote(SENT.plusDays(5)).date()).isEqualTo(SENT.plusDays(3));
        assertThat(quote(SENT.plusDays(7)).date()).isEqualTo(SENT.plusDays(7));
    }

    @Test
    void quote_afterDay14_noNewNode() {
        Cadence.Reminder r = quote(SENT.plusDays(30));
        assertThat(r.date()).isEqualTo(SENT.plusDays(14));
        assertThat(r.text()).isEqualTo("第 14 天：发收尾邮件");
    }

    @Test
    void quote_missingAction_fallsBackToDefaultText() {
        assertThat(quote(SENT.plusDays(7)).text()).isEqualTo("第 7 天：跟进报价，问清客户的反馈");
    }

    @Test
    void pi_withExpectedPayDate_staysOverdueAfterIt() {
        LocalDate pay = LocalDate.of(2026, 11, 3);
        Cadence.Reminder r = Cadence.of(FollowupConstants.GROUP_AFTER_PI, "S7", SENT, pay, LocalDate.of(2026, 11, 5), P, ACTIONS);
        assertThat(r.date()).isEqualTo(pay);
        assertThat(r.text()).isEqualTo("客户说 11-03 付款：确认付款安排");
    }

    @Test
    void pi_withoutExpectedPayDate_every2Days() {
        assertThat(Cadence.of(FollowupConstants.GROUP_AFTER_PI, "S7", SENT, null, SENT, P, ACTIONS).date())
                .isEqualTo(SENT.plusDays(2));
        assertThat(Cadence.of(FollowupConstants.GROUP_AFTER_PI, "S7", SENT, null, SENT.plusDays(5), P, ACTIONS).date())
                .isEqualTo(SENT.plusDays(4));
    }

    @Test
    void tenantOverride_changesNodes() {
        FollowupSettings.Params p = new FollowupSettings.Params(List.of(2, 5), 3, 60, 14, 3, P.amountTiers());
        assertThat(Cadence.of(FollowupConstants.GROUP_AFTER_QUOTE, "S4", SENT, null, SENT.plusDays(6), p, ACTIONS).date())
                .isEqualTo(SENT.plusDays(5));
        assertThat(Cadence.of(FollowupConstants.GROUP_AFTER_PI, "S7", SENT, null, SENT.plusDays(7), p, ACTIONS).date())
                .isEqualTo(SENT.plusDays(6));
    }

    @Test
    void beforeQuote_remindsOnStartDay() {
        Cadence.Reminder r = Cadence.of(FollowupConstants.GROUP_BEFORE_QUOTE, "S3", SENT, null, SENT.plusDays(2), P, Map.of());
        assertThat(r.date()).isEqualTo(SENT);
        assertThat(r.text()).isEqualTo("首次回复客户");
    }

    @Test
    void suggestNurture_after14DaysWithoutFeedback_orThreeNoReplies() {
        LocalDate d14 = SENT.plusDays(14);
        assertThat(Cadence.suggestNurture(FollowupConstants.GROUP_AFTER_QUOTE, SENT, false, 0, d14.minusDays(1), P)).isFalse();
        assertThat(Cadence.suggestNurture(FollowupConstants.GROUP_AFTER_QUOTE, SENT, false, 0, d14, P)).isTrue();
        assertThat(Cadence.suggestNurture(FollowupConstants.GROUP_AFTER_QUOTE, SENT, true, 0, d14, P)).isFalse();
        assertThat(Cadence.suggestNurture(FollowupConstants.GROUP_AFTER_QUOTE, SENT, true, 3, SENT.plusDays(2), P)).isTrue();
        assertThat(Cadence.suggestNurture(FollowupConstants.GROUP_AFTER_PI, SENT, false, 5, d14, P)).isFalse();
    }

    @Test
    void amountTier_boundaries() {
        assertThat(P.tierOf(null)).isZero();
        assertThat(P.tierOf(BigDecimal.ZERO)).isZero();
        assertThat(P.tierOf(new BigDecimal("-100"))).isZero();
        assertThat(P.tierOf(new BigDecimal("9999.99"))).isZero();
        assertThat(P.tierOf(new BigDecimal("10000"))).isEqualTo(1);
        assertThat(P.tierOf(new BigDecimal("50000"))).isEqualTo(2);
        assertThat(P.tierOf(new BigDecimal("200000"))).isEqualTo(3);
        assertThat(P.tierOf(new BigDecimal("999999999999.99"))).isEqualTo(3);
    }
}
