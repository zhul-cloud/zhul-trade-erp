package com.zhul.erp.modules.finance;

import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.finance.service.VoucherService;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.service.InventoryPosting;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Doc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Line;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Loc;
import com.zhul.erp.modules.inventory.service.InventoryWarehouses;
import com.zhul.erp.modules.inventory.service.InventoryWarehouses.Price;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** 生成凭证失败时结账整体回滚（openspec inventory-vouchers task 3.3） */
class VoucherCloseRollbackContractTest extends SalesContractSupport {

    @Autowired private InventoryPosting posting;
    @Autowired private InventoryWarehouses warehouses;
    @Autowired private TransactionTemplate tx;
    @SpyBean private VoucherService vouchers;

    @Test
    void 生成凭证失败_结账回滚() throws Exception {
        YearMonth m1 = YearMonth.now().minusMonths(2);
        long product = manualProduct("6ES7 315-2AH14-0AB0");
        TenantContext.runAs(0, () -> tx.executeWithoutResult(s -> {
            InvBatchDO b = warehouses.newBatch("GR-RB-1", product, "", InvConstants.BATCH_PURCHASE, 1L, 1L, null, null,
                    m1.atDay(5), Price.cny(new BigDecimal("710")));
            Loc loc = new Loc(warehouses.own().getId(), product, b.getId(), InvConstants.GOOD);
            posting.post(List.of(Line.of(loc, InvConstants.OP_IN, 2, null,
                    Doc.of(InvConstants.BIZ_PURCHASE_IN, "GR", 1L, "GR-RB").date(m1.atDay(5)))));
        }));
        doThrow(new IllegalStateException("业务凭证借贷不平")).when(vouchers).generate(any(), anyLong());

        assertEquals(500, call(post("/api/v1/finance/periods/" + m1 + "/close"), admin).path("code").asInt());
        assertEquals(0, jdbc.queryForObject("select count(*) from inv_period where tenant_id = 0 and period_month = ? and status = ?",
                Integer.class, m1.toString(), InvConstants.PERIOD_CLOSED), "月份没结上");
        assertEquals(0, jdbc.queryForObject("select count(*) from inv_period_ledger where tenant_id = 0 and deleted_at is null",
                Integer.class), "快照随结账回滚");
        assertEquals(0, jdbc.queryForObject("select count(*) from fin_voucher_month where tenant_id = 0", Integer.class));
    }
}
