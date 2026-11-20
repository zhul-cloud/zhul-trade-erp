package com.zhul.erp.modules.quotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.document.service.DocumentTemplateService;
import com.zhul.erp.modules.document.support.DocumentConverter;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import com.zhul.erp.modules.quotation.service.impl.QuotationDocumentServiceImpl;
import com.zhul.erp.modules.quotation.support.QuotationRenderModels;
import com.zhul.erp.modules.quotation.support.QuotationStore;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** spec quotation/quotation「文字报价」：报价货况、报价货期（默认全新原装、按采购货期顺延） */
class QuoteTextOptionsTest {

    private static final String TEXT = "{{#items}}\n${item.model} ${item.qty} ${quotation.currencySymbol}"
            + "${item.unitPriceShort} ${item.leadTimeEn} ${item.conditionEn}\n{{/items}}";
    /** 货期字典按顺序：1 现货 … 9 8周以上 */
    private static final List<Integer> LEADS = List.of(1, 2, 3, 4, 5, 6, 7, 8, 9);
    private static final Map<Integer, String> LEADS_EN = Map.of(1, "in stock", 2, "1-2 days", 3, "2-3 days", 4, "3-5 days",
            5, "5-7 days", 6, "1-2 weeks", 7, "2-4 weeks", 8, "4-8 weeks", 9, "over 8 weeks");
    /** 货况：1 全新原装、2 99新、3 二手 */
    private static final Map<Integer, String> CONDITIONS_EN = Map.of(1, "original new", 2, "99% new", 3, "used");

    private final QuotationStore store = mock(QuotationStore.class);
    private final DocumentTemplateService templates = mock(DocumentTemplateService.class);
    private final QuotationDocumentServiceImpl service = new QuotationDocumentServiceImpl(store, mock(CustomerMapper.class),
            mock(UserBasicMapper.class), templates,
            new DocumentConverter("/nonexistent/soffice", System.getProperty("java.io.tmpdir")),
            mock(CurrentUserResolver.class), new ObjectMapper(),
            mock(com.zhul.erp.modules.product.support.BrandResolver.class),
            mock(com.zhul.erp.modules.document.support.PlaceNames.class),
            new com.zhul.erp.modules.document.support.DocFileNames(mock(com.zhul.erp.modules.system.repository.SysConfigMapper.class)));
    private final List<QuotationItemDO> items = new ArrayList<>();

    @BeforeEach
    void setUp() {
        QuotationDO q = new QuotationDO();
        q.setId(1L);
        q.setQuotationNo("QT20261117001");
        q.setCurrencyCode("USD");
        when(store.view(1L, null)).thenReturn(new QuotationStore.View(q, 1, items, List.of()));
        when(store.labels()).thenReturn(new QuotationRenderModels.Labels(Map.of(), CONDITIONS_EN, Map.of(), LEADS_EN));
        when(store.enabledCodes(QuotationConstants.DICT_CONDITION)).thenReturn(List.of(1, 2, 3));
        when(store.enabledCodes(QuotationConstants.DICT_LEAD_TIME)).thenReturn(LEADS);
        when(templates.resolveDefault(any(Integer.class))).thenReturn(new DocumentTemplateService.Resolved(1L, 1, null, TEXT));
    }

    private QuotationItemDO line(String model, Long costQuoteId, int lead, int condition) {
        QuotationItemDO i = new QuotationItemDO();
        i.setModel(model);
        i.setQuantity(1);
        i.setCostQuoteId(costQuoteId);
        i.setLeadTime(lead);
        i.setItemCondition(condition);
        i.setUnitPrice(new BigDecimal("100.00"));
        i.setNoStock(0);
        items.add(i);
        return i;
    }

    private String[] text(Integer condition, String leadTime) {
        return service.text(1L, null, condition, leadTime).getText().split("\n");
    }

    @Test
    void defaultsToOriginalNewAndShiftsPurchaseLeadTime() {
        line("A", 11L, 1, 2);
        line("B", 12L, 6, 1);
        when(store.purchaseLeadTimes(anyList())).thenReturn(Map.of(11L, 4, 12L, 1));
        // A：采购 3-5天 → 5-7天，99新 → 全新原装；B：采购现货 → 1-2天（不看行上的 1-2周）
        assertThat(text(null, null)).containsExactly("A 1 $100 5-7 days original new", "B 1 $100 1-2 days original new");
    }

    @Test
    void fixedLeadTimeAppliesToAllInStockLines() {
        line("A", 11L, 1, 1);
        line("B", null, 4, 1);
        QuotationItemDO none = line("C", null, 2, 1);
        none.setNoStock(1);
        none.setUnitPrice(null);
        none.setNoStockReason(1);
        String[] out = text(3, "6");
        assertThat(out[0]).isEqualTo("A 1 $100 1-2 weeks used");
        assertThat(out[1]).isEqualTo("B 1 $100 1-2 weeks used");
        assertThat(out[2]).startsWith("C ").doesNotContain("1-2 weeks");
        verify(store, never()).purchaseLeadTimes(anyList());
    }

    @Test
    void lastLeadTimeStaysAndEmptyStaysEmpty() {
        line("A", 11L, 1, 1);
        line("B", 12L, 3, 1);
        when(store.purchaseLeadTimes(anyList())).thenReturn(Map.of(11L, 9, 12L, 0));
        String[] out = text(null, "shift");
        assertThat(out[0]).isEqualTo("A 1 $100 over 8 weeks original new");
        // 采购没填货期：保持为空
        assertThat(out[1]).isEqualTo("B 1 $100  original new");
    }

    @Test
    void purchaseLeadTimeAsIs() {
        line("A", 11L, 1, 2);
        line("B", 12L, 6, 1);
        line("C", null, 7, 1);
        line("D", 13L, 3, 1);
        when(store.purchaseLeadTimes(anyList())).thenReturn(Map.of(11L, 4, 12L, 9, 13L, 0));
        // A：采购 3-5天 原样；B：最后一档 8周以上；C：没有采购回价取行上 2-4周；D：采购没填货期保持为空
        assertThat(text(null, "purchase")).containsExactly("A 1 $100 3-5 days original new",
                "B 1 $100 over 8 weeks original new", "C 1 $100 2-4 weeks original new", "D 1 $100  original new");
    }

    @Test
    void withoutPurchaseQuoteUsesLineLeadTime() {
        line("A", null, 6, 1);
        // 采购回价已删除：退回行上的货期
        line("B", 99L, 2, 1);
        when(store.purchaseLeadTimes(anyList())).thenReturn(Map.of());
        assertThat(text(null, null)).containsExactly("A 1 $100 2-4 weeks original new", "B 1 $100 2-3 days original new");
    }

    @Test
    void doesNotChangeQuotationLines() {
        QuotationItemDO a = line("A", 11L, 1, 2);
        when(store.purchaseLeadTimes(anyList())).thenReturn(Map.of(11L, 4));
        text(null, null);
        assertThat(a.getLeadTime()).isEqualTo(1);
        assertThat(a.getItemCondition()).isEqualTo(2);
    }

    @Test
    void rejectsUnknownOptions() {
        line("A", null, 1, 1);
        assertThatThrownBy(() -> text(8, null)).isInstanceOf(BizException.class).hasMessage("报价货况不存在");
        assertThatThrownBy(() -> text(null, "42")).isInstanceOf(BizException.class).hasMessage("报价货期不存在");
        assertThatThrownBy(() -> text(null, "abc")).isInstanceOf(BizException.class).hasMessage("报价货期不存在");
    }
}
