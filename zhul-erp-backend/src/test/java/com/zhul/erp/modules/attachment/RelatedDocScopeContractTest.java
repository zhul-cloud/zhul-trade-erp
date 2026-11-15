package com.zhul.erp.modules.attachment;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 全项目排查（audit SEC-005 补全、SEC-016 补全、SEC-017）：报销、费用、付款类附件链接跟随单据规则；
 * 询价回价附件重新挂接核对上传人；按关联单据列出的接口校验关联单据的数据范围
 */
class RelatedDocScopeContractTest extends TenantContractSupport {

    /** IntegrationTestBase.loginWithResources 建的测试用户 ID */
    private static final long ME = 99000002L;
    private static final long SOMEONE_ELSE = 99000097L;
    private static final int MENU_SO = 100079;
    private static final int MENU_PO = 100088;
    private static final int MENU_PAYABLES = 100103;
    private static final int MENU_PAYMENTS = 100104;
    private static final int MENU_REIMBURSE = 100105;
    private static final int MENU_AFTER_SALES = 100120;
    private static final int MENU_REFUNDS = 100110;
    private static final int MENU_PURCHASE_RETURNS = 110232;
    private static final int PERM_REIMBURSE_PAY = 110206;

    @Autowired private AttachmentService attachments;

    @BeforeEach
    @AfterEach
    void cleanup() {
        jdbc.update("delete from biz_attachment where file_key like 'it-rel/%'");
        jdbc.update("delete from fin_payment_allocation where payment_id in (select id from fin_payment where payment_no like 'ITREL%')");
        jdbc.update("delete from fin_payment where payment_no like 'ITREL%'");
        jdbc.update("delete from fin_payable where payable_no like 'ITREL%'");
        jdbc.update("delete from fin_reimbursement where reimburse_no like 'ITREL%'");
        jdbc.update("delete from fin_expense where expense_no like 'ITREL%'");
        jdbc.update("delete from courier_waybill where tracking_no like 'ITREL%'");
        jdbc.update("delete from purchase_return where return_no like 'ITREL%'");
        jdbc.update("delete from purchase_order where po_no like 'ITREL%'");
        jdbc.update("delete from after_sales where as_no like 'ITREL%'");
        jdbc.update("delete from sales_order where so_no like 'ITREL%'");
    }

    private long id(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private long attachment(String ownerType, long ownerId, long uploadedBy) {
        String key = "it-rel/" + System.nanoTime() + ".pdf";
        jdbc.update("insert into biz_attachment (tenant_id, owner_type, owner_id, storage, file_key, file_name, content_type, uploaded_by) "
                + "values (0, ?, ?, 'LOCAL', ?, 'a.pdf', 'application/pdf', ?)", ownerType, ownerId, key, uploadedBy);
        return id("select id from biz_attachment where file_key = ?", key);
    }

    private int link(long attachmentId, String token) throws Exception {
        return perform(get("/api/v1/attachments/" + attachmentId + "/url"), token).getStatus();
    }

    private long po(String no, long purchaser) {
        jdbc.update("insert into purchase_order (tenant_id, po_no, purchaser_id, status) values (0, ?, ?, 1)", no, purchaser);
        return id("select id from purchase_order where po_no = ?", no);
    }

    /** 采购订单的应付与一笔核销到它的付款，返回付款 ID */
    private long paymentOf(long poId, String no, String advanceUser) {
        jdbc.update("insert into fin_payable (tenant_id, payable_no, source_type, source_id) values (0, ?, 1, ?)", no, poId);
        long payable = id("select id from fin_payable where payable_no = ?", no);
        jdbc.update("insert into fin_payment (tenant_id, payment_no, pay_date, advance_user, idempotency_key) values (0, ?, CURDATE(), ?, ?)",
                no, advanceUser, no);
        long payment = id("select id from fin_payment where payment_no = ?", no);
        jdbc.update("insert into fin_payment_allocation (tenant_id, payment_id, payable_id) values (0, ?, ?)", payment, payable);
        return payment;
    }

    @Test
    void paymentAttachments_followPurchaserScope_andOwnAdvance() throws Exception {
        long mine = attachment("PAYMENT", paymentOf(po("ITREL-PO-MINE", ME), "ITREL-P1", ""), 0);
        long others = attachment("PAYMENT", paymentOf(po("ITREL-PO-OTHER", SOMEONE_ELSE), "ITREL-P2", ""), 0);

        loginWithResources("it_rel_buyer", MENU_PO);
        String buyer = token("it_rel_buyer", 0);
        assertEquals(200, link(mine, buyer), "自己采购订单的付款附件");
        assertEquals(403, link(others, buyer), "别的采购员订单的付款附件");

        loginWithResources("it_rel_staff", MENU_REIMBURSE);
        String staff = token("it_rel_staff", 0);
        long myAdvance = attachment("PAYMENT", paymentOf(po("ITREL-PO-ADV1", SOMEONE_ELSE), "ITREL-P3", "it_rel_staff"), 0);
        long othersAdvance = attachment("PAYMENT", paymentOf(po("ITREL-PO-ADV2", SOMEONE_ELSE), "ITREL-P4", "it_rel_other"), 0);
        assertEquals(200, link(myAdvance, staff), "本人垫付的付款截图");
        assertEquals(403, link(othersAdvance, staff), "别人垫付的付款截图");

        loginWithResources("it_rel_fin", MENU_PAYMENTS);
        assertEquals(200, link(others, token("it_rel_fin", 0)), "付款管理看全部");
    }

    @Test
    void reimbursementProofs_onlyOwnUnlessPayer() throws Exception {
        jdbc.update("insert into fin_reimbursement (tenant_id, reimburse_no, applicant_id) values (0, 'ITREL-B1', ?), (0, 'ITREL-B2', ?)",
                ME, SOMEONE_ELSE);
        jdbc.update("insert into fin_expense (tenant_id, expense_no, occur_date, advance_user_id) values "
                + "(0, 'ITREL-E1', CURDATE(), ?), (0, 'ITREL-E2', CURDATE(), ?)", ME, SOMEONE_ELSE);
        jdbc.update("insert into courier_waybill (tenant_id, tracking_no, payer_id) values (0, 'ITREL-W1', ?), (0, 'ITREL-W2', ?)",
                ME, SOMEONE_ELSE);
        jdbc.update("insert into purchase_return (tenant_id, return_no, supplier_id, source_type, owner_id, payer_id) values "
                + "(0, 'ITREL-R1', 1, 1, ?, ?), (0, 'ITREL-R2', 1, 1, ?, ?)", SOMEONE_ELSE, ME, SOMEONE_ELSE, SOMEONE_ELSE);
        long[][] pairs = {
                {attachment("REIMBURSE", id("select id from fin_reimbursement where reimburse_no = 'ITREL-B1'"), 0),
                        attachment("REIMBURSE", id("select id from fin_reimbursement where reimburse_no = 'ITREL-B2'"), 0)},
                {attachment("EXPENSE", id("select id from fin_expense where expense_no = 'ITREL-E1'"), 0),
                        attachment("EXPENSE", id("select id from fin_expense where expense_no = 'ITREL-E2'"), 0)},
                {attachment("COURIER_PROOF", id("select id from courier_waybill where tracking_no = 'ITREL-W1'"), 0),
                        attachment("COURIER_PROOF", id("select id from courier_waybill where tracking_no = 'ITREL-W2'"), 0)},
                {attachment("RETURN_PROOF", id("select id from purchase_return where return_no = 'ITREL-R1'"), 0),
                        attachment("RETURN_PROOF", id("select id from purchase_return where return_no = 'ITREL-R2'"), 0)},
        };
        loginWithResources("it_rel_staff", MENU_REIMBURSE);
        String staff = token("it_rel_staff", 0);
        for (long[] p : pairs) {
            assertEquals(200, link(p[0], staff), "本人申请 / 垫付的凭证");
            assertEquals(403, link(p[1], staff), "别人申请 / 垫付的凭证");
        }
        loginWithResources("it_rel_payer", MENU_REIMBURSE, PERM_REIMBURSE_PAY);
        String payer = token("it_rel_payer", 0);
        for (long[] p : pairs) {
            assertEquals(200, link(p[1], payer), "有报销付款权限的看全部");
        }
    }

    @Test
    void reassign_rejectsOthersUnattachedUpload() {
        loginWithResources("it_rel_buyer", MENU_PO);
        long others = attachment("SOURCING_QUOTE", 0, SOMEONE_ELSE);
        long mine = attachment("SOURCING_QUOTE", 0, ME);
        BizException e = assertThrows(BizException.class, () -> TenantContext.runAs(0,
                () -> attachments.reassign(AttachmentService.SOURCING_QUOTE, List.of(others), List.of(), 123456L)));
        assertEquals("附件不存在，请重新上传", e.getMessage());
        assertEquals(0L, id("select owner_id from biz_attachment where id = ?", others));
        TenantContext.runAs(0, () -> attachments.reassign(AttachmentService.SOURCING_QUOTE, List.of(mine), List.of(), 123456L));
        assertEquals(123456L, id("select owner_id from biz_attachment where id = ?", mine));
    }

    @Test
    void listsByRelatedDoc_followRelatedDocScope() throws Exception {
        long minePo = po("ITREL-PO-L1", ME);
        long othersPo = po("ITREL-PO-L2", SOMEONE_ELSE);
        paymentOf(othersPo, "ITREL-P5", "");
        jdbc.update("insert into purchase_return (tenant_id, return_no, supplier_id, source_type, owner_id, po_id) values "
                + "(0, 'ITREL-R3', 1, 1, ?, ?)", SOMEONE_ELSE, othersPo);
        jdbc.update("insert into sales_order (tenant_id, so_no, owner_id, status) values (0, 'ITREL-SO', ?, 1)", SOMEONE_ELSE);
        long so = id("select id from sales_order where so_no = 'ITREL-SO'");
        jdbc.update("insert into after_sales (tenant_id, as_no, customer_id, so_id, owner_id, status) values "
                + "(0, 'ITREL-AS1', 1, ?, ?, 1), (0, 'ITREL-AS2', 1, ?, ?, 1)", so, SOMEONE_ELSE, so, ME);

        loginWithResources("it_rel_buyer", MENU_PO);
        String buyer = token("it_rel_buyer", 0);
        JsonNode denied = call(get("/api/v1/finance/payables/by-source?sourceType=1&sourceId=" + othersPo), buyer);
        assertEquals("采购订单不存在", denied.path("message").asText(), "别的采购员订单的应付");
        assertEquals(0, call(get("/api/v1/finance/payables/by-source?sourceType=1&sourceId=" + minePo), buyer).path("code").asInt());
        assertEquals(0, ok(call(get("/api/v1/purchase/returns/of-order/" + othersPo), buyer)).size(), "别人负责的采购退货不列出");

        loginWithResources("it_rel_payables", MENU_PAYABLES);
        assertTrue(ok(call(get("/api/v1/finance/payables/by-source?sourceType=1&sourceId=" + othersPo),
                token("it_rel_payables", 0))).path("id").asLong() > 0, "应付管理看全部");

        loginWithResources("it_rel_sales", MENU_SO, MENU_AFTER_SALES);
        JsonNode list = ok(call(get("/api/v1/sales/after-sales/order/" + so), token("it_rel_sales", 0)));
        assertEquals(1, list.size(), "订单不在范围内时只列自己负责的售后单");
        assertEquals("ITREL-AS2", list.get(0).path("asNo").asText());
        loginWithResources("it_rel_refund", MENU_REFUNDS);
        assertEquals(2, ok(call(get("/api/v1/sales/after-sales/order/" + so), token("it_rel_refund", 0))).size(), "财务看全部");
    }

    /** audit SEC-022：别人处理去向生成的采购退货单，采购订单的采购员（本人范围）也能看到并处理 */
    @Test
    void purchaseReturn_visibleToPurchaserOfItsOrder() throws Exception {
        long minePo = po("ITREL-PO-R1", ME);
        long othersPo = po("ITREL-PO-R2", SOMEONE_ELSE);
        // 售后来源都有退货入库单，这里只要非空即可
        jdbc.update("insert into purchase_return (tenant_id, return_no, supplier_id, source_type, owner_id, po_id, return_receipt_id) values "
                + "(0, 'ITREL-R4', 1, 1, ?, ?, 0), (0, 'ITREL-R5', 1, 1, ?, ?, 0)", SOMEONE_ELSE, minePo, SOMEONE_ELSE, othersPo);
        long mine = id("select id from purchase_return where return_no = 'ITREL-R4'");
        long others = id("select id from purchase_return where return_no = 'ITREL-R5'");
        long mineFile = attachment("PURCHASE_RETURN", mine, 0);
        long othersFile = attachment("PURCHASE_RETURN", others, 0);

        loginWithResources("it_rel_buyer", MENU_PO, MENU_PURCHASE_RETURNS);
        String buyer = token("it_rel_buyer", 0);
        assertEquals(1, ok(call(get("/api/v1/purchase/returns/of-order/" + minePo), buyer)).size(), "本人采购订单的采购退货");
        assertEquals("ITREL-R4", ok(call(get("/api/v1/purchase/returns/" + mine), buyer)).path("returnNo").asText());
        JsonNode page = ok(call(post("/api/v1/purchase/returns/page").contentType("application/json").content("{}"), buyer));
        assertEquals(1, page.path("total").asInt(), "列表含本人采购订单的采购退货，不含别人的");
        assertEquals("ITREL-R4", page.path("records").get(0).path("returnNo").asText());
        assertEquals(1, ok(call(get("/api/v1/purchase/returns/counts"), buyer)).path("pending").asInt());
        assertEquals(200, link(mineFile, buyer), "本人采购订单的采购退货附件");
        assertEquals("采购退货单不存在", call(get("/api/v1/purchase/returns/" + others), buyer).path("message").asText());
        assertEquals(403, link(othersFile, buyer), "别人的采购订单、别人负责的采购退货附件");
    }
}
