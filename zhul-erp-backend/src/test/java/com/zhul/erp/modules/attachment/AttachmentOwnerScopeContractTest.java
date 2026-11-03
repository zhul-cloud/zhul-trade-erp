package com.zhul.erp.modules.attachment;

import com.zhul.erp.support.TenantContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** 附件链接跟随所属单据的数据范围（audit SEC-005）：同租户内看不到单据的人拿不到附件链接 */
class AttachmentOwnerScopeContractTest extends TenantContractSupport {

    /** IntegrationTestBase.loginWithResources 建的测试用户 ID */
    private static final long ME = 99000002L;
    private static final long SOMEONE_ELSE = 99000097L;

    private long mine;
    private long others;

    @BeforeEach
    void data() {
        cleanup();
        jdbc.update("insert into sales_order (tenant_id, so_no, owner_id, status) values (0, 'ITATT-MINE', ?, 1), (0, 'ITATT-OTHER', ?, 1)",
                ME, SOMEONE_ELSE);
        mine = attachment("SALES_ORDER", orderId("ITATT-MINE"), 0);
        others = attachment("SALES_ORDER", orderId("ITATT-OTHER"), 0);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from biz_attachment where file_key like 'it-att/%'");
        jdbc.update("delete from sales_order where so_no like 'ITATT-%'");
    }

    private long orderId(String no) {
        return jdbc.queryForObject("select id from sales_order where so_no = ?", Long.class, no);
    }

    private long attachment(String ownerType, long ownerId, long uploadedBy) {
        String key = "it-att/" + System.nanoTime() + ".pdf";
        jdbc.update("insert into biz_attachment (tenant_id, owner_type, owner_id, storage, file_key, file_name, content_type, uploaded_by) "
                + "values (0, ?, ?, 'LOCAL', ?, 'a.pdf', 'application/pdf', ?)", ownerType, ownerId, key, uploadedBy);
        return jdbc.queryForObject("select id from biz_attachment where file_key = ?", Long.class, key);
    }

    private int status(long attachmentId, String token) throws Exception {
        return perform(get("/api/v1/attachments/" + attachmentId + "/url"), token).getStatus();
    }

    @Test
    void salesStaffOnlyGetsLinksOfOrdersInTheirScope() throws Exception {
        int salesMenu = jdbc.queryForObject("select id from resource where path = '/sales/orders' and type = 2 limit 1", Integer.class);
        loginWithResources("it_att_sales", salesMenu);
        String token = token("it_att_sales", 0);
        assertEquals(200, status(mine, token), "自己负责的订单附件可以预览");
        assertEquals(403, status(others, token), "数据范围仅本人：别人负责的订单附件拿不到链接");

        // 还没挂到单据上的附件只有上传人能看
        assertEquals(200, status(attachment("SALES_ORDER", 0, ME), token));
        assertEquals(403, status(attachment("SALES_ORDER", 0, SOMEONE_ELSE), token));
    }

    @Test
    void adminSeesAll() throws Exception {
        loginAsAdmin("it_att_admin");
        assertEquals(200, status(others, token("it_att_admin", 0)));
    }
}
