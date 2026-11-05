package com.zhul.erp.modules.crm.followup;

import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.crm.followup.support.FollowupHooks;
import com.zhul.erp.modules.crm.followup.support.FollowupSync;
import com.zhul.erp.modules.crm.service.OpportunityService;
import com.zhul.erp.modules.purchase.entity.PurchaseOrderItemDO;
import com.zhul.erp.modules.purchase.repository.PurchaseOrderItemMapper;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.support.MybatisPlusTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 单据钩子（第一期 tasks 4.2、4.3；第二期 tasks 4.1–4.4）：自动赢单、自动推进、跟进同步出错不影响单据操作本身；
 * 订单、采购、仓库、出运、售后各入口都调用了钩子。
 */
class FollowupHooksTest {

    private final FollowupSync sync = mock(FollowupSync.class);
    private final OpportunityService opportunityService = mock(OpportunityService.class);
    private final SalesOrderMapper orderMapper = mock(SalesOrderMapper.class);
    private final PurchaseOrderItemMapper poItemMapper = mock(PurchaseOrderItemMapper.class);
    private final FollowupHooks hooks = new FollowupHooks(sync, opportunityService, orderMapper, poItemMapper);

    @BeforeAll
    static void tableInfo() {
        MybatisPlusTestSupport.initTableInfo(SalesOrderDO.class, PurchaseOrderItemDO.class);
    }

    private static ProformaInvoiceDO pi(String received) {
        ProformaInvoiceDO p = new ProformaInvoiceDO();
        p.setId(1L);
        p.setCustomerId(7L);
        p.setPiNo("FWPI1");
        p.setCurrencyCode("USD");
        p.setTotalAmount(new BigDecimal("500"));
        p.setReceivedAmount(new BigDecimal(received));
        return p;
    }

    @Test
    void autoWinThrows_receiptStillSucceeds() {
        when(opportunityService.autoWin(7L, "FWPI1")).thenThrow(new IllegalStateException("boom"));
        assertThatCode(() -> hooks.receiptConfirmed(pi("150"), new BigDecimal("150"))).doesNotThrowAnyException();
        verify(sync).event(eq(7L), anyInt(), eq(1L), anyString());
        verify(sync, never()).writeSystemRecord(anyLong(), anyInt(), anyLong(), eq("PI FWPI1 收款到账，商机自动赢单"));
    }

    @Test
    void nothingReceived_noWin() {
        hooks.receiptConfirmed(pi("0"), BigDecimal.ZERO);
        verify(opportunityService, never()).autoWin(anyLong(), anyString());
    }

    @Test
    void autoAdvanceThrows_piSendStillSucceeds() {
        when(opportunityService.autoAdvance(eq(7L), eq("S7"), eq(null), anyString())).thenThrow(new IllegalStateException("boom"));
        assertThatCode(() -> hooks.piSent(pi("0"), true)).doesNotThrowAnyException();
        verify(sync).event(eq(7L), anyInt(), eq(1L), anyString());
    }

    private static SalesOrderDO so(long id, long customerId) {
        SalesOrderDO o = new SalesOrderDO();
        o.setId(id);
        o.setCustomerId(customerId);
        return o;
    }

    @Test
    void orderChanged_resyncsEachCustomerOnce() {
        when(orderMapper.selectList(any())).thenReturn(List.of(so(1, 7), so(2, 7), so(3, 8)));
        hooks.ordersChanged(List.of(1L, 2L, 3L));
        verify(sync, times(1)).customerChanged(7L);
        verify(sync, times(1)).customerChanged(8L);
    }

    @Test
    void purchaseChanged_followsLinkedOrders() {
        PurchaseOrderItemDO i = new PurchaseOrderItemDO();
        i.setSoId(1L);
        when(poItemMapper.selectList(any())).thenReturn(List.of(i));
        when(orderMapper.selectList(any())).thenReturn(List.of(so(1, 7)));
        hooks.purchaseChanged(9L);
        verify(sync).customerChanged(7L);
    }

    @Test
    void lookupThrows_documentOperationStillSucceeds() {
        when(orderMapper.selectList(any())).thenThrow(new IllegalStateException("boom"));
        when(poItemMapper.selectList(any())).thenThrow(new IllegalStateException("boom"));
        assertThatCode(() -> hooks.orderChanged(1L)).doesNotThrowAnyException();
        assertThatCode(() -> hooks.purchaseChanged(9L)).doesNotThrowAnyException();
        verify(sync, never()).customerChanged(anyLong());
    }

    @Test
    void afterSalesClosed_writesCaseNote() {
        AfterSalesDO a = new AfterSalesDO();
        a.setCustomerId(7L);
        a.setSoId(1L);
        a.setAsNo("AS1");
        a.setResult("补发 2 台");
        a.setCaseNote("该型号运输需加泡沫护角");
        org.mockito.Mockito.doThrow(new IllegalStateException("boom")).when(sync)
                .writeSystemRecord(anyLong(), anyInt(), any(), anyString(), any());
        assertThatCode(() -> hooks.afterSalesClosed(a)).doesNotThrowAnyException();
        verify(sync).customerChanged(7L);
        verify(sync).writeSystemRecord(7L, 5, 1L, "售后单 AS1 结案 · 补发 2 台", "案例沉淀：该型号运输需加泡沫护角");
    }

    /** 每个会改变订单履约、售后评价事实的入口都调用了钩子（在方法体里、最后的 return 之前） */
    @ParameterizedTest
    @CsvSource({
            "sales/service/impl/SalesOrderServiceImpl, convert",
            "sales/service/impl/SalesOrderServiceImpl, create",
            "sales/service/impl/SalesOrderServiceImpl, cancel",
            "sales/service/impl/SalesOrderServiceImpl, partialCancel",
            "sales/service/impl/SalesOrderServiceImpl, updateProgress",
            "sales/service/impl/SalesOrderServiceImpl, updateStockType",
            "sales/service/impl/SalesOrderServiceImpl, complete",
            "purchase/service/impl/PurchaseOrderServiceImpl, save",
            "purchase/service/impl/PurchaseOrderServiceImpl, confirm",
            "purchase/service/impl/PurchaseOrderServiceImpl, cancel",
            "warehouse/service/ShipmentService, create",
            "warehouse/service/ShipmentService, update",
            "warehouse/service/ShipmentService, voidShipment",
            "warehouse/service/ReceiptService, accept",
            "warehouse/service/ReceiptService, direct",
            "warehouse/service/ReceiptService, reverse",
            "warehouse/service/DiscrepancyService, handle",
            "warehouse/service/DiscrepancyService, reopen",
            "warehouse/service/ShootService, addMedia",
            "warehouse/service/ShootService, removeMedia",
            "logistics/service/OutboundService, createNotice",
            "logistics/service/OutboundService, updateNotice",
            "logistics/service/OutboundService, withdraw",
            "logistics/service/OutboundService, pack",
            "logistics/service/OutboundService, handOver",
            "logistics/service/OutboundService, undoHandOver",
            "logistics/service/LogisticsShipmentService, create",
            "logistics/service/LogisticsShipmentService, updateItems",
            "logistics/service/LogisticsShipmentService, voidShipment",
            "logistics/service/LogisticsShipmentService, confirmDirect",
            "logistics/service/LogisticsShipmentService, ship",
            "logistics/service/LogisticsShipmentService, updateShipping",
            "aftersales/service/AfterSalesService, create",
            "aftersales/service/AfterSalesService, updatePlan",
            "aftersales/service/AfterSalesService, setStatus",
            "aftersales/service/AfterSalesService, close",
            "aftersales/service/AfterSalesService, replace",
            "aftersales/service/AfterSalesService, cancel",
    })
    void entryCallsHook(String file, String method) throws IOException {
        String src = Files.readString(Path.of("src/main/java/com/zhul/erp/modules", file + ".java"));
        int start = src.indexOf(" " + method + "(", src.indexOf("@Transactional"));
        while (start >= 0 && !src.substring(src.lastIndexOf('\n', start), start).contains("public ")) {
            start = src.indexOf(" " + method + "(", start + 1);
        }
        assertThat(start).as(file + "#" + method).isPositive();
        int end = src.indexOf("\n    }\n", start);
        assertThat(src.substring(start, end)).as(file + "#" + method).contains("followupHooks.");
    }
}
