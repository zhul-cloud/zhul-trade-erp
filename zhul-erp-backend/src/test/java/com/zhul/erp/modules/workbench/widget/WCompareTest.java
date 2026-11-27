package com.zhul.erp.modules.workbench.widget;

import com.zhul.erp.modules.workbench.dto.WidgetData;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** 数字卡较上月：算不出变化时也下发上月的数，前端第三行改显示上月（ui-redesign D59） */
class WCompareTest {

    @Test
    void 上月有数时给出变化和上月的数() {
        WidgetData.Metric m = W.compare("毛利", new BigDecimal("112.40"), new BigDecimal("100.00"), "money", true);
        assertThat(m.getCompared()).isTrue();
        assertThat(m.getDelta()).isEqualByComparingTo("12.4");
        assertThat(m.getPrevious()).isEqualByComparingTo("100.00");
    }

    @Test
    void 上月为零时没有变化但有上月的数() {
        WidgetData.Metric m = W.compare("实收回款", BigDecimal.ZERO, BigDecimal.ZERO, "money", true);
        assertThat(m.getCompared()).isTrue();
        assertThat(m.getDelta()).isNull();
        assertThat(m.getPrevious()).isEqualByComparingTo("0");
    }

    @Test
    void 百分比指标上月没有数据时上月为空() {
        WidgetData.Metric m = W.compare("毛利率", new BigDecimal("26.65"), null, "percent", true);
        assertThat(m.getCompared()).isTrue();
        assertThat(m.getDelta()).isNull();
        assertThat(m.getPrevious()).isNull();
    }

    @Test
    void 不比较的指标不带上月() {
        WidgetData.Metric m = W.metric("待发货", 3, "count", null);
        assertThat(m.getCompared()).isNull();
        assertThat(m.getPrevious()).isNull();
    }
}
