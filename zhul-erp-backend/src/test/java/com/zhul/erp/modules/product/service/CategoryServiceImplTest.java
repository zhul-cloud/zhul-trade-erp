package com.zhul.erp.modules.product.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.product.constants.ProductConstants;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import com.zhul.erp.modules.product.dto.CategoryOptionVO;
import com.zhul.erp.modules.product.dto.SaveCategoryRequest;
import com.zhul.erp.modules.product.entity.ProductCategoryDO;
import com.zhul.erp.modules.product.repository.ProductCategoryMapper;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.product.service.impl.CategoryServiceImpl;
import com.zhul.erp.modules.product.support.OptionsCache;
import com.zhul.erp.modules.product.support.PlatformScopeGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private ProductCategoryMapper categoryMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private OptionsCache optionsCache;

    private CategoryServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0);
        service = new CategoryServiceImpl(categoryMapper, productMapper, new PlatformScopeGuard(), optionsCache);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static SaveCategoryRequest request(String code, String name) {
        SaveCategoryRequest req = new SaveCategoryRequest();
        req.setCategoryCode(code);
        req.setCategoryName(name);
        return req;
    }

    private static ProductCategoryDO category(Long id, String code, String name) {
        ProductCategoryDO c = new ProductCategoryDO();
        c.setId(id);
        c.setTenantId(0);
        c.setCategoryCode(code);
        c.setCategoryName(name);
        c.setSortOrder(0);
        c.setStatus(1);
        return c;
    }

    private static BizException assertBiz(org.junit.jupiter.api.function.Executable e, String errorCode) {
        BizException ex = assertThrows(BizException.class, e);
        assertEquals(errorCode, ex.getErrorCode());
        return ex;
    }

    @Test
    void createSavesEnabledCategoryForPlatform() {
        service.create(request("controllers", "PLC & Controllers"));

        ArgumentCaptor<ProductCategoryDO> captor = ArgumentCaptor.forClass(ProductCategoryDO.class);
        verify(categoryMapper).insert(captor.capture());
        assertEquals("controllers", captor.getValue().getCategoryCode());
        assertEquals("PLC & Controllers", captor.getValue().getCategoryName());
        assertEquals(0, captor.getValue().getTenantId());
        assertEquals(1, captor.getValue().getStatus());
        verify(optionsCache).evictAfterCommit(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS);
    }

    @Test
    void descriptionIsSavedTrimmed() {
        SaveCategoryRequest req = request("controllers", "PLC & Controllers");
        req.setDescription("  可编程逻辑控制器及配套模块  ");

        service.create(req);

        ArgumentCaptor<ProductCategoryDO> captor = ArgumentCaptor.forClass(ProductCategoryDO.class);
        verify(categoryMapper).insert(captor.capture());
        assertEquals("可编程逻辑控制器及配套模块", captor.getValue().getDescription());
    }

    @Test
    void missingDescriptionIsSavedAsEmpty() {
        service.create(request("controllers", "PLC & Controllers"));

        ArgumentCaptor<ProductCategoryDO> captor = ArgumentCaptor.forClass(ProductCategoryDO.class);
        verify(categoryMapper).insert(captor.capture());
        assertEquals("", captor.getValue().getDescription());
    }

    @Test
    void descriptionLengthBoundaryIs500() {
        SaveCategoryRequest ok = request("a", "N");
        ok.setDescription("字".repeat(500));
        service.create(ok);

        SaveCategoryRequest tooLong = request("b", "N");
        tooLong.setDescription("字".repeat(501));
        assertBiz(() -> service.create(tooLong), ProductErrorCodes.PARAM_INVALID);
        verify(categoryMapper, org.mockito.Mockito.times(1)).insert(any(ProductCategoryDO.class));
    }

    @Test
    void descriptionOfCategoryWithProductsCanBeChangedWithoutChangingCode() {
        ProductCategoryDO current = category(3L, "drives", "Drives");
        when(categoryMapper.selectOne(any())).thenReturn(current, current);
        when(productMapper.selectCount(any())).thenReturn(4L);
        SaveCategoryRequest req = request("drives", "Drives");
        req.setDescription("变频器与软启动器，用于电机调速和启停");

        service.update(3L, req);

        ArgumentCaptor<ProductCategoryDO> captor = ArgumentCaptor.forClass(ProductCategoryDO.class);
        verify(categoryMapper).updateById(captor.capture());
        assertEquals("drives", captor.getValue().getCategoryCode());
        assertEquals("变频器与软启动器，用于电机调速和启停", captor.getValue().getDescription());
    }

    @Test
    void optionsCarryDescription() {
        ProductCategoryDO servo = category(2L, "servo", "Servo Systems");
        servo.setDescription("伺服驱动器与电机");
        when(optionsCache.get(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS, CategoryOptionVO.class)).thenReturn(null);
        when(categoryMapper.selectList(any())).thenReturn(List.of(servo));

        assertEquals("伺服驱动器与电机", service.options(null).get(0).getDescription());
    }

    @Test
    void duplicateCodeIsRejected() {
        when(categoryMapper.selectOne(any())).thenReturn(category(2L, "servo", "Servo"));

        assertBiz(() -> service.create(request("servo", "Servo 2")), ProductErrorCodes.CATEGORY_DUPLICATE);
        verify(categoryMapper, never()).insert(any(ProductCategoryDO.class));
    }

    @Test
    void invalidCodeFormatsAreRejected() {
        for (String bad : new String[]{"PLC Controllers", "Servo", "1abc", "_abc", "a-b", "a b", "a".repeat(33)}) {
            assertBiz(() -> service.create(request(bad, "N")), ProductErrorCodes.PARAM_INVALID);
        }
        verify(categoryMapper, never()).insert(any(ProductCategoryDO.class));
    }

    @Test
    void validCodeBoundariesAreAccepted() {
        service.create(request("a", "N"));
        service.create(request("a".repeat(32), "N"));
        service.create(request("servo_2", "N"));
        verify(categoryMapper, org.mockito.Mockito.times(3)).insert(any(ProductCategoryDO.class));
    }

    @Test
    void blankNameIsRejected() {
        assertBiz(() -> service.create(request("servo", " ")), ProductErrorCodes.PARAM_INVALID);
        assertBiz(() -> service.create(request("servo", "n".repeat(65))), ProductErrorCodes.PARAM_INVALID);
    }

    @Test
    void codeOfCategoryWithProductsCannotBeChanged() {
        when(categoryMapper.selectOne(any())).thenReturn(category(3L, "drives", "Drives"));
        when(productMapper.selectCount(any())).thenReturn(4L);

        BizException e = assertBiz(() -> service.update(3L, request("inverters", "Drives")),
                ProductErrorCodes.CATEGORY_CODE_IMMUTABLE);

        assertEquals(4L, ((Map<?, ?>) e.getDetail()).get("usageCount"));
        verify(categoryMapper, never()).updateById(any(ProductCategoryDO.class));
    }

    @Test
    void nameOfCategoryWithProductsCanBeChanged() {
        ProductCategoryDO current = category(3L, "drives", "Drives");
        when(categoryMapper.selectOne(any())).thenReturn(current, current);
        when(productMapper.selectCount(any())).thenReturn(4L);

        service.update(3L, request("drives", "Drives & Inverters"));

        ArgumentCaptor<ProductCategoryDO> captor = ArgumentCaptor.forClass(ProductCategoryDO.class);
        verify(categoryMapper).updateById(captor.capture());
        assertEquals("drives", captor.getValue().getCategoryCode());
        assertEquals("Drives & Inverters", captor.getValue().getCategoryName());
    }

    @Test
    void codeOfCategoryWithoutProductsCanBeChangedIfFree() {
        ProductCategoryDO current = category(3L, "drives", "Drives");
        when(categoryMapper.selectOne(any())).thenReturn(current, null, current);
        when(productMapper.selectCount(any())).thenReturn(0L);

        service.update(3L, request("inverters", "Drives"));

        verify(categoryMapper).updateById(any(ProductCategoryDO.class));
    }

    @Test
    void deleteCategoryWithProductsIsRejected() {
        when(categoryMapper.selectOne(any())).thenReturn(category(4L, "sensors", "Sensors"));
        when(productMapper.selectCount(any())).thenReturn(21L);

        BizException e = assertBiz(() -> service.delete(4L), ProductErrorCodes.CATEGORY_IN_USE);

        assertTrue(e.getMessage().contains("21"));
        verify(categoryMapper, never()).updateById(any(ProductCategoryDO.class));
    }

    @Test
    void deleteCategoryWithoutProductsSoftDeletes() {
        when(categoryMapper.selectOne(any())).thenReturn(category(4L, "spares", "Spares"));
        when(productMapper.selectCount(any())).thenReturn(0L);

        service.delete(4L);

        ArgumentCaptor<ProductCategoryDO> captor = ArgumentCaptor.forClass(ProductCategoryDO.class);
        verify(categoryMapper).updateById(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
    }

    @Test
    void disableCategoryUpdatesStatusAndEvictsCache() {
        when(categoryMapper.selectOne(any())).thenReturn(category(5L, "spares", "Spares"));

        service.updateStatus(5L, 0);

        ArgumentCaptor<ProductCategoryDO> captor = ArgumentCaptor.forClass(ProductCategoryDO.class);
        verify(categoryMapper).updateById(captor.capture());
        assertEquals(0, captor.getValue().getStatus());
        verify(optionsCache).evictAfterCommit(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS);
    }

    @Test
    void tenantAccountCannotWriteButCanRead() {
        TenantContext.setTenantId(1001);
        assertBiz(() -> service.create(request("servo", "Servo")), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.update(1L, request("servo", "Servo")), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.updateStatus(1L, 0), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        assertBiz(() -> service.delete(1L), ProductErrorCodes.PLATFORM_ADMIN_REQUIRED);
        verifyNoInteractions(categoryMapper, productMapper);

        when(optionsCache.get(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS, CategoryOptionVO.class)).thenReturn(List.of());
        assertEquals(0, service.options(null).size());
    }

    // ---------------------------------------------------------------- 两级品类（add-supplier-brand-category）

    private static ProductCategoryDO sub(Long id, String code, Long parentId) {
        ProductCategoryDO c = category(id, code, code);
        c.setParentId(parentId);
        c.setCategoryNameZh("伺服驱动器");
        return c;
    }

    private void stubActive(ProductCategoryDO c) {
        when(categoryMapper.selectOne(any())).thenReturn(c);
    }

    @Test
    void createSubCategoryUnderTopLevel() {
        stubActive(category(1L, "servo", "Servo Systems"));
        // 父品类查询之后是编码查重：第二次返回 null
        when(categoryMapper.selectOne(any())).thenReturn(category(1L, "servo", "Servo Systems"), (ProductCategoryDO) null);
        SaveCategoryRequest req = request("servo_drive", "Servo Drives");
        req.setParentId(1L);
        req.setCategoryNameZh("伺服驱动器");

        service.create(req);

        ArgumentCaptor<ProductCategoryDO> captor = ArgumentCaptor.forClass(ProductCategoryDO.class);
        verify(categoryMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getParentId());
        assertEquals("伺服驱动器", captor.getValue().getCategoryNameZh());
    }

    @Test
    void cannotCreateThirdLevel() {
        stubActive(sub(2L, "servo_drive", 1L));
        SaveCategoryRequest req = request("servo_drive_ac", "AC Servo Drives");
        req.setParentId(2L);
        req.setCategoryNameZh("交流伺服");

        assertBiz(() -> service.create(req), ProductErrorCodes.CATEGORY_LEVEL_INVALID);
        verify(categoryMapper, never()).insert(any(ProductCategoryDO.class));
    }

    @Test
    void subCategoryRequiresChineseName() {
        stubActive(category(1L, "servo", "Servo Systems"));
        SaveCategoryRequest req = request("servo_drive", "Servo Drives");
        req.setParentId(1L);

        BizException e = assertThrows(BizException.class, () -> service.create(req));
        assertTrue(e.getMessage().contains("中文名称"));
    }

    @Test
    void deleteTopLevelWithChildrenIsRejected() {
        stubActive(category(1L, "servo", "Servo Systems"));
        when(categoryMapper.selectCount(any())).thenReturn(2L);

        assertBiz(() -> service.delete(1L), ProductErrorCodes.CATEGORY_HAS_CHILDREN);
    }

    @Test
    void deleteSubCategoryUsedBySupplierIsRejected() {
        stubActive(sub(2L, "plc_cpu", 1L));
        when(categoryMapper.countSupplierScopes(2L)).thenReturn(3L);

        BizException e = assertBiz(() -> service.delete(2L), ProductErrorCodes.CATEGORY_IN_USE);
        assertEquals("该品类已被供应商主营产品使用，可改为停用", e.getMessage());
    }

    @Test
    void optionsFilterByLevel() {
        CategoryOptionVO top = new CategoryOptionVO();
        top.setId(1L);
        CategoryOptionVO child = new CategoryOptionVO();
        child.setId(2L);
        child.setParentId(1L);
        when(optionsCache.get(ProductConstants.CACHE_KEY_CATEGORY_OPTIONS, CategoryOptionVO.class))
                .thenReturn(List.of(top, child));

        assertEquals(List.of(top), service.options(null));
        assertEquals(List.of(child), service.options(2));
        assertEquals(2, service.options(0).size());
    }
}
