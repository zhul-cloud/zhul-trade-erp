package com.zhul.erp.modules.quotation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.document.service.DocumentTemplateService;
import com.zhul.erp.modules.document.support.DocumentConverter;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.quotation.dto.PreviewRequest;
import com.zhul.erp.modules.quotation.dto.PreviewVO;
import com.zhul.erp.modules.quotation.dto.SaveQuotationRequest;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import com.zhul.erp.modules.quotation.service.impl.QuotationDocumentServiceImpl;
import com.zhul.erp.modules.quotation.support.QuotationRenderModels;
import com.zhul.erp.modules.quotation.support.QuotationStore;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** PDF / 图片转换服务不可用：预览给出提示，PDF 导出报错、Excel 不受影响 */
class QuotationDocumentServiceTest {

    private final QuotationStore store = mock(QuotationStore.class);
    private final DocumentTemplateService templates = mock(DocumentTemplateService.class);
    private final DocumentConverter converter = new DocumentConverter("/nonexistent/soffice", System.getProperty("java.io.tmpdir"));
    private final QuotationDocumentServiceImpl service = new QuotationDocumentServiceImpl(store, mock(CustomerMapper.class),
            mock(UserBasicMapper.class), templates, converter, mock(CurrentUserResolver.class), new ObjectMapper());

    private QuotationDO quotation() throws Exception {
        QuotationDO q = new QuotationDO();
        q.setId(1L);
        q.setQuotationNo("QT20261004001");
        q.setCurrencyCode("USD");
        q.setExchangeRate(new BigDecimal("7.15"));
        q.setItemAmount(new BigDecimal("410.26"));
        q.setFeeAmount(BigDecimal.ZERO);
        q.setTotalAmount(new BigDecimal("410.26"));
        when(store.visible(1L)).thenReturn(q);
        QuotationItemDO item = new QuotationItemDO();
        item.setModel("6ES7214-1AG40-0XB0");
        item.setQuantity(1);
        item.setItemCondition(0);
        item.setLeadTime(0);
        item.setUnitPrice(new BigDecimal("410.26"));
        item.setAmount(new BigDecimal("410.26"));
        when(store.items(1L)).thenReturn(List.of(item));
        when(store.fees(1L)).thenReturn(List.of());
        when(store.labels()).thenReturn(new QuotationRenderModels.Labels(Map.of(), Map.of(), Map.of(), Map.of()));
        byte[] tpl;
        try (var in = new ClassPathResource("document-template/quotation-v1.xlsx").getInputStream()) {
            tpl = in.readAllBytes();
        }
        when(templates.resolveDefault(any(Integer.class))).thenReturn(new DocumentTemplateService.Resolved(1L, 1, tpl, null));
        return q;
    }

    @Test
    void previewUnavailable() throws Exception {
        quotation();
        PreviewRequest req = new PreviewRequest();
        req.setQuotationId(1L);
        req.setContent(new SaveQuotationRequest());
        PreviewVO vo = service.preview(req);
        assertThat(vo.getUnavailable()).isTrue();
        assertThat(vo.getMessage()).isEqualTo("预览暂时不可用，不影响编辑与导出 Excel");
    }

    @Test
    void pdfUnavailableButExcelWorks() throws Exception {
        quotation();
        assertThatThrownBy(() -> service.export(1L, "pdf")).isInstanceOf(BizException.class)
                .hasMessage("PDF / 图片暂时无法生成，可以先下载 Excel");
        assertThatThrownBy(() -> service.export(1L, "jpg")).hasMessage("PDF / 图片暂时无法生成，可以先下载 Excel");
        assertThat(service.export(1L, "xlsx").content()).isNotEmpty();
        assertThat(service.export(1L, "xlsx").fileName()).isEqualTo("QT20261004001.xlsx");
    }
}
