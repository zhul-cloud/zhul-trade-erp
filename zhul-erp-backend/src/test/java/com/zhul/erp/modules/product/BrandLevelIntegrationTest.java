package com.zhul.erp.modules.product;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.dto.BrandOptionVO;
import com.zhul.erp.modules.product.dto.BrandQuery;
import com.zhul.erp.modules.product.dto.BrandVO;
import com.zhul.erp.modules.product.dto.SaveBrandRequest;
import com.zhul.erp.modules.product.service.BrandService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** spec product/brand「品牌等级」「品牌携带品牌简介」 */
class BrandLevelIntegrationTest extends IntegrationTestBase {

    @Autowired
    private BrandService brandService;
    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void clean() {
        for (String table : List.of("product", "product_series", "product_brand_alias", "product_brand")) {
            jdbc.update("delete from " + table);
        }
        redis.delete(ProductConstants.CACHE_KEY_BRAND_OPTIONS);
        loginAsAdmin("it_brand_level");
        TenantContext.setTenantId(0);
    }

    private BrandVO create(String name, Integer level) {
        SaveBrandRequest req = new SaveBrandRequest();
        req.setBrandName(name);
        req.setBrandLevel(level);
        return brandService.create(req);
    }

    @Test
    void levelSaveFilterCountsAndOptionOrder() {
        create("Festo", null);
        create("Siemens", 2);
        create("AirTAC", 1);
        BrandVO abb = create("ABB", 2);
        assertEquals(0, brandService.page(queryLevel(null)).getRecords().stream()
                .filter(b -> b.getBrandName().equals("Festo")).findFirst().orElseThrow().getBrandLevel(), "不传为普通");

        // 列表与下拉：核心 → 常做 → 普通，同级按名称
        assertEquals(List.of("ABB", "Siemens", "AirTAC", "Festo"),
                brandService.page(queryLevel(null)).getRecords().stream().map(BrandVO::getBrandName).toList());
        assertEquals(List.of("ABB", "Siemens", "AirTAC", "Festo"),
                brandService.options().stream().map(BrandOptionVO::getBrandName).toList());
        assertEquals(List.of("AirTAC"), brandService.page(queryLevel(1)).getRecords().stream().map(BrandVO::getBrandName).toList());
        BrandQuery byCountry = new BrandQuery();
        byCountry.setCountry("Japan");
        assertEquals(0, brandService.page(byCountry).getTotal(), "按原产地筛选");
        Map<String, Long> counts = brandService.levelCounts(new BrandQuery());
        assertEquals(4L, counts.get("all"));
        assertEquals(2L, counts.get("2"));
        assertEquals(1L, counts.get("1"));
        assertEquals(1L, counts.get("0"));

        // 修改：不传等级保持不变；中英文简介
        SaveBrandRequest req = new SaveBrandRequest();
        req.setBrandName("ABB");
        req.setDescriptionZh("ACS 变频器、AC500 PLC");
        req.setDescription("ACS drives and AC500 PLCs.");
        BrandVO updated = brandService.update(abb.getId(), req);
        assertEquals(2, updated.getBrandLevel());
        assertEquals("ACS 变频器、AC500 PLC", updated.getDescriptionZh());
        assertEquals("ACS drives and AC500 PLCs.", updated.getDescription());
        req.setBrandLevel(0);
        assertEquals(0, brandService.update(abb.getId(), req).getBrandLevel());
        assertEquals(List.of("Siemens", "AirTAC", "ABB", "Festo"),
                brandService.options().stream().map(BrandOptionVO::getBrandName).toList(), "修改后缓存清除，排序更新");

        req.setBrandLevel(3);
        assertThrows(BizException.class, () -> brandService.update(abb.getId(), req), "等级只能 0-2");
        req.setBrandLevel(null);
        req.setDescriptionZh("长".repeat(501));
        assertThrows(BizException.class, () -> brandService.update(abb.getId(), req), "中文简介超长");
    }

    private static BrandQuery queryLevel(Integer level) {
        BrandQuery q = new BrandQuery();
        q.setBrandLevel(level);
        return q;
    }
}
