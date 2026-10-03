package com.zhul.erp.modules.inquiry.sourcing.service.impl;

import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.sourcing.dto.ImportRowVO;
import com.zhul.erp.modules.inquiry.support.PriceKeys;
import com.zhul.erp.modules.inquiry.support.QuoteDictSnapshot;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 导入询价结果的行解析：用知识库里一份真实填过价的询价单（没有隐藏标识，按型号匹配） */
class SourcingExcelParseTest {

    private static InquiryItemDO item(long id, String model) {
        InquiryItemDO i = new InquiryItemDO();
        i.setId(id);
        i.setConfirmedModel(model);
        i.setModelKey(PriceKeys.model(model));
        return i;
    }

    /** 与迁移 V1.2.17 内置的货况、货期字典一致 */
    private static final QuoteDictSnapshot DICTS = new QuoteDictSnapshot(
            dict("全新原装", "99新", "翻新", "二手", "拆机件", "国产替代", "待确认"),
            dict("现货", "1-2天", "2-3天", "3-5天", "5-7天", "1-2周", "2-4周", "4-8周", "8周以上"),
            dict("在产", "停产", "待查"),
            new QuoteDictSnapshot.Dict(Map.of(13, "13%", 3, "3%", 1, "1%"), new LinkedHashSet<>(List.of(13, 3, 1))));

    private static QuoteDictSnapshot.Dict dict(String... names) {
        Map<Integer, String> labels = new LinkedHashMap<>();
        for (int i = 0; i < names.length; i++) {
            labels.put(i + 1, names[i]);
        }
        return new QuoteDictSnapshot.Dict(labels, new LinkedHashSet<>(labels.keySet()));
    }

    @Test
    void parsesRealQuotedFile() throws Exception {
        SourcingExcelServiceImpl service = new SourcingExcelServiceImpl(null, null, null, null, null, null, null, null, null, null);
        try (InputStream in = getClass().getResourceAsStream("/inquiry/dropsa-quoted.xlsx"); Workbook wb = new XSSFWorkbook(in)) {
            List<ImportRowVO> rows = service.parseRows(wb.getSheet("②询价单"), List.of(item(1, "20905"), item(2, "2043103")), DICTS);
            assertThat(rows).hasSize(1);
            ImportRowVO r = rows.get(0);
            assertThat(r.getItemId()).isEqualTo(1L);
            assertThat(r.getChannel()).isEqualTo(2);
            assertThat(r.getUnitPrice()).isEqualByComparingTo("750");
            assertThat(r.getItemCondition()).isEqualTo(1);
            assertThat(r.getRawLeadTime()).isEqualTo("3周");
            assertThat(r.getLeadTime()).as("3周落在 2-4周").isEqualTo(7);
            assertThat(r.getShopName()).isEqualTo("梁经理");
            assertThat(r.getProblems()).isEmpty();
            assertThat(r.getPosition()).startsWith("1688 区第");
        }
    }

    @Test
    void priceAndConditionText() {
        assertThat(SourcingExcelServiceImpl.parsePrice("¥1,350.5")).isEqualByComparingTo(new BigDecimal("1350.5"));
        assertThat(SourcingExcelServiceImpl.parsePrice("约3000")).isNull();
        assertThat(SourcingExcelServiceImpl.parsePrice("-5")).isNull();
        assertThat(DICTS.conditionOf("")).isZero();
        assertThat(DICTS.conditionOf("全新")).isEqualTo(1);
        assertThat(DICTS.conditionOf("99%新")).isEqualTo(2);
        assertThat(DICTS.conditionOf("国产高仿")).isEqualTo(6);
        assertThat(DICTS.conditionOf("七成新")).isNull();
    }

    @Test
    void leadTimeTextMapsToDictRange() {
        assertThat(DICTS.leadTimeOf("")).isZero();
        assertThat(DICTS.leadTimeOf("现货")).isEqualTo(1);
        assertThat(DICTS.leadTimeOf("有现货")).isEqualTo(1);
        assertThat(DICTS.leadTimeOf("2-3天")).as("名称精确匹配").isEqualTo(3);
        assertThat(DICTS.leadTimeOf("4天")).isEqualTo(4);
        assertThat(DICTS.leadTimeOf("10个工作日")).as("10 天落在 1-2周").isEqualTo(6);
        assertThat(DICTS.leadTimeOf("3-4周")).as("按上限 28 天").isEqualTo(7);
        assertThat(DICTS.leadTimeOf("3个月")).as("90 天落在 8周以上").isEqualTo(9);
        assertThat(DICTS.leadTimeOf("看情况")).isNull();
    }

    @Test
    void disabledDictItemsAreNotAccepted() {
        QuoteDictSnapshot.Dict lead = new QuoteDictSnapshot.Dict(Map.of(1, "现货", 2, "1-2天"), new LinkedHashSet<>(List.of(1)));
        QuoteDictSnapshot snapshot = new QuoteDictSnapshot(DICTS.conditions(), lead, DICTS.lifecycles(), DICTS.taxRates());
        assertThat(lead.acceptable(0)).as("0 为未填").isTrue();
        assertThat(lead.acceptable(2)).as("已停用").isFalse();
        assertThat(lead.label(2)).as("停用项仍可展示").isEqualTo("1-2天");
        assertThat(snapshot.leadTimeOf("1-2天")).isNull();
    }

    @Test
    void taxedPriceText() {
        assertThat(SourcingExcelServiceImpl.parseTaxedPrice("100").taxIncluded()).isFalse();
        SourcingExcelServiceImpl.TaxedPrice a = SourcingExcelServiceImpl.parseTaxedPrice("113含税");
        assertThat(a.taxIncluded()).isTrue();
        assertThat(a.rate()).as("没写税率按 13%").isEqualTo(13);
        assertThat(a.price()).isEqualByComparingTo("113");
        assertThat(SourcingExcelServiceImpl.parseTaxedPrice("含税113").price()).isEqualByComparingTo("113");
        SourcingExcelServiceImpl.TaxedPrice b = SourcingExcelServiceImpl.parseTaxedPrice("103 含税3%");
        assertThat(b.rate()).isEqualTo(3);
        assertThat(b.price()).isEqualByComparingTo("103");
        SourcingExcelServiceImpl.TaxedPrice c = SourcingExcelServiceImpl.parseTaxedPrice("¥101（含1%税）");
        assertThat(c.rate()).isEqualTo(1);
        assertThat(c.price()).isEqualByComparingTo("101");
    }

    @Test
    void excludeTaxRoundsHalfUp() {
        assertThat(MyTaskServiceImpl.excludeTax(new BigDecimal("113.00"), 13)).isEqualByComparingTo("100.00");
        assertThat(MyTaskServiceImpl.excludeTax(new BigDecimal("100.00"), 13)).as("88.4955… → 88.50").isEqualByComparingTo("88.50");
        assertThat(MyTaskServiceImpl.excludeTax(new BigDecimal("0.00"), 13)).isEqualByComparingTo("0.00");
        assertThat(MyTaskServiceImpl.excludeTax(new BigDecimal("99999999.99"), 3)).isEqualByComparingTo("97087378.63");
    }
}
