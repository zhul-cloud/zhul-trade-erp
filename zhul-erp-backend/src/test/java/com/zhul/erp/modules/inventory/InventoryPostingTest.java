package com.zhul.erp.modules.inventory;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.entity.InvMoveDO;
import com.zhul.erp.modules.inventory.service.InventoryPosting;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Doc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Line;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Loc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Target;
import com.zhul.erp.modules.inventory.service.InventoryWarehouses;
import com.zhul.erp.modules.inventory.service.InventoryWarehouses.Price;
import com.zhul.erp.support.IntegrationTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 库存过账服务（openspec inventory-foundation tasks 2.3）：边界值、预占、反向冲销、并发 */
class InventoryPostingTest extends IntegrationTestBase {

    private static final int TENANT = 990551;
    private static final long PRODUCT = 990551001L;
    private static final Doc DOC = Doc.of(InvConstants.BIZ_PURCHASE_IN, "GR", 1L, "GR-TEST");
    private static final Doc OUT = Doc.of(InvConstants.BIZ_OTHER_OUT, "OO", 2L, "OO-TEST");

    @Autowired private InventoryPosting posting;
    @Autowired private InventoryWarehouses warehouses;
    @Autowired private TransactionTemplate tx;

    @BeforeEach
    void clean() {
        wipe();
        TenantContext.setTenantId(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        wipe();
    }

    private void wipe() {
        for (String t : List.of("inv_move", "inv_balance", "inv_reservation", "inv_batch", "inv_warehouse")) {
            jdbc.update("delete from " + t + " where tenant_id = ?", TENANT);
        }
    }

    private Loc batch(String no, Price price) {
        return tx.execute(s -> {
            long wh = warehouses.own().getId();
            InvBatchDO b = warehouses.newBatch(no, PRODUCT, "全新原装", InvConstants.BATCH_PURCHASE, 1L, 1L, null, null,
                    LocalDate.now(), price);
            return new Loc(wh, PRODUCT, b.getId(), InvConstants.GOOD);
        });
    }

    private List<InvMoveDO> post(Line... lines) {
        return tx.execute(s -> posting.post(List.of(lines)));
    }

    private int[] balance(Loc k) {
        return jdbc.queryForObject("select on_hand, reserved from inv_balance where tenant_id = ? and warehouse_id = ? and batch_id = ?"
                        + " and stock_status = ?", (rs, i) -> new int[]{rs.getInt(1), rs.getInt(2)},
                TENANT, k.warehouseId(), k.batchId(), k.status());
    }

    @Test
    void zeroOrNegativeQuantityIsRejected() {
        Loc k = new Loc(1, PRODUCT, 1, InvConstants.GOOD);
        assertThrows(IllegalArgumentException.class, () -> Line.of(k, InvConstants.OP_IN, 0, null, DOC));
        assertThrows(IllegalArgumentException.class, () -> Line.of(k, InvConstants.OP_IN, -1, null, DOC));
        assertThrows(IllegalArgumentException.class, () -> Line.of(k, InvConstants.OP_RESERVE, 1, null, DOC));
    }

    @Test
    void inThenOutAndNeverNegative() {
        Loc k = batch("T-1", Price.cny(new BigDecimal("712.3456")));
        List<InvMoveDO> in = post(Line.of(k, InvConstants.OP_IN, 10, null, DOC));
        assertEquals(10, in.get(0).getQuantity());
        assertEquals(0, new BigDecimal("7123.46").compareTo(in.get(0).getAmountCny()));
        List<InvMoveDO> out = post(Line.of(k, InvConstants.OP_OUT, 4, null, OUT));
        assertEquals(-4, out.get(0).getQuantity());
        // 712.3456 × −4 = −2849.3824 → HALF_UP −2849.38
        assertEquals(0, new BigDecimal("-2849.38").compareTo(out.get(0).getAmountCny()));
        assertEquals(6, balance(k)[0]);

        BizException e = assertThrows(BizException.class, () -> post(Line.of(k, InvConstants.OP_OUT, 7, null, OUT)));
        assertTrue(e.getMessage().startsWith("库存不足"), e.getMessage());
        assertEquals(6, balance(k)[0], "失败的过账整体回滚");
        assertEquals(2, jdbc.queryForObject("select count(*) from inv_move where tenant_id = ?", Integer.class, TENANT));
    }

    @Test
    void reservedGoodsCannotBeTakenWithoutTarget() {
        Loc k = batch("T-2", Price.ZERO);
        Target so = Target.soItem(77L);
        post(Line.of(k, InvConstants.OP_IN, 10, so, DOC));
        assertEquals(10, balance(k)[1]);
        post(Line.of(k, InvConstants.OP_RELEASE, 3, so, Doc.of(InvConstants.BIZ_RELEASE, "SO", 9L, "SO-TEST")));
        assertEquals(7, balance(k)[1]);

        BizException e = assertThrows(BizException.class, () -> post(Line.of(k, InvConstants.OP_OUT, 4, null, OUT)));
        assertTrue(e.getMessage().contains("没有预占的只有 3"), e.getMessage());
        post(Line.of(k, InvConstants.OP_OUT, 3, null, OUT));
        // 出订单预占的货：同时减少预占
        post(Line.of(k, InvConstants.OP_OUT, 7, so, OUT));
        assertEquals(0, balance(k)[0]);
        assertEquals(0, balance(k)[1]);
        assertEquals(0, jdbc.queryForObject("select quantity from inv_reservation where tenant_id = ? and target_id = 77",
                Integer.class, TENANT));

        assertThrows(BizException.class, () -> post(Line.of(k, InvConstants.OP_RELEASE, 1, so, OUT)), "预占不能减到负数");
    }

    @Test
    void reverseCreatesOppositeMovesOnlyOnce() {
        Loc k = batch("T-3", Price.cny(new BigDecimal("10")));
        Target so = Target.soItem(88L);
        List<InvMoveDO> in = post(Line.of(k, InvConstants.OP_IN, 5, so, DOC));
        List<InvMoveDO> back = tx.execute(s -> posting.reverse(in, InvConstants.BIZ_PURCHASE_IN_REVERSE));
        assertEquals(InvConstants.OP_OUT, back.get(0).getOpType());
        assertEquals(-5, back.get(0).getQuantity());
        assertEquals(in.get(0).getId(), back.get(0).getReverseOf());
        assertEquals(0, balance(k)[0]);
        assertEquals(0, balance(k)[1]);
        assertTrue(tx.execute(s -> posting.activeMoves("GR", 1L, null)).isEmpty());

        BizException e = assertThrows(BizException.class,
                () -> tx.execute(s -> posting.reverse(in, InvConstants.BIZ_PURCHASE_IN_REVERSE)));
        assertTrue(e.getMessage().contains("不能重复冲销") || e.getMessage().startsWith("库存不足"), e.getMessage());
        assertThrows(BizException.class, () -> tx.execute(s -> posting.reverse(back, InvConstants.BIZ_PURCHASE_IN)),
                "冲销流水不能再冲销");
    }

    @Test
    void transferAndConvertKeepTotals() {
        Loc good = batch("T-4", Price.cny(new BigDecimal("546")));
        Loc bad = new Loc(good.warehouseId(), PRODUCT, good.batchId(), InvConstants.DEFECTIVE);
        post(Line.of(bad, InvConstants.OP_IN, 2, null, DOC));
        Target so = Target.soItem(99L);
        Doc d = Doc.of(InvConstants.BIZ_DISCOUNT_ACCEPT, "RD", 3L, "RD-TEST");
        post(Line.of(bad, InvConstants.OP_CONVERT_OUT, 1, null, d), Line.of(good, InvConstants.OP_CONVERT_IN, 1, so, d));
        assertEquals(1, balance(bad)[0]);
        assertEquals(1, balance(good)[0]);
        assertEquals(1, balance(good)[1]);
        Loc fwd = new Loc(good.warehouseId() + 100000, PRODUCT, good.batchId(), InvConstants.GOOD);
        Doc h = Doc.of(InvConstants.BIZ_HANDOVER, "OB", 4L, "OB-TEST");
        post(Line.of(good, InvConstants.OP_TRANSFER_OUT, 1, so, h), Line.of(fwd, InvConstants.OP_TRANSFER_IN, 1, so, h));
        assertEquals(0, balance(good)[0]);
        assertEquals(1, balance(fwd)[0]);
        assertEquals(1, balance(fwd)[1], "预占随货转到货代仓");
    }

    @Test
    void foreignCurrencyBatchPrice() {
        Loc k = batch("T-5", new Price("USD", new BigDecimal("100.123456"), new BigDecimal("7.123456")));
        // 100.123456 × 7.123456 = 713.225071… → 6 位 713.225071
        BigDecimal cny = jdbc.queryForObject("select unit_price_cny from inv_batch where id = ?", BigDecimal.class, k.batchId());
        assertEquals(0, new BigDecimal("100.123456").multiply(new BigDecimal("7.123456"))
                .setScale(6, java.math.RoundingMode.HALF_UP).compareTo(cny));
        List<InvMoveDO> in = post(Line.of(k, InvConstants.OP_IN, 3, null, DOC));
        assertEquals(0, cny.multiply(BigDecimal.valueOf(3)).setScale(2, java.math.RoundingMode.HALF_UP).compareTo(in.get(0).getAmountCny()));
    }

    @Test
    void concurrentOutboundOfSameBatchOnlyOneSucceeds() throws Exception {
        Loc k = batch("T-6", Price.ZERO);
        post(Line.of(k, InvConstants.OP_IN, 10, null, DOC));
        ExecutorService pool = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS, new LinkedBlockingQueue<>(4),
                r -> new Thread(r, "inv-posting-test"));
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = List.of(pool.submit(() -> outSix(start)), pool.submit(() -> outSix(start)));
            start.countDown();
            int ok = 0;
            for (Future<Boolean> f : results) {
                ok += f.get(30, TimeUnit.SECONDS) ? 1 : 0;
            }
            assertEquals(1, ok);
            assertEquals(4, balance(k)[0]);
        } finally {
            pool.shutdownNow();
        }
    }

    private boolean outSix(CountDownLatch start) throws InterruptedException {
        start.await();
        TenantContext.setTenantId(TENANT);
        try {
            Loc k = jdbc.queryForObject("select warehouse_id, batch_id from inv_balance where tenant_id = ? limit 1",
                    (rs, i) -> new Loc(rs.getLong(1), PRODUCT, rs.getLong(2), InvConstants.GOOD), TENANT);
            post(Line.of(k, InvConstants.OP_OUT, 6, null, OUT));
            return true;
        } catch (BizException e) {
            return false;
        } finally {
            TenantContext.clear();
        }
    }
}
