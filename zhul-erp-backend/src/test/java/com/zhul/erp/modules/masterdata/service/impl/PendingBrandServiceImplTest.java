package com.zhul.erp.modules.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.masterdata.dto.PendingBrandVO;
import com.zhul.erp.modules.masterdata.entity.SupplierProductScopeDO;
import com.zhul.erp.modules.masterdata.repository.SupplierProductScopeMapper;
import com.zhul.erp.modules.product.dto.BrandVO;
import com.zhul.erp.modules.product.dto.SaveBrandRequest;
import com.zhul.erp.modules.product.service.BrandService;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import com.zhul.erp.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 对应 specs/product/brand/spec.md「确认待确认品牌」 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PendingBrandServiceImplTest {

    @Mock private SupplierProductScopeMapper scopeMapper;
    @Mock private BrandService brandService;

    private PendingBrandServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestSupport.initTableInfo(SupplierProductScopeDO.class);
    }

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0);
        service = new PendingBrandServiceImpl(scopeMapper, brandService, new PlatformScopeGuard());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static PendingBrandVO pending(String key, String name, int count) {
        PendingBrandVO p = new PendingBrandVO();
        p.setPendingKey(key);
        p.setName(name);
        p.setSupplierCount(count);
        return p;
    }

    private static SupplierProductScopeDO row(long id, Long categoryId) {
        SupplierProductScopeDO r = new SupplierProductScopeDO();
        r.setId(id);
        r.setBrandId(1L);
        r.setCategoryId(categoryId);
        return r;
    }

    @Test
    void linkAsAlias_addsAliasAndRelinksAllSuppliers() {
        when(scopeMapper.selectPendingBrands()).thenReturn(List.of(pending("西门子plc", "西门子PLC", 3)));
        when(scopeMapper.selectSupplierIdsByPendingKey("西门子plc")).thenReturn(List.of(11L, 12L, 13L));
        when(scopeMapper.selectList(any())).thenReturn(List.of(row(1, null)));

        service.linkAsAlias(" 西门子PLC ", 1L);

        verify(brandService).addAlias(1L, "西门子PLC");
        verify(scopeMapper).linkPendingToBrand(eq("西门子plc"), eq(1L), any());
    }

    @Test
    void relinkMergesDuplicateBrandIntoAllCategories() {
        when(scopeMapper.selectPendingBrands()).thenReturn(List.of(pending("西门子plc", "西门子PLC", 1)));
        when(scopeMapper.selectSupplierIdsByPendingKey("西门子plc")).thenReturn(List.of(11L));
        // 原有「Siemens：PLC」（行1）+ 关联后的「Siemens：全部品类」（行2）
        when(scopeMapper.selectList(any())).thenReturn(List.of(row(1, 10L), row(2, null)));

        service.linkAsAlias("西门子plc", 1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<SupplierProductScopeDO>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(scopeMapper).update(any(), captor.capture());
        captor.getValue().getSqlSegment(); // 参数在拼 SQL 片段时才写入
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains(1L).doesNotContain(2L);
    }

    @Test
    void relinkDedupesSameCategoriesWhenNoAllCategoriesRow() {
        when(scopeMapper.selectPendingBrands()).thenReturn(List.of(pending("x", "X", 1)));
        when(scopeMapper.selectSupplierIdsByPendingKey("x")).thenReturn(List.of(11L));
        when(scopeMapper.selectList(any())).thenReturn(List.of(row(1, 10L), row(2, 20L), row(3, 10L)));

        service.linkAsAlias("x", 1L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaUpdateWrapper<SupplierProductScopeDO>> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(scopeMapper).update(any(), captor.capture());
        captor.getValue().getSqlSegment(); // 参数在拼 SQL 片段时才写入
        assertThat(captor.getValue().getParamNameValuePairs().values()).contains(3L).doesNotContain(1L, 2L);
    }

    @Test
    void createBrandWithSameNameDoesNotAddAlias() {
        when(scopeMapper.selectPendingBrands()).thenReturn(List.of(pending("hengstler", "Hengstler", 2)));
        BrandVO created = new BrandVO();
        created.setId(9L);
        created.setBrandName("Hengstler");
        when(brandService.create(any())).thenReturn(created);

        assertThat(service.createBrand("hengstler", null)).isEqualTo(9L);

        ArgumentCaptor<SaveBrandRequest> req = ArgumentCaptor.forClass(SaveBrandRequest.class);
        verify(brandService).create(req.capture());
        assertThat(req.getValue().getBrandName()).isEqualTo("Hengstler");
        verify(brandService, never()).addAlias(any(), anyString());
        verify(scopeMapper).linkPendingToBrand(eq("hengstler"), eq(9L), any());
    }

    @Test
    void createBrandWithRenameKeepsOriginalAsAlias() {
        when(scopeMapper.selectPendingBrands()).thenReturn(List.of(pending("b&r automation", "B&R Automation", 1)));
        BrandVO created = new BrandVO();
        created.setId(9L);
        created.setBrandName("B&R");
        when(brandService.create(any())).thenReturn(created);
        SaveBrandRequest req = new SaveBrandRequest();
        req.setBrandName("B&R");

        service.createBrand("b&r automation", req);

        verify(brandService).addAlias(9L, "B&R Automation");
    }

    @Test
    void unknownPendingKeyIsRejected() {
        when(scopeMapper.selectPendingBrands()).thenReturn(List.of());
        assertThrows(BizException.class, () -> service.linkAsAlias("nope", 1L));
    }

    @Test
    void tenantAccountIsRejected() {
        TenantContext.setTenantId(5);
        assertThrows(BizException.class, () -> service.list());
        verify(scopeMapper, never()).selectPendingBrands();
    }
}
