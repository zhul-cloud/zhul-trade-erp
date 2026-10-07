package com.zhul.erp.modules.sales;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec sales/payment-receipt：水单、到账、手续费差额、作废、收款状态 */
class PaymentReceiptContractTest extends SalesContractSupport {

    private static final L[] FOUR = {l("6ES7214-1AG40-0XB0", 1, "4500", "740.44"), l("6ES7231-4HD32-0XB0", 2, "1230", "180.00"),
            l("6AV2123-2GB03-0AX0", 2, "420", "65.73"), l("6SL3210-1KE21-3UF1", 2, "350", "54.39")};

    /** 合计 USD 1,333.65 的 PI：型号 1,340.68 + 运费 60 − 5% */
    private long pi1333() throws Exception {
        long c = customer("ACROBOT", "India");
        return sentPi(quotationItemIds(quotation(c, 2, "USD", "60", FOUR)), 5);
    }

    @Test
    void depositSlip_thenBankFeeDifference() throws Exception {
        long id = pi1333();
        assertEquals("1333.65", ok(call(get(PI + "/" + id), admin)).path("version").path("totalAmount").decimalValue().toPlainString());
        JsonNode afterSlip = ok(uploadSlip(id, "667.00", "2026-10-07", admin));
        assertEquals(2, afterSlip.path("receiptStatus").asInt());
        assertEquals("待到账", afterSlip.path("receiptStatusName").asText());
        JsonNode slip = afterSlip.path("receipts").get(0);
        assertEquals(1, slip.path("kind").asInt());
        assertEquals("slip.png", slip.path("files").get(0).path("fileName").asText());
        money("0", afterSlip.path("receivedAmount"));
        assertEquals(200, perform(get(PI + "/" + id + "/slips/" + slip.path("id").asLong() + "/files/0"), admin).getStatus());

        // 剩余 1,333.65 − 1,000 = 333.65 > 50：不能记为手续费
        assertTrue(fail(confirmReceipt(id, "1000", null, true, admin)).path("message").asText().contains("超过可记为手续费的上限 USD 50"));
        JsonNode paid = ok(confirmReceipt(id, "1308.65", slip.path("id").asLong(), true, admin));
        money("1308.65", paid.path("receivedAmount"));
        money("25.00", paid.path("feeDiffAmount"));
        assertEquals(4, paid.path("receiptStatus").asInt());
        JsonNode receipt = paid.path("receipts").get(1);
        money("9356.85", receipt.path("amountCny"));
        assertTrue(paid.path("receipts").get(0).path("matched").asBoolean());
        assertEquals("这张水单已有对应的到账记录，不能删除",
                fail(call(delete(PI + "/" + id + "/slips/" + slip.path("id").asLong()), admin)).path("message").asText());
    }

    @Test
    void depositThenBalance_voidWrongReceipt() throws Exception {
        long c = customer("Pacific Controls", "Australia");
        long id = sentPi(quotationItemIds(quotation(c, 2, "USD", null, l("6ES7214", 1, "5000", "1000"))), null);
        assertEquals(3, ok(confirmReceipt(id, "500", null, false, admin)).path("receiptStatus").asInt(), "部分到账");
        JsonNode full = ok(confirmReceipt(id, "500", null, false, admin));
        assertEquals(4, full.path("receiptStatus").asInt(), "已到账");

        long wrong = full.path("receipts").get(1).path("id").asLong();
        assertEquals("请填写作废原因",
                fail(call(json(post(PI + "/" + id + "/receipts/" + wrong + "/void"), "{\"reason\":\" \"}"), admin)).path("message").asText());
        JsonNode voided = ok(call(json(post(PI + "/" + id + "/receipts/" + wrong + "/void"), "{\"reason\":\"金额录错\"}"), admin));
        assertEquals(3, voided.path("receiptStatus").asInt());
        money("500", voided.path("receivedAmount"));
        assertEquals(2, voided.path("receipts").get(1).path("status").asInt());
        assertEquals("金额录错", voided.path("receipts").get(1).path("voidReason").asText());
    }

    @Test
    void salesCannotConfirm_draftCannotReceive_slipValidation() throws Exception {
        long c = customer("ACROBOT", "India");
        long draft = ok(createPi(quotationItemIds(quotation(c, 2, "USD", null, FOUR[0])))).path("id").asLong();
        assertEquals("PI 还没有发送，不能登记收款", fail(uploadSlip(draft, "100", "2026-10-07", admin)).path("message").asText());
        long id = pi1333();
        assertEquals("付款金额需要大于 0", fail(uploadSlip(id, "0", "2026-10-07", admin)).path("message").asText());

        loginWithResources("it_sales_rep", MENU_PI, BTN_SLIP);
        String rep = token("it_sales_rep");
        ok(uploadSlip(id, "667.00", "2026-10-07", rep));
        assertEquals(403, perform(json(post(PI + "/" + id + "/receipts"), write(java.util.Map.of("amount", 100, "receiptDate", "2026-10-07",
                "bankAccountId", 1))), rep).getStatus(), "业务员没有登记到账权限");
        assertEquals(List.of(1), ok(call(get(PI + "/" + id), rep)).path("receipts").findValues("kind").stream().map(JsonNode::asInt).toList());
    }
}
