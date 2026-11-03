package com.zhul.erp.modules.inquiry.support;

import com.zhul.erp.modules.inquiry.customerinquiry.dto.AiParseGroupDTO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AiParseItemDTO;
import com.zhul.erp.modules.inquiry.customerinquiry.dto.AiParseOutputDTO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** 询盘解析：相同型号合并数量 */
class ParsedItemMergerTest {

    private static AiParseItemDTO item(String model, Integer qty) {
        AiParseItemDTO i = new AiParseItemDTO();
        i.setOriginalModel(model);
        i.setQuantity(qty);
        return i;
    }

    private static AiParseGroupDTO group(String brand, AiParseItemDTO... items) {
        AiParseGroupDTO g = new AiParseGroupDTO();
        g.setBrand(brand);
        g.setItems(new ArrayList<>(List.of(items)));
        return g;
    }

    private static AiParseOutputDTO output(AiParseGroupDTO... groups) {
        AiParseOutputDTO o = new AiParseOutputDTO();
        o.setGroups(new ArrayList<>(List.of(groups)));
        return o;
    }

    @Test
    void 同品牌同型号合并数量_忽略大小写和分隔符() {
        AiParseItemDTO a = item("6ES7 214-1AG40-0XB0", 2);
        a.setRemark("急用");
        a.setNameplates(List.of(1));
        AiParseItemDTO b = item("6es72141ag400xb0", 3);
        b.setRemark("要原装");
        b.setNameplates(List.of(1, 2));
        b.setConfidence(3);
        AiParseOutputDTO o = ParsedItemMerger.merge(output(group("Siemens", a, item("6ES7 315-2AH14-0AB0", 1), b)));

        List<AiParseItemDTO> items = o.getGroups().get(0).getItems();
        assertThat(items).hasSize(2);
        assertThat(items.get(0).getQuantity()).isEqualTo(5);
        assertThat(items.get(0).getRemark()).isEqualTo("急用；要原装");
        assertThat(items.get(0).getNameplates()).containsExactly(1, 2);
        assertThat(items.get(0).getConfidence()).isEqualTo(3);
    }

    @Test
    void 跨分组同品牌也合并_空分组去掉() {
        AiParseOutputDTO o = ParsedItemMerger.merge(output(
                group("Siemens", item("A-1", 1)),
                group("SIEMENS", item("a1", null))));
        assertThat(o.getGroups()).hasSize(1);
        assertThat(o.getGroups().get(0).getItems().get(0).getQuantity()).isEqualTo(2);
    }

    @Test
    void 品牌不同_订货号不同_没有型号的都不合并() {
        AiParseItemDTO x = item("A-1", 1);
        x.setOrderCode("X1");
        AiParseItemDTO y = item("A-1", 1);
        y.setOrderCode("Y1");
        AiParseOutputDTO o = ParsedItemMerger.merge(output(
                group("Siemens", item("A-1", 1), x, y, item("", 1), item(null, 1)),
                group("ABB", item("A-1", 1))));
        assertThat(o.getGroups().get(0).getItems()).hasSize(5);
        assertThat(o.getGroups().get(1).getItems()).hasSize(1);
    }

    @Test
    void 空结果原样返回() {
        assertThat(ParsedItemMerger.merge(null)).isNull();
        assertThat(ParsedItemMerger.merge(new AiParseOutputDTO()).getGroups()).isEmpty();
    }
}
