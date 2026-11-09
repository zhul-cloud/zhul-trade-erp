package com.zhul.erp.modules.crm.followup;

import com.zhul.erp.modules.crm.followup.constants.FollowupConstants;
import com.zhul.erp.modules.crm.followup.entity.FollowupItemDO;
import com.zhul.erp.modules.crm.followup.support.NextDateSuggest;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 下一步日期推荐（openspec change followup-quick-actions，design D2）：报价后节点、PI 后、主动开发序列、客户经营、参考停留天数。
 */
class NextDateSuggestTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
    private static final List<Integer> QUOTE_DAYS = List.of(3, 7, 14);
    private static final List<Integer> SEQUENCE = List.of(1, 4, 9, 16);

    private static FollowupItemDO item(int board, int group, String stage) {
        FollowupItemDO i = new FollowupItemDO();
        i.setBoard(board);
        i.setStageGroup(group);
        i.setStageCode(stage);
        return i;
    }

    private static NextDateSuggest.Suggestion of(FollowupItemDO i, Integer refDays, Integer accountDays) {
        return NextDateSuggest.of(i, TODAY, QUOTE_DAYS, 5, SEQUENCE, refDays, accountDays);
    }

    @Test
    void afterQuote_nextNodeAfterToday() {
        FollowupItemDO i = item(FollowupConstants.BOARD_SALES, FollowupConstants.GROUP_AFTER_QUOTE, "S4");
        i.setSentAt(TODAY.minusDays(3).atTime(10, 0));
        NextDateSuggest.Suggestion s = of(i, 7, null);
        assertEquals(TODAY.plusDays(4), s.date(), "第 3 天是今天，取第 7 天");
        assertEquals("报价后第 7 天", s.reason());
    }

    @Test
    void afterQuote_allNodesPassed_sevenDaysLater() {
        FollowupItemDO i = item(FollowupConstants.BOARD_SALES, FollowupConstants.GROUP_AFTER_QUOTE, "S5");
        i.setSentAt(TODAY.minusDays(30).atTime(10, 0));
        assertEquals(TODAY.plusDays(7), of(i, 7, null).date());
    }

    @Test
    void afterPi_expectedPayDateWins_elseInterval() {
        FollowupItemDO i = item(FollowupConstants.BOARD_SALES, FollowupConstants.GROUP_AFTER_PI, "S7");
        i.setExpectedPayDate(TODAY.plusDays(2));
        assertEquals(TODAY.plusDays(2), of(i, null, null).date());
        i.setExpectedPayDate(TODAY);
        NextDateSuggest.Suggestion s = of(i, null, null);
        assertEquals(TODAY.plusDays(5), s.date(), "付款日已到：按 PI 提醒间隔");
        assertEquals("PI 后每 5 天问一次付款", s.reason());
    }

    @Test
    void develop_d4UsesSecondNode_d5UsesNextNodeFromFirstTouch() {
        assertEquals(TODAY.plusDays(3), of(item(FollowupConstants.BOARD_DEVELOP, 0, "D4"), null, null).date());
        FollowupItemDO d5 = item(FollowupConstants.BOARD_DEVELOP, 0, "D5");
        d5.setFirstTouchDate(TODAY.minusDays(4));
        NextDateSuggest.Suggestion s = of(d5, null, null);
        assertEquals(TODAY.minusDays(4).plusDays(8), s.date(), "第 4 天已过，取第 9 天");
        assertEquals("触达序列第 9 天", s.reason());
    }

    @Test
    void account_byGradeInterval() {
        NextDateSuggest.Suggestion s = of(item(FollowupConstants.BOARD_ACCOUNT, 0, "G4"), 5, 30);
        assertEquals(TODAY.plusDays(30), s.date());
        assertEquals("按客户等级每 30 天联系一次", s.reason());
    }

    @Test
    void refDays_clampedTo14_fallbackWhenZeroOrMissing() {
        FollowupItemDO s2 = item(FollowupConstants.BOARD_SALES, 1, "S2");
        assertEquals(TODAY.plusDays(5), of(s2, 5, null).date());
        NextDateSuggest.Suggestion far = of(s2, 30, null);
        assertEquals(TODAY.plusDays(14), far.date());
        assertEquals("S2 参考停留 30 天，最多隔 14 天", far.reason());
        assertEquals(TODAY.plusDays(3), of(s2, 0, null).date());
        assertEquals(TODAY.plusDays(3), of(s2, null, null).date());
    }
}
