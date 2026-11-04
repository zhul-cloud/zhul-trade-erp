package com.zhul.erp.modules.crm.followup;

import com.zhul.erp.modules.crm.followup.support.FollowupHooks;
import com.zhul.erp.modules.crm.followup.support.FollowupSync;
import com.zhul.erp.modules.crm.service.OpportunityService;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 单据钩子的异常隔离（tasks 4.3、4.2）：自动赢单、自动推进出错不影响收款确认、PI 发送本身 */
class FollowupHooksTest {

    private final FollowupSync sync = mock(FollowupSync.class);
    private final OpportunityService opportunityService = mock(OpportunityService.class);
    private final FollowupHooks hooks = new FollowupHooks(sync, opportunityService);

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
}
