package com.zhul.erp.modules.finance;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.sales.SalesContractSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** spec finance/customer-refund、finance/other-income、finance/operating-report 与 finance/expense（类别与分摊） */
class OperatingReportContractTest extends SalesContractSupport {

    private static final String REFUND = "/api/v1/finance/refunds";
    private static final String INCOME = "/api/v1/finance/other-incomes";
    private static final String FE = "/api/v1/finance/expenses";
    private static final String REPORT = "/api/v1/finance/operating-report";
    private static final String SO = "/api/v1/sales/orders";

    @BeforeEach
    void banks() {
        bankAccount("USD");
    }

    private long upload(String ownerType) throws Exception {
        return ok(call(multipart("/api/v1/attachments").file(new MockMultipartFile("file", "凭证.png", "image/png", PNG))
                .param("ownerType", ownerType), admin)).path("id").asLong();
    }

    /** 信保收款 USD 309.19（手续费 9.19）后转成的订单 */
    private long paidOrder() throws Exception {
        long c = customer("Surya Prima Mandiri CV", "Indonesia");
        long pi = sentPi(quotationItemIds(quotation(c, 2, "USD", null, l("6AV2123-2JB03-0AX0", 1, "1000", "309.19"))), null);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentMethod", "ALIBABA_TA");
        body.put("platformOrderNo", "317394886001028011");
        body.put("amount", "309.19");
        body.put("platformFee", "9.19");
        body.put("receiptDate", LocalDate.now().toString());
        ok(call(json(post(PI + "/" + pi + "/platform-receipts"), write(body)), admin));
        JsonNode so = ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin));
        return so.path("id").isMissingNode() ? jdbc.queryForObject("select max(id) from sales_order where tenant_id = 0", Long.class)
                : so.path("id").asLong();
    }

    private Map<String, Object> refundBody(long so, String amount) throws Exception {
        Map<String, Object> b = new HashMap<>();
        b.put("soId", so);
        b.put("refundDate", LocalDate.now().toString());
        b.put("reason", "QUALITY");
        b.put("amount", amount);
        b.put("method", 1);
        b.put("attachmentIds", List.of(upload("REFUND")));
        return b;
    }

    private Map<String, Object> expense(String name, String category, String amount, int months, LocalDate date) {
        Map<String, Object> b = new HashMap<>();
        b.put("name", name);
        b.put("category", category);
        b.put("amortizeMonths", months);
        b.put("occurDate", date.toString());
        b.put("payeeType", 4);
        b.put("payeeName", "阿里巴巴");
        b.put("currencyCode", "CNY");
        b.put("amount", amount);
        b.put("method", 1);
        b.put("allocations", List.of());
        return b;
    }

    @Test
    void refundCutsOrderProfit_otherIncome_expenseAmortized_monthlyReport() throws Exception {
        LocalDate today = LocalDate.now();
        int month = today.getMonthValue();
        long so = paidOrder();
        JsonNode before = ok(call(get(SO + "/" + so), admin)).path("cost");
        BigDecimal revenue = before.path("revenue").decimalValue();
        BigDecimal cost = before.path("cost").decimalValue();

        // 退款单：可退金额、超退、默认人民币按平均到账汇率
        JsonNode info = ok(call(get(REFUND + "/refundable/" + so), admin));
        money("309.19", info.path("refundable"));
        money("7.150000", info.path("averageRate"));
        assertEquals("最多还能退 USD 309.19", fail(call(json(post(REFUND), write(refundBody(so, "400"))), admin)).path("message").asText());
        Map<String, Object> noProof = refundBody(so, "50");
        noProof.put("attachmentIds", List.of());
        assertEquals("请上传退款凭证", fail(call(json(post(REFUND), write(noProof)), admin)).path("message").asText());
        JsonNode refund = ok(call(json(post(REFUND), write(refundBody(so, "50"))), admin));
        assertEquals("TK" + TODAY + "001", refund.path("refundNo").asText());
        money("357.50", refund.path("amountCny"));
        assertEquals("质量问题", refund.path("reasonName").asText());
        money("259.19", ok(call(get(REFUND + "/refundable/" + so), admin)).path("refundable"));

        // 订单毛利扣退款
        JsonNode after = ok(call(get(SO + "/" + so), admin)).path("cost");
        money("357.50", after.path("refund"));
        assertEquals(refund.path("refundNo").asText(), after.path("refunds").get(0).path("refundNo").asText());
        money(revenue.subtract(new BigDecimal("357.50")).subtract(cost).toPlainString(), after.path("profit"));
        money("357.50", ok(call(get(REFUND + "/total"), admin)));

        // 其他收入：登记、修改
        Map<String, Object> inc = new HashMap<>();
        inc.put("incomeDate", today.toString());
        inc.put("category", "REBATE");
        inc.put("amount", "600");
        inc.put("remark", "信保 9 月返点");
        JsonNode income = ok(call(json(post(INCOME), write(inc)), admin));
        assertEquals("OI" + TODAY + "001", income.path("incomeNo").asText());
        inc.put("amount", "666.66");
        ok(call(json(put(INCOME + "/" + income.path("id").asLong()), write(inc)), admin));
        inc.put("category", "NOPE");
        assertEquals("类别不存在", fail(call(json(post(INCOME), write(inc)), admin)).path("message").asText());

        // 费用单：类别校验、年费分 12 个月、本月工资；订单费用不进运营成本
        assertEquals("费用类别不存在",
                fail(call(json(post(FE), write(expense("x", "NOPE", "1", 1, today))), admin)).path("message").asText());
        JsonNode fee = ok(call(json(post(FE), write(expense("阿里国际站年费", "PLATFORM", "93324", 12, LocalDate.of(today.getYear(), 1, 5)))), admin));
        assertEquals("平台费", fee.path("categoryName").asText());
        assertEquals(12, fee.path("amortizeMonths").asInt());
        ok(call(json(post(FE), write(expense("10 月工资", "SALARY", "24000", 1, today))), admin));
        assertEquals(2, ok(call(get(FE).param("linked", "false"), admin)).path("total").asInt());
        assertEquals(1, ok(call(get(FE).param("category", "SALARY"), admin)).path("total").asInt());

        // 经营统计
        JsonNode report = ok(call(get(REPORT), admin));
        assertEquals(today.getYear(), report.path("year").asInt());
        assertEquals(month, report.path("rows").size(), "到当前月为止");
        JsonNode jan = report.path("rows").get(0);
        money("7777.00", jan.path("opex").path("PLATFORM"));
        JsonNode now = report.path("rows").get(month - 1);
        money(revenue.toPlainString(), now.path("revenue"));
        money(revenue.subtract(cost).toPlainString(), now.path("profit"));
        money("357.50", now.path("refund"));
        money("666.66", now.path("otherIncome"));
        money("24000.00", now.path("opex").path("SALARY"));
        money("31777.00", now.path("opexTotal"));
        BigDecimal net = revenue.subtract(cost).subtract(new BigDecimal("357.50")).add(new BigDecimal("666.66")).subtract(new BigDecimal("31777.00"));
        money(net.toPlainString(), now.path("net"));
        money(new BigDecimal("7777").multiply(BigDecimal.valueOf(month)).add(new BigDecimal("24000")).toPlainString(),
                report.path("total").path("opexTotal"));
        assertTrue(report.path("categories").toString().contains("人员工资"));
        assertTrue(!report.path("categories").toString().contains("订单费用"), "订单费用不是运营成本类别");

        // 作废退款：订单毛利与经营统计都恢复
        ok(call(json(post(REFUND + "/" + refund.path("id").asLong() + "/void"), write(Map.of("reason", "金额录错"))), admin));
        money("0.00", ok(call(get(SO + "/" + so), admin)).path("cost").path("refund"));
        money("0.00", ok(call(get(REPORT), admin)).path("rows").get(month - 1).path("refund"));
        assertEquals(0, ok(call(get(REFUND), admin)).path("total").asInt(), "作废的默认不显示");

        // 已取消的订单不能登记退款
        ok(call(json(post(SO + "/" + so + "/cancel"), write(Map.of("reason", "客户不要了"))), admin));
        assertEquals("订单已取消，不能登记退款", fail(call(json(post(REFUND), write(refundBody(so, "10"))), admin)).path("message").asText());
    }
}
