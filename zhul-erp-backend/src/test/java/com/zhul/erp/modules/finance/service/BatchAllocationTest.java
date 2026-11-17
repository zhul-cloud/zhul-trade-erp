package com.zhul.erp.modules.finance.service;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.finance.dto.BatchPaymentRequest;
import com.zhul.erp.modules.finance.dto.RegisterPaymentRequest;
import com.zhul.erp.modules.finance.entity.PayableDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** spec finance/supplier-payment「批量付款」：组内先按各单当期应付、再按未付，依到期日核销 */
class BatchAllocationTest {

    private static PayableDO ap(long id, String amount) {
        PayableDO p = new PayableDO();
        p.setId(id);
        p.setAmount(new BigDecimal(amount));
        p.setPaidAmount(BigDecimal.ZERO);
        return p;
    }

    private static Map<Long, BigDecimal> alloc(String amount, List<PayableDO> rows, Map<Long, BigDecimal> dueNow) {
        BatchPaymentRequest req = new BatchPaymentRequest();
        req.setMethod(1);
        req.setPayDate(LocalDate.now());
        req.setIdempotencyKey("k");
        BatchPaymentRequest.Group g = new BatchPaymentRequest.Group();
        g.setAmount(new BigDecimal(amount));
        RegisterPaymentRequest r = PaymentService.groupRequest(req, g, rows, 0, dueNow);
        return r.getAllocations().stream().collect(java.util.stream.Collectors.toMap(
                RegisterPaymentRequest.Line::getPayableId, l -> l.getAmount().stripTrailingZeros()));
    }

    /** 前一张只有定金到期、后一张待入库：默认金额正好落在各单的当期应付上 */
    @Test
    void defaultAmountLandsOnEachDueNow() {
        List<PayableDO> rows = List.of(ap(1, "10000"), ap(2, "5000"));
        Map<Long, BigDecimal> due = Map.of(1L, new BigDecimal("3000"), 2L, new BigDecimal("5000"));
        assertThat(alloc("8000", rows, due)).isEqualTo(Map.of(1L, new BigDecimal("3E+3"), 2L, new BigDecimal("5E+3")));
        // 多付的部分再按到期日：先补前一张
        assertThat(alloc("9000", rows, due)).isEqualTo(Map.of(1L, new BigDecimal("4E+3"), 2L, new BigDecimal("5E+3")));
        // 改小：先满足前一张的当期应付
        assertThat(alloc("4000", rows, due)).isEqualTo(Map.of(1L, new BigDecimal("3E+3"), 2L, new BigDecimal("1E+3")));
        assertThat(alloc("15000", rows, due)).isEqualTo(Map.of(1L, new BigDecimal("1E+4"), 2L, new BigDecimal("5E+3")));
    }

    /** 全部到期时与原来一样按到期日核销：2,400 + 1,260，付 3,000 → 2,400 / 600 */
    @Test
    void allDueKeepsDueDateOrder() {
        List<PayableDO> rows = List.of(ap(1, "2400"), ap(2, "1260"));
        Map<Long, BigDecimal> due = Map.of(1L, new BigDecimal("2400"), 2L, new BigDecimal("1260"));
        assertThat(alloc("3000", rows, due)).isEqualTo(Map.of(1L, new BigDecimal("2.4E+3"), 2L, new BigDecimal("6E+2")));
        assertThatThrownBy(() -> alloc("3700", rows, due)).isInstanceOf(BizException.class).hasMessageContaining("超过未付合计");
    }
}
