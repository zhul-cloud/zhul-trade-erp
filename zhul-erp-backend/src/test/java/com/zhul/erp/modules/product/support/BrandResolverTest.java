package com.zhul.erp.modules.product.support;

import com.zhul.erp.modules.product.entity.ProductBrandAliasDO;
import com.zhul.erp.modules.product.entity.ProductBrandDO;
import com.zhul.erp.modules.product.repository.ProductBrandAliasMapper;
import com.zhul.erp.modules.product.repository.ProductBrandMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrandResolverTest {

    @Mock private ProductBrandMapper brandMapper;
    @Mock private ProductBrandAliasMapper aliasMapper;

    private BrandResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new BrandResolver(brandMapper, aliasMapper);
    }

    private static ProductBrandDO brand(long id, String name) {
        ProductBrandDO b = new ProductBrandDO();
        b.setId(id);
        b.setBrandName(name);
        return b;
    }

    @Test
    void resolvesByBrandName() {
        when(brandMapper.selectOne(any())).thenReturn(brand(1, "Siemens"));
        assertThat(resolver.resolve("  siemens ")).isEqualTo(1L);
        verify(aliasMapper, never()).selectOne(any());
    }

    @Test
    void resolvesByAliasIgnoringCaseAndSpaces() {
        when(brandMapper.selectOne(any())).thenReturn(null);
        ProductBrandAliasDO alias = new ProductBrandAliasDO();
        alias.setBrandId(1L);
        when(aliasMapper.selectOne(any())).thenReturn(alias);
        when(brandMapper.selectById(1L)).thenReturn(brand(1, "Siemens"));

        assertThat(resolver.resolve(" SIEMENS AG ")).isEqualTo(1L);
    }

    @Test
    void unknownOrBlankReturnsNull() {
        when(brandMapper.selectOne(any())).thenReturn(null);
        when(aliasMapper.selectOne(any())).thenReturn(null);
        assertThat(resolver.resolve("Hengstler")).isNull();
        assertThat(resolver.resolve("  ")).isNull();
    }

    @Test
    void keyIsTrimmedLowercase() {
        assertThat(BrandResolver.key("  SIEMENS AG ")).isEqualTo("siemens ag");
    }
}
