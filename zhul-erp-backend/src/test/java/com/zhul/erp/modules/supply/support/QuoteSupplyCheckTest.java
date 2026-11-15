package com.zhul.erp.modules.supply.support;

import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.modules.quotation.support.QuoteSupplyHints.Line;
import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.entity.ScDemandDO;
import com.zhul.erp.modules.supply.repository.ScDemandMapper;
import com.zhul.erp.modules.supply.repository.SupplySourceMapper;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 报价行供应提示规则（tasks 3.2）：成本过期、覆盖状态三档、品牌识别不到、未进入矩阵、证据正好到期 */
class QuoteSupplyCheckTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 15);

    @Test
    void hints() {
        SupplySourceMapper source = mock(SupplySourceMapper.class);
        ScDemandMapper demands = mock(ScDemandMapper.class);
        BrandResolver brands = mock(BrandResolver.class);
        SupplyLookups lookups = mock(SupplyLookups.class);
        when(lookups.evidenceDays()).thenReturn(30);
        when(brands.resolve(anyString())).thenAnswer(inv -> "Siemens".equals(inv.getArgument(0)) ? 7L : null);
        when(source.quoteDates(any())).thenReturn(List.of(
                Map.of("id", 1L, "quotedAt", Timestamp.valueOf(TODAY.minusDays(45).atTime(10, 0))),
                Map.of("id", 2L, "quotedAt", Timestamp.valueOf(TODAY.minusDays(30).atTime(10, 0)))));
        when(demands.selectList(any())).thenReturn(List.of(
                demand("a1", SupplyConstants.DEMAND_IN_MATRIX, SupplyConstants.COVER_CONFIRM),
                demand("a2", SupplyConstants.DEMAND_IN_MATRIX, SupplyConstants.COVER_LEAD),
                demand("a3", SupplyConstants.DEMAND_IN_MATRIX, SupplyConstants.COVER_NO_CHANNEL),
                demand("a4", SupplyConstants.DEMAND_IN_MATRIX, SupplyConstants.COVER_READY),
                demand("a5", SupplyConstants.DEMAND_WATCHING, SupplyConstants.COVER_NONE)));
        QuoteSupplyCheck check = new QuoteSupplyCheck(source, demands, null, brands, lookups, null);

        Map<Long, List<String>> out = check.hints(List.of(
                new Line(10L, "Siemens", "A-1", 1L),
                new Line(11L, "Siemens", "a 2", 2L),
                new Line(12L, "Siemens", "A3", null),
                new Line(13L, "Siemens", "A4", null),
                new Line(14L, "Siemens", "A5", null),
                new Line(15L, "Unknown", "A1", null)), TODAY);

        assertThat(out.get(10L)).containsExactly("成本 45 天前", "货源需确认");
        assertThat(out.get(11L)).as("证据正好 30 天不提示成本").containsExactly("货源未验证");
        assertThat(out.get(12L)).containsExactly("无稳定渠道");
        assertThat(out).as("可直接报价、未进入矩阵、品牌识别不到的不提示").doesNotContainKeys(13L, 14L, 15L);
    }

    private static ScDemandDO demand(String key, int status, int coverage) {
        ScDemandDO d = new ScDemandDO();
        d.setBrandId(7L);
        d.setModelKey(key);
        d.setStatus(status);
        d.setCoverageStatus(coverage);
        return d;
    }
}
