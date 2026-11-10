package com.zhul.erp.modules.system.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.sales.SalesContractSupport;
import com.zhul.erp.modules.system.notification.constants.NotificationNode;
import com.zhul.erp.modules.system.notification.support.Notice;
import com.zhul.erp.modules.system.notification.support.Notifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/** spec system/notification 与 purchase/purchase-requirement「超时未下单标红」 */
class NotificationContractTest extends SalesContractSupport {

    private static final String API = "/api/v1/notifications";
    private static final String RECEIVERS = "/api/v1/system/notification-receivers";
    private static final String SHIP = "/api/v1/purchase/shipments";
    private static final String GR = "/api/v1/warehouse/receipts";
    private static final long KEEPER = 99000041L;
    private static final int MENU_REQ = 100087;
    private static final int MENU_PO = 100088;
    private static final int MENU_SHIP = 100089;
    private static final int[] BUYER_BUTTONS = {110187, 110188, 110189, 110190, 110191, 110192};

    @Autowired private Notifier notifier;
    @Autowired private TransactionTemplate tx;

    private String lin;
    private String keeper;

    @BeforeEach
    void people() {
        clearNotifications();
        for (int id : new int[] {MENU_REQ, MENU_PO, MENU_SHIP}) {
            jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", BUYER_ROLE, id);
        }
        for (int id : BUYER_BUTTONS) {
            jdbc.update("insert into role_resource (role_code, resource_id, resource_code) values (?, ?, '')", BUYER_ROLE, id);
        }
        jdbc.update("insert into user_basic (id, tenant_id, name, username, role_code, status) values (?, 0, '陈晨', 'it_keeper', 'ROLE_WH', 1)", KEEPER);
        jdbc.update("insert into account (id, tenant_id, user_id, username, admin_flag) values (?, 0, ?, 'it_keeper', 0)", KEEPER, KEEPER);
        lin = token("it_lin");
        keeper = token("it_keeper");
    }

    @AfterEach
    void cleanup() {
        clearNotifications();
        jdbc.update("update sys_config set config_value = '24' where tenant_id = 0 and config_key = 'purchase.requirement.overdue-hours'");
        jdbc.update("delete from account where id = ?", KEEPER);
        jdbc.update("delete from user_basic where id = ?", KEEPER);
    }

    private void clearNotifications() {
        jdbc.update("delete from notification where tenant_id = 0");
        jdbc.update("delete from notification_receiver where tenant_id = 0");
    }

    // ---------------------------------------------------------------- 准备数据

    /** 一张订单：每行的采购回价来自林熙，转成订单后需求的采购员为林熙；返回销售订单 ID */
    private long order(long supplierId, L... lines) throws Exception {
        long c = customer("ACROBOT", "India");
        List<Long> items = quotationItemIds(quotation(c, 2, "USD", null, lines));
        for (Long i : items) {
            long inquiryItem = jdbc.queryForObject("select inquiry_item_id from quotation_item where id = ?", Long.class, i);
            jdbc.update("insert into sourcing_quote (tenant_id, inquiry_item_id, quoted_by, supplier_id, channel, shop_name) values (0, ?, ?, ?, 4, '')",
                    inquiryItem, BUYER_LIN, supplierId);
            long quote = jdbc.queryForObject("select max(id) from sourcing_quote where inquiry_item_id = ?", Long.class, inquiryItem);
            jdbc.update("update quotation_item set cost_quote_id = ? where id = ?", quote, i);
        }
        long pi = sentPi(items, null);
        ok(uploadSlip(pi, "1.00", "2026-10-07", admin));
        return ok(call(json(post(PI + "/" + pi + "/convert"), "{}"), admin)).path("id").asLong();
    }

    private long draftOf(long supplierId) {
        return jdbc.queryForObject("select max(id) from purchase_order where tenant_id = 0 and supplier_id = ? and status = 1 and deleted_at is null",
                Long.class, supplierId);
    }

    private long ship(JsonNode po) throws Exception {
        List<Map<String, Object>> items = new ArrayList<>();
        po.path("items").forEach(i -> items.add(Map.of("poItemId", i.path("id").asLong(), "quantity", i.path("quantity").asInt())));
        Map<String, Object> body = new HashMap<>();
        body.put("poId", po.path("id").asLong());
        body.put("carrier", "顺丰");
        body.put("trackingNo", "SF1234568812");
        body.put("shipDate", LocalDate.now().toString());
        body.put("expectedArrivalDate", LocalDate.now().plusDays(2).toString());
        body.put("items", items);
        return ok(call(json(post(SHIP), write(body)), lin)).path("shipment").path("id").asLong();
    }

    /** 全部合格收货 */
    private void receiveAll(long shipmentId) throws Exception {
        JsonNode s = ok(call(get(GR + "/shipments/" + shipmentId), keeper));
        List<Map<String, Object>> items = new ArrayList<>();
        s.path("shipment").path("items").forEach(i -> {
            int q = i.path("quantity").asInt();
            items.add(Map.of("shipmentItemId", i.path("id").asLong(), "receivedQty", q, "qualifiedQty", q, "defectiveQty", 0));
        });
        ok(call(json(post(GR), write(Map.of("shipmentId", shipmentId, "receivedDate", LocalDate.now().toString(), "items", items))), keeper));
    }

    private JsonNode list(String token, String tab) throws Exception {
        return ok(call(get(API).param("tab", tab), token)).path("records");
    }

    private static JsonNode find(JsonNode list, String node) {
        for (JsonNode n : list) {
            if (node.equals(n.path("node").asText())) {
                return n;
            }
        }
        return null;
    }

    private int rows(String node, long recipient) {
        return jdbc.queryForObject("select count(*) from notification where tenant_id = 0 and node_code = ? and recipient_id = ?",
                Integer.class, node, recipient);
    }

    // ---------------------------------------------------------------- 下单 → 发货 → 收货

    @Test
    void orderToReceiptNotifiesEachStepAndClosesTodos() throws Exception {
        long sup = supplier("深圳华强", null);
        long so = order(sup, l("6ES7 214-1AG40-0XB0", 2, "800", "150"), l("6AV2 123-2GB03-0AX0", 1, "900", "200"));

        // 转成订单：采购员林熙收到「有型号需要你采购」，2 个型号合并成一条；操作人自己不收
        JsonNode todo = find(list(lin, "todo"), NotificationNode.SO_PURCHASE.name());
        assertTrue(todo != null, "林熙应收到采购待办");
        assertTrue(todo.path("title").asText().contains("2"), todo.toString());
        assertEquals("/purchase/requirements", todo.path("link").asText());
        assertEquals(0, rows(NotificationNode.SO_PURCHASE.name(), ADMIN_USER));
        assertTrue(ok(call(get(API + "/count"), lin)).path("todo").asInt() >= 1);

        // 确认下单后，待办自动变为已处理
        long po = draftOf(sup);
        priceAndConfirm(po, "800", lin);
        assertTrue(find(list(lin, "todo"), NotificationNode.SO_PURCHASE.name()) == null);
        assertTrue(find(list(lin, "all"), NotificationNode.SO_PURCHASE.name()).path("done").asBoolean());

        // 登记发货：收货人默认是采购订单的采购员；林熙自己登记的，不通知自己。业务员收到知会
        long sd = ship(po(po, lin));
        assertEquals(0, rows(NotificationNode.SUPPLIER_SHIPPED.name(), BUYER_LIN));
        JsonNode shipped = find(list(admin, "all"), NotificationNode.SHIPPED_FOR_SALES.name());
        assertTrue(shipped != null && shipped.path("content").asText().contains("6ES7 214-1AG40-0XB0 × 2"), String.valueOf(shipped));

        // 收货完成：业务员收到「货已入库」与「订单货已到齐」，货齐只发一次
        receiveAll(sd);
        assertTrue(find(list(admin, "all"), NotificationNode.GOODS_RECEIVED.name()) != null);
        JsonNode ready = find(list(admin, "all"), NotificationNode.ORDER_READY.name());
        assertTrue(ready != null, "货齐应通知业务员");
        assertEquals("/sales/orders/" + so, ready.path("link").asText());
        assertEquals(1, rows(NotificationNode.ORDER_READY.name(), ADMIN_USER));
    }

    @Test
    void receiverPostGetsShipmentTodoUntilReceived() throws Exception {
        // 收货人设为陈晨（不跟采购订单的采购员走）
        ok(call(json(put(RECEIVERS + "/RECEIVER"), write(Map.of("poBuyer", false, "userIds", List.of(KEEPER)))), admin));
        long sup = supplier("深圳华强", null);
        order(sup, l("6ES7 214-1AG40-0XB0", 2, "800", "150"));
        long po = draftOf(sup);
        priceAndConfirm(po, "800", lin);
        long sd = ship(po(po, lin));

        JsonNode todo = find(list(keeper, "todo"), NotificationNode.SUPPLIER_SHIPPED.name());
        assertTrue(todo != null, "收货人应收到收货待办");
        assertEquals("/warehouse/receipts", todo.path("link").asText());
        assertEquals(0, rows(NotificationNode.SUPPLIER_SHIPPED.name(), BUYER_LIN));

        receiveAll(sd);
        assertTrue(find(list(keeper, "todo"), NotificationNode.SUPPLIER_SHIPPED.name()) == null, "收货后待办应变为已处理");
    }

    // ---------------------------------------------------------------- 询价

    @Test
    void sourcingPoolAndAssignTodosFollowTheTask() throws Exception {
        String jiang = token("it_jiang");
        ok(call(json(put(RECEIVERS + "/PURCHASE_LEAD"), write(Map.of("poBuyer", false, "userIds", List.of(BUYER_JIANG)))), admin));
        long c = customer("Wingrid Rocha", "Brazil");
        long inq = ok(call(json(post(INQ), "{\"customerId\":" + c + ",\"source\":1,\"urgent\":false,\"rawContent\":\"rfq\"}"), admin)).path("id").asLong();
        Map<String, Object> row = Map.of("brand", "Omron", "category", "传感器", "confirmedModel", "E3Z-D61", "quantity", 10);
        ok(call(json(post(INQ + "/" + inq + "/confirm"), write(Map.of("rows", List.of(row)))), admin));

        // 转采购后进入待分配：采购负责人收到「待分配」
        JsonNode pool = find(list(jiang, "todo"), NotificationNode.SOURCING_POOL.name());
        assertTrue(pool != null, "采购负责人应收到待分配");
        assertEquals("/inquiry/sourcing-board", pool.path("link").asText());

        // 分配给林熙：林熙收到「新的询价任务」，负责人的待分配变为已处理
        long task = jdbc.queryForObject("select id from sourcing_task where customer_inquiry_id = ?", Long.class, inq);
        ok(call(json(post(BOARD + "/assign"), "{\"taskIds\":[" + task + "],\"assigneeId\":" + BUYER_LIN + "}"), admin));
        assertTrue(find(list(lin, "todo"), NotificationNode.SOURCING_ASSIGNED.name()) != null);
        assertTrue(find(list(jiang, "todo"), NotificationNode.SOURCING_POOL.name()) == null);

        // 改派给江晓晞：林熙的待办变为已处理
        ok(call(json(post(BOARD + "/tasks/" + task + "/reassign"), "{\"assigneeId\":" + BUYER_JIANG + "}"), admin));
        assertTrue(find(list(lin, "todo"), NotificationNode.SOURCING_ASSIGNED.name()) == null);
        assertTrue(find(list(jiang, "todo"), NotificationNode.SOURCING_ASSIGNED.name()) != null);
    }

    // ---------------------------------------------------------------- 合并、回滚、已读

    @Test
    void sameKeyMergesAndRollbackSendsNothing() throws Exception {
        TenantContext.setTenantId(0);
        tx.executeWithoutResult(s -> {
            notifier.send(Notice.of(NotificationNode.MODEL_DOUBT, "INQ001", "/inquiry/customer-inquiries/1").merge(1L).ids(List.of(11L)).to(BUYER_LIN));
            notifier.send(Notice.of(NotificationNode.MODEL_DOUBT, "INQ001", "/inquiry/customer-inquiries/1").merge(1L).ids(List.of(12L)).to(BUYER_LIN));
        });
        tx.executeWithoutResult(s -> {
            notifier.send(Notice.of(NotificationNode.MODEL_DOUBT, "INQ002", "/inquiry/customer-inquiries/2").merge(2L).ids(List.of(21L)).to(BUYER_LIN));
            s.setRollbackOnly();
        });
        TenantContext.clear();

        assertEquals(1, rows(NotificationNode.MODEL_DOUBT.name(), BUYER_LIN));
        JsonNode n = find(list(lin, "all"), NotificationNode.MODEL_DOUBT.name());
        assertTrue(n.path("title").asText().contains("2 个型号"), n.toString());
        assertFalse(n.path("read").asBoolean());

        assertEquals(1, ok(call(post(API + "/read-all"), lin)).asInt());
        assertEquals(0, ok(call(get(API + "/count"), lin)).path("unread").asInt());
    }

    @Test
    void receiverSettingsDefaultAndSave() throws Exception {
        JsonNode posts = ok(call(get(RECEIVERS), admin));
        JsonNode receiver = null;
        for (JsonNode p : posts) {
            if ("RECEIVER".equals(p.path("post").asText())) {
                receiver = p;
            }
        }
        assertTrue(receiver != null && receiver.path("poBuyer").asBoolean(), "收货人默认是采购订单的采购员");

        // 全部移除后保存，不再按权限取默认值
        ok(call(json(put(RECEIVERS + "/PURCHASE_LEAD"), write(Map.of("poBuyer", false, "userIds", List.of()))), admin));
        for (JsonNode p : ok(call(get(RECEIVERS), admin))) {
            if ("PURCHASE_LEAD".equals(p.path("post").asText())) {
                assertEquals(0, p.path("users").size());
            }
        }
        // 只有收货人能设为采购订单的采购员
        assertTrue(call(json(put(RECEIVERS + "/FINANCE"), write(Map.of("poBuyer", true, "userIds", List.of()))), admin).path("code").asInt() != 0);
    }

    // ---------------------------------------------------------------- 超时未下单标红

    @Test
    void overdueRequirementIsFlaggedUntilOrdered() throws Exception {
        long sup = supplier("深圳华强", null);
        long so = order(sup, l("6ES7 214-1AG40-0XB0", 2, "800", "150"), l("6AV2 123-2GB03-0AX0", 1, "900", "200"));
        List<Long> reqs = requirementIds(so);
        // 一条生成于 30 小时前（默认时限 24 小时，超时 6 小时），另一条刚生成
        jdbc.update("update purchase_requirement set create_time = date_sub(now(), interval 30 hour) where id = ?", reqs.get(0));

        assertEquals(1, ok(call(get(REQ + "/stats"), admin)).path("overdue").asInt());
        JsonNode page = ok(call(json(post(REQ + "/page"), write(Map.of("overdue", true))), admin));
        assertEquals(1, page.path("total").asInt());
        assertEquals(6, page.path("records").get(0).path("overdueHours").asInt());
        JsonNode group = ok(call(json(post(REQ + "/orders/page"), write(Map.of())), admin)).path("records").get(0);
        assertEquals(1, group.path("overdueCount").asInt());

        // 时限可在系统设置中调整
        jdbc.update("update sys_config set config_value = '6' where tenant_id = 0 and config_key = 'purchase.requirement.overdue-hours'");
        assertEquals(24, ok(call(json(post(REQ + "/page"), write(Map.of("overdue", true))), admin)).path("records").get(0).path("overdueHours").asInt());
        jdbc.update("update sys_config set config_value = '24' where tenant_id = 0 and config_key = 'purchase.requirement.overdue-hours'");

        // 已排入草稿仍算超时，确认下单后不再标红
        assertEquals(1, ok(call(get(REQ + "/stats"), admin)).path("overdue").asInt());
        priceAndConfirm(draftOf(sup), "800", lin);
        assertEquals(0, ok(call(get(REQ + "/stats"), admin)).path("overdue").asInt());
    }
}
