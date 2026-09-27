package com.zhul.erp.modules.masterdata.service.impl;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.masterdata.dto.SupplierProductScopeRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierProductScopeVO;
import com.zhul.erp.modules.masterdata.entity.SupplierProductScopeDO;
import com.zhul.erp.modules.masterdata.repository.SupplierProductScopeMapper;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.support.BrandResolver;
import com.zhul.erp.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 对应 specs/master-data/supplier-product-scope/spec.md */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SupplierProductScopeSyncTest {

    @Mock private SupplierProductScopeMapper scopeMapper;
    @Mock private BrandResolver brandResolver;
    @Mock private ProductBrandMapper brandMapper;
    @Mock private ProductCategoryMapper categoryMapper;

    private SupplierProductScopeSync sync;

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestSupport.initTableInfo(SupplierProductScopeDO.class);
    }

    @BeforeEach
    void setUp() {
        sync = new SupplierProductScopeSync(scopeMapper, brandResolver, brandMapper, categoryMapper);
        when(brandResolver.resolve(anyString())).thenAnswer(inv -> switch (BrandResolver.key(inv.getArgument(0))) {
            case "siemens", "西门子" -> 1L;
            case "abb" -> 2L;
            default -> null;
        });
        when(brandMapper.selectById(1L)).thenReturn(brand(1L, "Siemens"));
        when(categoryMapper.selectBatchIds(any())).thenAnswer(inv -> {
            List<ProductCategoryDO> out = new ArrayList<>();
            for (Object id : (java.util.Collection<?>) inv.getArgument(0)) {
                long v = (Long) id;
                out.add(category(v, v >= 100 ? 1L : null, v == 101 ? "PLC" : v == 102 ? "HMI" : "Controllers"));
            }
            return out;
        });
    }

    private static ProductBrandDO brand(Long id, String name) {
        ProductBrandDO b = new ProductBrandDO();
        b.setId(id);
        b.setBrandName(name);
        return b;
    }

    private static ProductCategoryDO category(Long id, Long parentId, String zh) {
        ProductCategoryDO c = new ProductCategoryDO();
        c.setId(id);
        c.setParentId(parentId);
        c.setCategoryName(zh);
        c.setCategoryNameZh(parentId == null ? "" : zh);
        return c;
    }

    private static SupplierProductScopeRequest req(Long brandId, String name, Long... categoryIds) {
        SupplierProductScopeRequest r = new SupplierProductScopeRequest();
        r.setBrandId(brandId);
        r.setBrandName(name);
        r.setCategoryIds(List.of(categoryIds));
        return r;
    }

    @Test
    void normalize_resolvesAliasAndKeepsUnknownAsPending() {
        List<SupplierProductScopeSync.Scope> scopes = sync.normalize(List.of(
                req(null, "西门子", 101L, 102L), req(null, "ABB"), req(null, " Hengstler ")));

        assertThat(scopes).containsExactly(
                new SupplierProductScopeSync.Scope(1L, "", Set.of(101L, 102L)),
                new SupplierProductScopeSync.Scope(2L, "", Set.of()),
                new SupplierProductScopeSync.Scope(null, "Hengstler", Set.of()));
    }

    @Test
    void normalize_duplicateBrandViaAliasIsRejected() {
        BizException e = assertThrows(BizException.class,
                () -> sync.normalize(List.of(req(1L, null), req(null, "西门子"))));
        assertThat(e.getMessage()).isEqualTo("主营品牌重复：西门子");
    }

    @Test
    void normalize_onlySubCategoriesAllowed() {
        BizException e = assertThrows(BizException.class,
                () -> sync.normalize(List.of(req(1L, null, 5L))));
        assertThat(e.getMessage()).isEqualTo("主营品类只能选择细分品类");
    }

    @Test
    void normalize_tooManyBrandsRejected() {
        List<SupplierProductScopeRequest> many = new ArrayList<>();
        for (int i = 0; i < 51; i++) {
            many.add(req(null, "Brand" + i));
        }
        assertThrows(BizException.class, () -> sync.normalize(many));
    }

    @Test
    void replace_softDeletesOldAndInsertsOneRowPerCategoryOrAllCategories() {
        sync.replace(1, 7L, List.of(
                new SupplierProductScopeSync.Scope(1L, "", Set.of(101L, 102L)),
                new SupplierProductScopeSync.Scope(null, "Hengstler", Set.of())));

        verify(scopeMapper).update(any(), any());
        ArgumentCaptor<SupplierProductScopeDO> captor = ArgumentCaptor.forClass(SupplierProductScopeDO.class);
        verify(scopeMapper, times(3)).insert(captor.capture());
        List<SupplierProductScopeDO> rows = captor.getAllValues();
        assertThat(rows).extracting(SupplierProductScopeDO::getCategoryId).containsExactlyInAnyOrder(101L, 102L, null);
        SupplierProductScopeDO pending = rows.stream().filter(r -> r.getBrandId() == null).findFirst().orElseThrow();
        assertThat(pending.getPendingBrandName()).isEqualTo("Hengstler");
        assertThat(pending.getPendingKey()).isEqualTo("hengstler");
    }

    @Test
    void fromLegacy_splitsMixedSeparatorsAndDedupes() {
        List<SupplierProductScopeSync.Scope> scopes = sync.fromLegacy("Siemens，abb、Hengstler/西门子; ;");

        assertThat(scopes).containsExactly(
                new SupplierProductScopeSync.Scope(1L, "", Set.of()),
                new SupplierProductScopeSync.Scope(2L, "", Set.of()),
                new SupplierProductScopeSync.Scope(null, "Hengstler", Set.of()));
    }

    @Test
    void load_groupsByBrandWithCategoryNames() {
        SupplierProductScopeDO a = new SupplierProductScopeDO();
        a.setSupplierId(7L); a.setBrandId(1L); a.setCategoryId(101L);
        SupplierProductScopeDO b = new SupplierProductScopeDO();
        b.setSupplierId(7L); b.setBrandId(1L); b.setCategoryId(102L);
        SupplierProductScopeDO c = new SupplierProductScopeDO();
        c.setSupplierId(7L); c.setPendingBrandName("Hengstler"); c.setPendingKey("hengstler");
        when(scopeMapper.selectList(any())).thenReturn(List.of(a, b, c));
        when(brandMapper.selectBatchIds(any())).thenReturn(List.of(brand(1L, "Siemens")));

        Map<Long, List<SupplierProductScopeVO>> loaded = sync.load(List.of(7L));

        List<SupplierProductScopeVO> scopes = loaded.get(7L);
        assertThat(scopes).hasSize(2);
        assertThat(scopes.get(0).getBrandName()).isEqualTo("Siemens");
        assertThat(scopes.get(0).getCategories()).extracting(SupplierProductScopeVO.CategoryRef::getName).containsExactly("PLC", "HMI");
        assertThat(scopes.get(1).isPending()).isTrue();
        assertThat(SupplierProductScopeSync.toText(scopes)).isEqualTo("Siemens(PLC/HMI); Hengstler[待确认](全部品类)");
        assertThat(SupplierProductScopeSync.toText(Collections.emptyList())).isEmpty();
    }
}
