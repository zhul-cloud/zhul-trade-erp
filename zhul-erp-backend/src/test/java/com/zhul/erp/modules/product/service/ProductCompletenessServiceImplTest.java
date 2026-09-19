package com.zhul.erp.modules.product.service;

import com.zhul.erp.modules.product.dto.CompletenessVO;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ModuleHit;
import com.zhul.erp.modules.product.repository.ProductCompletenessMapper;
import com.zhul.erp.modules.product.service.impl.ProductCompletenessServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductCompletenessServiceImplTest {

    @Mock
    private ProductCompletenessMapper mapper;

    private static ProductDO product(long id) {
        ProductDO p = new ProductDO();
        p.setId(id);
        p.setBrandId(1L);
        p.setCategoryId(2L);
        p.setMpnRaw("A1");
        return p;
    }

    private static ModuleHit hit(long productId, String module) {
        ModuleHit h = new ModuleHit();
        h.setProductId(productId);
        h.setModuleKey(module);
        return h;
    }

    @Test
    void freshProductIsOneOfTen() {
        when(mapper.findModuleHits(any())).thenReturn(List.of());

        CompletenessVO vo = new ProductCompletenessServiceImpl(mapper).compute(List.of(product(1))).get(1L);

        assertEquals(1, vo.getDone());
        assertEquals(10, vo.getTotal());
        assertEquals(9, vo.getMissing().size());
        assertFalse(vo.getMissing().contains("basic"));
        assertEquals(10, vo.getModules().size());
    }

    @Test
    void productMissingOnlyMediaLogisticsCustomsIsSevenOfTen() {
        when(mapper.findModuleHits(any())).thenReturn(List.of(
                hit(1, "specifications"), hit(1, "referencePrice"), hit(1, "relationships"),
                hit(1, "documents"), hit(1, "applications"), hit(1, "faq")));

        CompletenessVO vo = new ProductCompletenessServiceImpl(mapper).compute(List.of(product(1))).get(1L);

        assertEquals(7, vo.getDone());
        assertEquals(List.of("media", "logistics", "customs"), vo.getMissing());
    }

    @Test
    void modulesAreReportedInFixedOrder() {
        when(mapper.findModuleHits(any())).thenReturn(List.of());

        CompletenessVO vo = new ProductCompletenessServiceImpl(mapper).compute(List.of(product(1))).get(1L);

        assertEquals(List.of("basic", "media", "specifications", "logistics", "customs", "referencePrice",
                "relationships", "documents", "applications", "faq"),
                vo.getModules().stream().map(CompletenessVO.Module::getKey).toList());
    }

    @Test
    void basicInfoNeedsBrandCategoryAndModel() {
        when(mapper.findModuleHits(any())).thenReturn(List.of());
        ProductDO noBrand = product(1);
        noBrand.setBrandId(0L);
        ProductDO blankMpn = product(2);
        blankMpn.setMpnRaw("  ");
        ProductDO noCategory = product(3);
        noCategory.setCategoryId(null);

        Map<Long, CompletenessVO> result = new ProductCompletenessServiceImpl(mapper)
                .compute(List.of(noBrand, blankMpn, noCategory));

        for (long id : new long[]{1, 2, 3}) {
            assertTrue(result.get(id).getMissing().contains("basic"), "商品 " + id);
            assertEquals(0, result.get(id).getDone());
        }
    }

    @Test
    void hitsAreAttributedToTheRightProduct() {
        when(mapper.findModuleHits(any())).thenReturn(List.of(hit(2, "media"), hit(2, "customs")));

        Map<Long, CompletenessVO> result = new ProductCompletenessServiceImpl(mapper)
                .compute(List.of(product(1), product(2)));

        assertEquals(1, result.get(1L).getDone());
        assertEquals(3, result.get(2L).getDone());
    }

    @Test
    void aWholePageIsComputedWithASingleQuery() {
        when(mapper.findModuleHits(any())).thenReturn(List.of());
        List<ProductDO> page = new ArrayList<>();
        IntStream.rangeClosed(1, 20).forEach(i -> page.add(product(i)));

        Map<Long, CompletenessVO> result = new ProductCompletenessServiceImpl(mapper).compute(page);

        assertEquals(20, result.size());
        verify(mapper, times(1)).findModuleHits(any());
    }

    @Test
    void emptyInputDoesNotQuery() {
        assertTrue(new ProductCompletenessServiceImpl(mapper).compute(List.of()).isEmpty());
        verifyNoInteractions(mapper);
    }

    @Test
    void completeProductIsTenOfTen() {
        when(mapper.findModuleHits(any())).thenReturn(List.of(hit(1, "media"), hit(1, "specifications"),
                hit(1, "logistics"), hit(1, "customs"), hit(1, "referencePrice"), hit(1, "relationships"),
                hit(1, "documents"), hit(1, "applications"), hit(1, "faq")));

        CompletenessVO vo = new ProductCompletenessServiceImpl(mapper).compute(List.of(product(1))).get(1L);

        assertEquals(10, vo.getDone());
        assertTrue(vo.getMissing().isEmpty());
    }
}
