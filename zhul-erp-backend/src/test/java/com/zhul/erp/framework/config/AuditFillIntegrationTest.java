package com.zhul.erp.framework.config;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.masterdata.entity.SupplierDO;
import com.zhul.erp.modules.masterdata.repository.SupplierMapper;
import com.zhul.erp.modules.system.service.MenuService;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计字段自动填充：谁在什么时间改的，更新人、更新时间就是谁、什么时间。
 * 业务代码普遍"先查出完整实体、改几个字段再 updateById"，实体上已带着旧的更新人和更新时间，填充必须覆盖它们。
 */
class AuditFillIntegrationTest extends IntegrationTestBase {

    private static final String CODE = "AUDITFILL01";

    @Autowired
    private SupplierMapper supplierMapper;

    @Autowired
    private MenuService menuService;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId(0);
        cleanup();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from supplier where supplier_code = ?", CODE);
    }

    private static void loginAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(username, null, List.of()));
    }

    private long createAsA() {
        loginAs("audit_a");
        SupplierDO supplier = new SupplierDO();
        supplier.setTenantId(0);
        supplier.setSupplierCode(CODE);
        supplier.setName("审计填充测试");
        supplierMapper.insert(supplier);
        jdbc.update("update supplier set update_time = '2020-01-01 00:00:00' where id = ?", supplier.getId());
        return supplier.getId();
    }

    private Map<String, Object> audit(long id) {
        return jdbc.queryForMap("select create_by, update_by, update_time from supplier where id = ?", id);
    }

    @Test
    void updateWithLoadedEntityRecordsCurrentOperator() {
        long id = createAsA();

        loginAs("audit_b");
        SupplierDO loaded = supplierMapper.selectById(id);
        loaded.setShortName("改名");
        supplierMapper.updateById(loaded);

        Map<String, Object> row = audit(id);
        assertEquals("audit_a", row.get("create_by"), "创建人不变");
        assertEquals("audit_b", row.get("update_by"));
        assertTrue(((LocalDateTime) row.get("update_time")).isAfter(LocalDateTime.now().minusMinutes(1)));
    }

    @Test
    void wrapperUpdateWithEmptyEntityRecordsCurrentOperator() {
        long id = createAsA();

        loginAs("audit_b");
        supplierMapper.update(new SupplierDO(), new LambdaUpdateWrapper<SupplierDO>()
                .eq(SupplierDO::getId, id)
                .set(SupplierDO::getShortName, "改名"));

        Map<String, Object> row = audit(id);
        assertEquals("audit_b", row.get("update_by"));
        assertTrue(((LocalDateTime) row.get("update_time")).isAfter(LocalDateTime.now().minusMinutes(1)));
    }

    /** 业务里的条件更新（这里以菜单排序为例）也要记录操作人 */
    @Test
    void serviceWrapperUpdateRecordsCurrentOperator() {
        long id = jdbc.queryForObject("select min(id) from resource", Long.class);
        Map<String, Object> before = jdbc.queryForMap("select sort, update_by, update_time from resource where id = ?", id);
        try {
            loginAs("audit_b");
            menuService.updateSort((int) id, 999);
            Map<String, Object> row = jdbc.queryForMap("select update_by, update_time from resource where id = ?", id);
            assertEquals("audit_b", row.get("update_by"));
            assertTrue(((LocalDateTime) row.get("update_time")).isAfter(LocalDateTime.now().minusMinutes(1)));
        } finally {
            jdbc.update("update resource set sort = ?, update_by = ?, update_time = ? where id = ?",
                    before.get("sort"), before.get("update_by"), before.get("update_time"), id);
        }
    }
}
