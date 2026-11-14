package com.zhul.erp.modules.supply.support;

import com.zhul.erp.modules.supply.constants.SupplyConstants;
import com.zhul.erp.modules.supply.entity.ScExpansionScopeDO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** 供应商开发的范围规则（tasks 3.1、3.2）：要求家数三档、热度取较高一档、缺口最小为 0、证据归属顺序、主营产品覆盖 */
class DevPoolRulesTest {

    @Test
    void requiredSuppliers_threeTiers() {
        assertThat(SupplyConstants.requiredSuppliers(SupplyConstants.HEAT_HIGH)).isEqualTo(3);
        assertThat(SupplyConstants.requiredSuppliers(SupplyConstants.HEAT_MID)).isEqualTo(2);
        assertThat(SupplyConstants.requiredSuppliers(SupplyConstants.HEAT_LOW)).isEqualTo(1);
    }

    @Test
    void heat_takesHigherOfDemandAndExpansion() {
        DevPool.Range r = new DevPool.Range(1, 10);
        r.heatFrom(SupplyConstants.PRIORITY_LOW);
        r.heatFrom(SupplyConstants.PRIORITY_HIGH);
        r.heatFrom(SupplyConstants.PRIORITY_LOW);
        assertThat(r.heat()).as("一个高优先级的需求对象就是高热度").isEqualTo(SupplyConstants.HEAT_HIGH);
        assertThat(r.source()).isEqualTo("demand");

        DevPool.Range m = new DevPool.Range(1, 11);
        m.heatFrom(SupplyConstants.PRIORITY_MID);
        m.setExpansion(expansion(SupplyConstants.HEAT_HIGH));
        assertThat(m.heat()).as("主动拓展更高时取主动拓展").isEqualTo(SupplyConstants.HEAT_HIGH);
        assertThat(m.source()).isEqualTo("both");

        DevPool.Range e = new DevPool.Range(1, 12);
        e.setExpansion(expansion(SupplyConstants.HEAT_LOW));
        assertThat(e.heat()).isEqualTo(SupplyConstants.HEAT_LOW);
        assertThat(e.source()).isEqualTo("expansion");
    }

    @Test
    void gap_neverBelowZero() {
        DevPool.Range r = new DevPool.Range(1, 10);
        r.heatFrom(SupplyConstants.PRIORITY_MID);
        assertThat(r.gap()).isEqualTo(2);
        r.qualified().put(100L, new DevPool.Evidence(SupplyConstants.EVIDENCE_QUOTE, 1, LocalDate.now()));
        assertThat(r.gap()).isEqualTo(1);
        r.qualified().put(101L, new DevPool.Evidence(SupplyConstants.EVIDENCE_PO, 2, LocalDate.now()));
        r.qualified().put(102L, new DevPool.Evidence(SupplyConstants.EVIDENCE_PO, 3, LocalDate.now()));
        assertThat(r.gap()).as("合格多于要求时缺口为 0").isZero();
    }

    @Test
    void attribute_demandFirst_thenProduct_thenUnclassified() {
        Map<String, List<long[]>> demands = Map.of("M1", List.of(new long[]{1, 10}));
        Map<String, List<long[]>> products = Map.of("M1", List.of(new long[]{1, 20}),
                "M2", List.of(new long[]{1, 20}), "M3", List.of(new long[]{1, 5}));
        Set<Long> level2 = Set.of(10L, 20L);
        assertThat(DevPool.attribute("M1", 1L, demands, products, level2)).as("需求对象优先").containsExactly(1, 10);
        assertThat(DevPool.attribute("M2", 1L, demands, products, level2)).as("其次商品主数据").containsExactly(1, 20);
        assertThat(DevPool.attribute("M3", 1L, demands, products, level2)).as("商品是一级品类时算未分类").containsExactly(1, 0);
        assertThat(DevPool.attribute("M9", 1L, demands, products, level2)).as("都没有时品牌 · 未分类").containsExactly(1, 0);
        assertThat(DevPool.attribute("M9", null, demands, products, level2)).as("取不到品牌丢弃").isNull();
        assertThat(DevPool.attribute("M1", 2L, demands, products, level2)).as("品牌对不上时不归到需求对象").containsExactly(2, 0);
    }

    @Test
    void scopeCovers_allCategoriesOrParent() {
        Map<Long, Long> parents = Map.of(10L, 1L, 11L, 1L);
        assertThat(DevPool.scopeCovers(0, 10, parents)).as("主营产品不填品类为该品牌全部品类").isTrue();
        assertThat(DevPool.scopeCovers(10, 10, parents)).isTrue();
        assertThat(DevPool.scopeCovers(1, 10, parents)).as("登记的是一级品类").isTrue();
        assertThat(DevPool.scopeCovers(11, 10, parents)).isFalse();
        assertThat(DevPool.scopeCovers(11, 0, parents)).as("未分类范围只看品牌").isTrue();
    }

    private static ScExpansionScopeDO expansion(int heat) {
        ScExpansionScopeDO e = new ScExpansionScopeDO();
        e.setHeat(heat);
        return e;
    }
}
