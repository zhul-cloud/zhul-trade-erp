package com.zhul.erp.modules.tenant.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.tenant.dto.PackageDeleteCheckVO;
import com.zhul.erp.modules.tenant.dto.SaveTenantPackageRequest;
import com.zhul.erp.modules.tenant.dto.TenantPackageQuery;
import com.zhul.erp.modules.tenant.dto.TenantPackageVO;
import com.zhul.erp.modules.tenant.entity.TenantPackageDO;
import com.zhul.erp.modules.tenant.repository.PackageTenantCount;
import com.zhul.erp.modules.tenant.repository.TenantMapper;
import com.zhul.erp.modules.tenant.repository.TenantPackageMapper;
import com.zhul.erp.modules.tenant.service.impl.TenantPackageServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TenantPackageServiceImplTest {

    @Mock
    private TenantPackageMapper packageMapper;
    @Mock
    private TenantMapper tenantMapper;

    private TenantPackageServiceImpl service;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0);
        service = new TenantPackageServiceImpl(packageMapper, tenantMapper, new ObjectMapper());
        // insert 时模拟自增主键回填；lenient 因为不是每个用例都会走到创建这一步
        lenient().doAnswer(invocation -> {
            TenantPackageDO arg = invocation.getArgument(0);
            arg.setId(5000);
            return 1;
        }).when(packageMapper).insert(any(TenantPackageDO.class));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static SaveTenantPackageRequest request() {
        SaveTenantPackageRequest req = new SaveTenantPackageRequest();
        req.setName("标准版");
        req.setDescription("面向初创贸易团队");
        req.setMenuIds(List.of(1, 2, 5));
        return req;
    }

    private static TenantPackageDO existingPackage() {
        TenantPackageDO pkg = new TenantPackageDO();
        pkg.setId(1);
        pkg.setName("标准版");
        pkg.setRemark("旧描述");
        pkg.setMenuIds("[1,2,5]");
        pkg.setStatus(1);
        return pkg;
    }

    // ---------- 权限边界：只有平台账号（tenantId=0）能访问 ----------

    @Test
    void nonPlatformTenantIsRejected() {
        TenantContext.setTenantId(1000);
        BizException e = assertThrows(BizException.class, () -> service.page(new TenantPackageQuery()));
        assertEquals("仅平台账号可访问套餐管理", e.getMessage());
    }

    @Test
    void nullTenantIsRejected() {
        TenantContext.clear();
        assertThrows(BizException.class, () -> service.page(new TenantPackageQuery()));
    }

    // ---------- 创建 ----------

    @Test
    void createSucceedsAndSerializesMenuIds() {
        when(packageMapper.selectCount(any())).thenReturn(0L);

        TenantPackageVO vo = service.create(request());

        ArgumentCaptor<TenantPackageDO> captor = ArgumentCaptor.forClass(TenantPackageDO.class);
        verify(packageMapper).insert(captor.capture());
        assertEquals("标准版", captor.getValue().getName());
        assertEquals("面向初创贸易团队", captor.getValue().getRemark());
        assertEquals("[1,2,5]", captor.getValue().getMenuIds());
        assertEquals(1, captor.getValue().getStatus());

        assertEquals(List.of(1, 2, 5), vo.getMenuIds());
        assertEquals(3, vo.getMenuCount());
        assertEquals(0, vo.getTenantCount());
    }

    @Test
    void createRejectsDuplicateName() {
        when(packageMapper.selectCount(any())).thenReturn(1L);
        BizException e = assertThrows(BizException.class, () -> service.create(request()));
        assertEquals("该套餐名称已存在", e.getMessage());
    }

    // ---------- 编辑 ----------

    @Test
    void updateNotFoundThrows() {
        when(packageMapper.selectById(99)).thenReturn(null);
        assertThrows(BizException.class, () -> service.update(99, request()));
    }

    @Test
    void updateSucceeds() {
        when(packageMapper.selectById(1)).thenReturn(existingPackage());
        when(packageMapper.selectCount(any())).thenReturn(0L);
        when(tenantMapper.selectCount(any())).thenReturn(2L);

        SaveTenantPackageRequest req = request();
        req.setName("专业版");
        req.setMenuIds(List.of(1, 2, 3, 5));
        TenantPackageVO vo = service.update(1, req);

        ArgumentCaptor<TenantPackageDO> captor = ArgumentCaptor.forClass(TenantPackageDO.class);
        verify(packageMapper).updateById(captor.capture());
        assertEquals("专业版", captor.getValue().getName());
        assertEquals("[1,2,3,5]", captor.getValue().getMenuIds());

        assertEquals(2, vo.getTenantCount());
    }

    // ---------- 启用/禁用 ----------

    @Test
    void updateStatusNotFoundThrows() {
        when(packageMapper.selectById(99)).thenReturn(null);
        assertThrows(BizException.class, () -> service.updateStatus(99, 0));
    }

    @Test
    void updateStatusSucceeds() {
        when(packageMapper.selectById(1)).thenReturn(existingPackage());
        service.updateStatus(1, 0);

        ArgumentCaptor<TenantPackageDO> captor = ArgumentCaptor.forClass(TenantPackageDO.class);
        verify(packageMapper).updateById(captor.capture());
        assertEquals(1, captor.getValue().getId());
        assertEquals(0, captor.getValue().getStatus());
    }

    // ---------- 删除前置校验 + 删除 ----------

    @Test
    void checkDeletableReturnsNotBlockedWhenNoTenants() {
        when(packageMapper.selectById(1)).thenReturn(existingPackage());
        when(tenantMapper.selectCount(any())).thenReturn(0L);

        PackageDeleteCheckVO vo = service.checkDeletable(1);
        assertFalse(vo.isBlocked());
        assertEquals(0, vo.getTenantCount());
    }

    @Test
    void checkDeletableReturnsBlockedWhenTenantsBound() {
        when(packageMapper.selectById(1)).thenReturn(existingPackage());
        when(tenantMapper.selectCount(any())).thenReturn(34L);

        PackageDeleteCheckVO vo = service.checkDeletable(1);
        assertTrue(vo.isBlocked());
        assertEquals(34, vo.getTenantCount());
    }

    @Test
    void deleteSucceedsWhenNoTenantsBound() {
        when(packageMapper.selectById(1)).thenReturn(existingPackage());
        when(tenantMapper.selectCount(any())).thenReturn(0L);

        service.delete(1);

        ArgumentCaptor<TenantPackageDO> captor = ArgumentCaptor.forClass(TenantPackageDO.class);
        verify(packageMapper).updateById(captor.capture());
        assertNotNull(captor.getValue().getDeletedAt());
        assertTrue(captor.getValue().getDeletedAt().isBefore(LocalDateTime.now().plusSeconds(1)));
    }

    @Test
    void deleteRejectedWhenTenantsBound() {
        when(packageMapper.selectById(1)).thenReturn(existingPackage());
        when(tenantMapper.selectCount(any())).thenReturn(5L);

        BizException e = assertThrows(BizException.class, () -> service.delete(1));
        assertEquals("该套餐正在被租户使用，无法删除", e.getMessage());
    }

    @Test
    void deleteNotFoundThrows() {
        when(packageMapper.selectById(99)).thenReturn(null);
        assertThrows(BizException.class, () -> service.delete(99));
    }

    // ---------- 列表 ----------

    @Test
    void pageMapsTenantCountsAndMenuCount() {
        TenantPackageDO pkg1 = existingPackage();
        TenantPackageDO pkg2 = new TenantPackageDO();
        pkg2.setId(2);
        pkg2.setName("旗舰版");
        pkg2.setMenuIds("[1,2,3,4,5,6]");
        pkg2.setStatus(1);

        Page<TenantPackageDO> mpPage = new Page<>(1, 20);
        mpPage.setRecords(List.of(pkg1, pkg2));
        mpPage.setTotal(2);
        when(packageMapper.selectPage(any(), any())).thenReturn(mpPage);

        PackageTenantCount c1 = new PackageTenantCount();
        c1.setId(1);
        c1.setCnt(34);
        when(tenantMapper.countByPackageIds(anyCollection())).thenReturn(List.of(c1));

        PageResult<TenantPackageVO> result = service.page(new TenantPackageQuery());

        assertEquals(2, result.getTotal());
        TenantPackageVO vo1 = result.getRecords().get(0);
        assertEquals(34, vo1.getTenantCount());
        assertEquals(3, vo1.getMenuCount());
        TenantPackageVO vo2 = result.getRecords().get(1);
        assertEquals(0, vo2.getTenantCount());
        assertEquals(6, vo2.getMenuCount());
    }
}
