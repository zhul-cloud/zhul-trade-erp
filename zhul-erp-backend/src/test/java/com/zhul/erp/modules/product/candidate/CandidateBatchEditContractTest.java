package com.zhul.erp.modules.product.candidate;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.modules.inquiry.InquiryContractSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/** spec product/product-candidate「批量修改品牌与品类」「AI 解析不出的品牌记为 Others」（审核部分） */
class CandidateBatchEditContractTest extends InquiryContractSupport {

    private static final String CAND = "/api/v1/product/candidates";

    @Autowired private TransactionTemplate tx;
    @Autowired private com.zhul.erp.modules.product.candidate.repository.ProductCandidateMapper candidateMapper;
    @Autowired private org.springframework.transaction.PlatformTransactionManager txManager;
    private String admin;
    private long others;
    private long abb;
    private long contactor;
    private long breaker;

    @BeforeEach
    void seed() {
        cleanup();
        loginAsAdmin("it_bedit_admin");
        admin = token("it_bedit_admin");
        others = jdbc.queryForObject("select id from product_brand where tenant_id = 0 and brand_name = 'Others'", Long.class);
        jdbc.update("insert into product_brand (tenant_id, brand_name, status) values (0, 'ITBEAbb', 1)");
        abb = jdbc.queryForObject("select id from product_brand where brand_name = 'ITBEAbb'", Long.class);
        contactor = category("itbe-contactor", "仝己接触器");
        breaker = category("itbe-breaker", "仝己断路器");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from product_order_code where product_id in (select id from product where mpn_raw like 'ITBE-%')");
        jdbc.update("delete from product where mpn_raw like 'ITBE-%'");
        jdbc.update("delete from product_candidate where mpn_raw like 'ITBE-%'");
        jdbc.update("delete from product_brand_alias where brand_id in (select id from product_brand where brand_name like 'ITBE%')");
        jdbc.update("delete from product_brand where brand_name like 'ITBE%'");
        jdbc.update("delete from product_category where category_code like 'itbe-%'");
    }

    private long category(String code, String zh) {
        jdbc.update("insert into product_category (tenant_id, category_code, category_name, category_name_zh, status) values (0, ?, ?, ?, 1)",
                code, code, zh);
        return jdbc.queryForObject("select id from product_category where tenant_id = 0 and category_code = ?", Long.class, code);
    }

    private long candidate(String mpn, String brandText, Long brandId, Long categoryId, int status) {
        jdbc.update("insert into product_candidate (tenant_id, brand_id, brand_key, brand_text, mpn_raw, mpn_normalized, category_id, category_text, "
                + "status, level, source_count) values (0, ?, ?, ?, ?, ?, ?, '', ?, 1, 0)",
                brandId, brandText.toLowerCase(), brandText, mpn, mpn.toLowerCase().replace("-", ""), categoryId, status);
        return jdbc.queryForObject("select id from product_candidate where mpn_raw = ? and brand_text = ?", Long.class, mpn, brandText);
    }

    private JsonNode edit(List<Long> ids, Long brandId, Long categoryId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("ids", ids);
        body.put("brandId", brandId);
        body.put("categoryId", categoryId);
        return call(json(post(CAND + "/batch-edit"), write(body)), admin);
    }

    private static JsonNode fail(JsonNode res) {
        assertNotEquals(0, res.path("code").asInt(), res.toString());
        return res;
    }

    private Long col(String column, long id) {
        return jdbc.queryForObject("select " + column + " from product_candidate where id = ?", Long.class, id);
    }

    @Test
    void batchEdit_changesOnlyFilledFields_skipsReviewed_clearsAiMarks() throws Exception {
        long unconfirmed = candidate("ITBE-1", "Mitsu", null, null, 1);
        jdbc.update("update product_candidate set ai_brand = 2, ai_brand_name = 'Mitsubishi' where id = ?", unconfirmed);
        long aiCategory = candidate("ITBE-2", "ABB", abb, contactor, 1);
        jdbc.update("update product_candidate set ai_category = 1, ai_brand = 1 where id = ?", aiCategory);
        long archived = candidate("ITBE-3", "Mitsu", null, null, 2);

        JsonNode vo = ok(edit(List.of(unconfirmed, archived), abb, null));
        assertEquals(1, vo.path("updated").asInt());
        assertEquals(1, vo.path("skipped").asInt(), "已审核的跳过");
        assertEquals(abb, col("brand_id", unconfirmed));
        assertEquals(0L, col("ai_brand", unconfirmed), "改了品牌，AI 标记去掉");
        assertEquals("Mitsubishi", jdbc.queryForObject("select ai_brand_name from product_candidate where id = ?", String.class, unconfirmed),
                "AI 原名称保留");
        assertEquals(null, col("brand_id", archived));

        ok(edit(List.of(aiCategory), null, breaker));
        assertEquals(abb, col("brand_id", aiCategory), "只改品类，品牌不变");
        assertEquals(1L, col("ai_brand", aiCategory));
        assertEquals(breaker, col("category_id", aiCategory));
        assertEquals(0L, col("ai_category", aiCategory));

        ok(edit(List.of(unconfirmed), others, contactor));
        assertEquals(others, col("brand_id", unconfirmed));
        assertEquals(contactor, col("category_id", unconfirmed));
    }

    @Test
    void batchEdit_rejectsInvalidInput() throws Exception {
        long c = candidate("ITBE-11", "ABB", abb, null, 1);
        assertTrue(fail(edit(List.of(c), null, null)).path("message").asText().contains("品牌或品类"));
        assertTrue(fail(edit(LongStream.rangeClosed(1, 201).boxed().toList(), abb, null)).path("message").asText().contains("200"));
        fail(edit(List.of(c), 999_999_999L, null));
        jdbc.update("update product_category set status = 0 where id = ?", breaker);
        fail(edit(List.of(c), null, breaker));
        assertEquals(null, col("category_id", c));
        // 没有审核权限
        Map<String, Object> body = Map.of("ids", List.of(c), "brandId", abb);
        fail(call(json(post(CAND + "/batch-edit"), write(body)), token("it_lin")));
    }

    /** BUS-022：别人驳回还没提交时批量通过：等对方提交后按最新状态跳过，不能用旧快照建档、覆盖驳回 */
    @Test
    void batchApprove_whileRejectInFlight_skipsRejected() throws Exception {
        long c = candidate("ITBE-22", "ABB", abb, contactor, 1);
        CountDownLatch rejecting = new CountDownLatch(1);
        Thread reject = new Thread(() -> tx.executeWithoutResult(s -> {
            jdbc.queryForObject("select id from product_candidate where id = ? for update", Long.class, c);
            jdbc.update("update product_candidate set status = 4 where id = ?", c);
            rejecting.countDown();
            try {
                Thread.sleep(800);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }), "candidate-reject");
        reject.start();
        assertTrue(rejecting.await(10, TimeUnit.SECONDS));
        JsonNode vo = ok(call(json(post(CAND + "/batch-approve"), write(Map.of("ids", List.of(c)))), admin));
        reject.join(10_000);

        assertEquals(0, vo.path("approved").asInt(), vo.toString());
        assertEquals("已审核", vo.path("skipped").get(0).path("reason").asText());
        assertEquals(4L, col("status", c), "仍是驳回");
        assertEquals(null, jdbc.queryForObject("select product_id from product_candidate where id = ?", Long.class, c));
    }

    /** BUS-035：同一事务里按 ID 再读一次，要读到别人已提交的新值，不能拿 MyBatis 会话缓存里的旧对象 */
    @Test
    void sameTransactionReread_seesCommittedChange() throws Exception {
        long c = candidate("ITBE-35", "ABB", abb, contactor, 1);
        TransactionTemplate rc = new TransactionTemplate(txManager);
        rc.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        Integer reread = rc.execute(s -> {
            com.zhul.erp.framework.tenant.TenantContext.setTenantId(0);
            assertEquals(1, candidateMapper.selectById(c).getStatus());
            Thread other = new Thread(() -> jdbc.update("update product_candidate set status = 4 where id = ?", c));
            other.start();
            try {
                other.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return candidateMapper.selectById(c).getStatus();
        });
        assertEquals(4, reread);
    }

    @Test
    void othersCandidates_batchApprove_sameModelMergesIntoFirst() throws Exception {
        long a = candidate("ITBE-21", "Mitsu A", others, contactor, 1);
        long b = candidate("ITBE-21", "Mitsu B", others, contactor, 1);
        JsonNode vo = ok(call(json(post(CAND + "/batch-approve"), write(Map.of("ids", List.of(a, b)))), admin));
        assertEquals(2, vo.path("approved").asInt());
        assertEquals(2L, col("status", a));
        assertEquals(3L, col("status", b), "Others 下同型号改为并入");
        assertEquals(col("product_id", a), col("product_id", b));
        assertEquals(others, jdbc.queryForObject("select brand_id from product where id = ?", Long.class, col("product_id", a)));
    }

    @Test
    void othersCandidate_approveAsNewBrand() throws Exception {
        long c = candidate("ITBE-31", "Mitsu", others, contactor, 1);
        jdbc.update("update product_candidate set ai_brand = 2, ai_brand_name = 'ITBEMitsubishi' where id = ?", c);
        Map<String, Object> approve = Map.of("brandMode", "NEW", "brandName", "ITBEMitsubishi", "categoryId", contactor, "lifecycleStatus", 1);
        ok(call(json(post(CAND + "/" + c + "/approve"), write(approve)), admin));
        long brand = jdbc.queryForObject("select id from product_brand where brand_name = 'ITBEMitsubishi'", Long.class);
        assertEquals(brand, col("brand_id", c));
        assertEquals(brand, jdbc.queryForObject("select brand_id from product where id = ?", Long.class, col("product_id", c)));
        assertEquals(0L, col("ai_brand", c));
        assertEquals(1L, jdbc.queryForObject("select count(*) from product_brand_alias where brand_id = ? and alias = 'Mitsu'", Long.class, brand),
                "品牌原文成为新品牌的别名");
    }
}
