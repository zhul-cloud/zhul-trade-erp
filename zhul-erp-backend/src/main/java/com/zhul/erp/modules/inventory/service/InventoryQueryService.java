package com.zhul.erp.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.dto.InvMoveVO;
import com.zhul.erp.modules.inventory.dto.LedgerQuery;
import com.zhul.erp.modules.inventory.dto.LedgerRowVO;
import com.zhul.erp.modules.inventory.dto.MoveQuery;
import com.zhul.erp.modules.inventory.dto.StockBatchVO;
import com.zhul.erp.modules.inventory.dto.StockQuery;
import com.zhul.erp.modules.inventory.dto.StockRowVO;
import com.zhul.erp.modules.inventory.dto.StockSummaryVO;
import com.zhul.erp.modules.inventory.entity.InvBalanceDO;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.entity.InvMoveDO;
import com.zhul.erp.modules.inventory.entity.InvReservationDO;
import com.zhul.erp.modules.inventory.entity.InvWarehouseDO;
import com.zhul.erp.modules.inventory.repository.InvBalanceMapper;
import com.zhul.erp.modules.inventory.repository.InvBatchMapper;
import com.zhul.erp.modules.inventory.repository.InvMoveMapper;
import com.zhul.erp.modules.inventory.repository.InvReservationMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 库存查询、库存流水、库存台账（spec warehouse/inventory）。只读。
 * 汇总用 JdbcTemplate 手写 SQL，不经过租户拦截器，每条 SQL 都显式带 tenant_id 条件。
 * 金额：库存金额 = Σ 结存 × 批次人民币单价（HALF_UP 2 位）；台账金额为流水金额（已按 2 位舍入）的合计。
 */
@Service
@RequiredArgsConstructor
public class InventoryQueryService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_LIMIT = 5000;
    private static final int KEYWORD_PRODUCT_LIMIT = 500;

    private final JdbcTemplate jdbc;
    private final InventoryWarehouses warehouses;
    private final InventoryProducts products;
    private final InventoryMoves moves;
    private final InvBalanceMapper balanceMapper;
    private final InvBatchMapper batchMapper;
    private final InvReservationMapper reservationMapper;
    private final InvMoveMapper moveMapper;

    // ---------------------------------------------------------------- 库存查询

    /** 按商品 × 仓库汇总的 SQL 与参数；筛选结果必为空时返回 null */
    private record Grouped(String sql, List<Object> args) {
    }

    private Grouped stocksSql(StockQuery q) {
        int tenant = PiStore.tenantId();
        StringBuilder where = new StringBuilder(" WHERE b.tenant_id = ?");
        List<Object> args = new ArrayList<>(List.of(tenant));
        List<Long> whIds = warehouseFilter(q.getWarehouseId(), q.getWarehouseScope());
        if (whIds != null) {
            if (whIds.isEmpty()) {
                return null;
            }
            where.append(" AND b.warehouse_id IN (").append(marks(whIds.size())).append(')');
            args.addAll(whIds);
        }
        List<Long> productIds = keywordProducts(q.getKeyword());
        if (productIds != null) {
            if (productIds.isEmpty()) {
                return null;
            }
            where.append(" AND b.product_id IN (").append(marks(productIds.size())).append(')');
            args.addAll(productIds);
        }
        StringBuilder having = new StringBuilder(" HAVING SUM(b.on_hand) > 0");
        if (Boolean.TRUE.equals(q.getOnlyAvailable())) {
            having.append(" AND SUM(CASE WHEN b.stock_status = 1 THEN b.on_hand - b.reserved ELSE 0 END) > 0");
        }
        if (Boolean.TRUE.equals(q.getOnlyDefective())) {
            having.append(" AND SUM(CASE WHEN b.stock_status = 2 THEN b.on_hand ELSE 0 END) > 0");
        }
        String grouped = "SELECT b.product_id, b.warehouse_id,"
                + " SUM(CASE WHEN b.stock_status = 1 THEN b.on_hand ELSE 0 END) good,"
                + " SUM(CASE WHEN b.stock_status = 2 THEN b.on_hand ELSE 0 END) defective,"
                + " SUM(CASE WHEN b.stock_status = 1 THEN b.on_hand - b.reserved ELSE 0 END) available,"
                + " SUM(b.on_hand * COALESCE(t.unit_price_cny, 0)) amount, MAX(b.last_move_time) last_move"
                + " FROM inv_balance b LEFT JOIN inv_batch t ON t.id = b.batch_id AND t.tenant_id = b.tenant_id"
                + where + " GROUP BY b.product_id, b.warehouse_id" + having;
        return new Grouped(grouped, args);
    }

    public PageResult<StockRowVO> stocks(StockQuery q) {
        int tenant = PiStore.tenantId();
        Grouped g = stocksSql(q);
        if (g == null) {
            return PageResult.of(0L, List.of());
        }
        String grouped = g.sql();
        List<Object> args = g.args();
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + grouped + ") x", Long.class, args.toArray());
        if (total == null || total == 0) {
            return PageResult.of(0L, List.of());
        }
        int size = size(q.getPageSize());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((long) (page(q.getPage()) - 1) * size);
        pageArgs.add(size);
        List<StockRowVO> rows = jdbc.query(grouped + " ORDER BY last_move DESC, b.product_id DESC, b.warehouse_id LIMIT ?, ?", (rs, i) -> {
            StockRowVO v = new StockRowVO();
            v.setProductId(rs.getLong("product_id"));
            v.setWarehouseId(rs.getLong("warehouse_id"));
            v.setGood(rs.getInt("good"));
            v.setDefective(rs.getInt("defective"));
            v.setAvailable(rs.getInt("available"));
            v.setAmountCny(rs.getBigDecimal("amount").setScale(2, RoundingMode.HALF_UP));
            Timestamp t = rs.getTimestamp("last_move");
            v.setLastMoveTime(t == null ? null : t.toLocalDateTime());
            return v;
        }, pageArgs.toArray());
        fillStockRows(tenant, rows);
        return PageResult.of(total, rows);
    }

    /** 库存查询顶部汇总：与列表同样的筛选；退货待寄出 = 采购退货单的预占 */
    public StockSummaryVO stockSummary(StockQuery q) {
        StockSummaryVO v = new StockSummaryVO();
        Grouped g = stocksSql(q);
        if (g == null) {
            return v;
        }
        jdbc.query("SELECT COALESCE(SUM(amount), 0), COUNT(DISTINCT product_id), COUNT(DISTINCT warehouse_id),"
                + " COALESCE(SUM(available), 0), COALESCE(SUM(defective), 0) FROM (" + g.sql() + ") x", rs -> {
            v.setAmountCny(rs.getBigDecimal(1).setScale(2, RoundingMode.HALF_UP));
            v.setProductCount(rs.getInt(2));
            v.setWarehouseCount(rs.getInt(3));
            v.setAvailable(rs.getInt(4));
            v.setDefective(rs.getInt(5));
        }, g.args().toArray());
        List<Object> args = new ArrayList<>(g.args());
        args.add(PiStore.tenantId());
        args.add(InvConstants.TARGET_RETURN_ITEM);
        Integer pending = jdbc.queryForObject("SELECT COALESCE(SUM(r.quantity), 0) FROM inv_reservation r JOIN (" + g.sql() + ") x"
                + " ON x.product_id = r.product_id AND x.warehouse_id = r.warehouse_id WHERE r.tenant_id = ? AND r.target_type = ?",
                Integer.class, args.toArray());
        v.setReturnPending(pending == null ? 0 : pending);
        return v;
    }

    private void fillStockRows(int tenant, List<StockRowVO> rows) {
        List<Long> pids = rows.stream().map(StockRowVO::getProductId).distinct().toList();
        Map<Long, InventoryProducts.Label> labels = products.labels(pids);
        Map<Long, InvWarehouseDO> whs = warehouses.all().stream().collect(Collectors.toMap(InvWarehouseDO::getId, w -> w));
        Map<String, int[]> reserved = new HashMap<>();
        jdbc.query("SELECT product_id, warehouse_id, target_type, SUM(quantity) q FROM inv_reservation WHERE tenant_id = ?"
                + " AND product_id IN (" + marks(pids.size()) + ") GROUP BY product_id, warehouse_id, target_type", rs -> {
            int[] r = reserved.computeIfAbsent(rs.getLong(1) + ":" + rs.getLong(2), k -> new int[2]);
            r[rs.getInt(3) == InvConstants.TARGET_SO_ITEM ? 0 : 1] += rs.getInt(4);
        }, concat(tenant, pids));
        Map<Long, Integer> inTransit = inTransit(tenant, pids);
        for (StockRowVO v : rows) {
            InventoryProducts.Label l = labels.getOrDefault(v.getProductId(), InventoryProducts.Label.NONE);
            v.setModel(l.model());
            v.setBrand(l.brand());
            v.setCategory(l.category());
            InvWarehouseDO w = whs.get(v.getWarehouseId());
            v.setWarehouseName(w == null ? "" : w.getName());
            v.setWarehouseType(w == null ? null : w.getType());
            int[] r = reserved.getOrDefault(v.getProductId() + ":" + v.getWarehouseId(), new int[2]);
            v.setOrderReserved(r[0]);
            v.setReturnReserved(r[1]);
            boolean own = w != null && w.getType() == InvConstants.WH_OWN;
            v.setInTransit(own ? inTransit.getOrDefault(v.getProductId(), 0) : null);
        }
    }

    /** 在途：未收货、没有直发货代的供应商发货单数量（按商品） */
    private Map<Long, Integer> inTransit(int tenant, List<Long> pids) {
        Map<Long, Integer> out = new HashMap<>();
        if (pids.isEmpty()) {
            return out;
        }
        jdbc.query("SELECT i.product_id, SUM(i.quantity) FROM supplier_shipment_item i JOIN supplier_shipment s ON s.id = i.shipment_id"
                + " WHERE s.tenant_id = ? AND i.tenant_id = ? AND s.status = 1 AND s.deleted_at IS NULL AND i.deleted_at IS NULL"
                + " AND s.direct_forwarder_id IS NULL AND i.product_id IN (" + marks(pids.size()) + ") GROUP BY i.product_id",
                rs -> {
                    out.put(rs.getLong(1), rs.getInt(2));
                }, concat(tenant, List.of(tenant), pids));
        return out;
    }

    /** 展开：某仓库某商品有结存的批次 */
    public List<StockBatchVO> batches(Long warehouseId, Long productId) {
        warehouses.require(warehouseId);
        List<InvBalanceDO> rows = balanceMapper.selectList(new LambdaQueryWrapper<InvBalanceDO>()
                .eq(InvBalanceDO::getTenantId, PiStore.tenantId())
                .eq(InvBalanceDO::getWarehouseId, warehouseId)
                .eq(InvBalanceDO::getProductId, productId)
                .gt(InvBalanceDO::getOnHand, 0)
                .orderByAsc(InvBalanceDO::getBatchId)
                .orderByAsc(InvBalanceDO::getStockStatus));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, InvBatchDO> batches = batchMapper.selectBatchIds(rows.stream().map(InvBalanceDO::getBatchId).distinct().toList())
                .stream().collect(Collectors.toMap(InvBatchDO::getId, b -> b));
        List<InvReservationDO> rs = reservationMapper.selectList(new LambdaQueryWrapper<InvReservationDO>()
                .eq(InvReservationDO::getTenantId, PiStore.tenantId())
                .eq(InvReservationDO::getWarehouseId, warehouseId)
                .eq(InvReservationDO::getProductId, productId)
                .gt(InvReservationDO::getQuantity, 0));
        Map<String, String> nos = moves.targetNos(rs.stream().collect(Collectors.groupingBy(InvReservationDO::getTargetType,
                Collectors.mapping(InvReservationDO::getTargetId, Collectors.toSet()))));
        Map<Long, Map<String, Object>> pos = new HashMap<>();
        List<Long> poItems = batches.values().stream().map(InvBatchDO::getPoItemId).filter(Objects::nonNull).distinct().toList();
        if (!poItems.isEmpty()) {
            jdbc.queryForList("SELECT i.id, o.id po_id, o.po_no, o.supplier_id, COALESCE(s.name, o.shop_name) supplier_name"
                    + " FROM purchase_order_item i JOIN purchase_order o ON o.id = i.po_id AND o.tenant_id = i.tenant_id"
                    + " LEFT JOIN supplier s ON s.id = o.supplier_id AND s.tenant_id = o.tenant_id"
                    + " WHERE i.tenant_id = ? AND i.id IN (" + String.join(",", poItems.stream().map(x -> "?").toList()) + ")",
                    concat(PiStore.tenantId(), poItems)).forEach(r -> pos.put(((Number) r.get("id")).longValue(), r));
        }
        return rows.stream().map(b -> {
            InvBatchDO t = batches.get(b.getBatchId());
            StockBatchVO v = new StockBatchVO();
            Map<String, Object> po = t == null || t.getPoItemId() == null ? null : pos.get(t.getPoItemId());
            if (po != null) {
                v.setPoId(((Number) po.get("po_id")).longValue());
                v.setPoNo((String) po.get("po_no"));
                v.setSupplierId(((Number) po.get("supplier_id")).longValue());
                v.setSupplierName(Objects.toString(po.get("supplier_name"), ""));
            }
            v.setBatchId(b.getBatchId());
            v.setStockStatus(b.getStockStatus());
            v.setStockStatusName(InvConstants.STATUS_NAMES.get(b.getStockStatus()));
            v.setOnHand(b.getOnHand());
            v.setReserved(b.getReserved());
            v.setFree(b.getOnHand() - b.getReserved());
            if (t != null) {
                v.setBatchNo(t.getBatchNo());
                v.setSourceType(t.getSourceType());
                v.setSourceTypeName(InvConstants.BATCH_SOURCE_NAMES.get(t.getSourceType()));
                v.setSourceId(t.getSourceId());
                v.setInDate(t.getInDate());
                v.setGrade(t.getGrade());
                v.setUnitPriceCny(t.getUnitPriceCny());
                v.setAmountCny(t.getUnitPriceCny().multiply(BigDecimal.valueOf(b.getOnHand())).setScale(2, RoundingMode.HALF_UP));
            }
            v.setReservations(rs.stream().filter(r -> Objects.equals(r.getBatchId(), b.getBatchId())
                    && Objects.equals(r.getStockStatus(), b.getStockStatus())).map(r -> {
                StockBatchVO.Reservation x = new StockBatchVO.Reservation();
                x.setTargetType(r.getTargetType());
                x.setTargetId(r.getTargetId());
                x.setDocNo(nos.getOrDefault(r.getTargetType() + ":" + r.getTargetId(), ""));
                x.setQuantity(r.getQuantity());
                return x;
            }).toList());
            return v;
        }).toList();
    }

    // ---------------------------------------------------------------- 库存流水

    public PageResult<InvMoveVO> moves(MoveQuery q) {
        LambdaQueryWrapper<InvMoveDO> w = new LambdaQueryWrapper<InvMoveDO>().eq(InvMoveDO::getTenantId, PiStore.tenantId());
        if (q.getDateFrom() != null) {
            w.ge(InvMoveDO::getMoveTime, q.getDateFrom().atStartOfDay());
        }
        if (q.getDateTo() != null) {
            w.lt(InvMoveDO::getMoveTime, q.getDateTo().plusDays(1).atStartOfDay());
        }
        if (q.getWarehouseId() != null) {
            w.eq(InvMoveDO::getWarehouseId, q.getWarehouseId());
        }
        if (q.getProductId() != null) {
            w.eq(InvMoveDO::getProductId, q.getProductId());
        }
        if (q.getOpType() != null) {
            w.eq(InvMoveDO::getOpType, q.getOpType());
        } else if (!Boolean.TRUE.equals(q.getIncludeReservation())) {
            w.notIn(InvMoveDO::getOpType, List.of(InvConstants.OP_RESERVE, InvConstants.OP_RELEASE));
        }
        if (StringUtils.hasText(q.getBizType())) {
            w.eq(InvMoveDO::getBizType, q.getBizType());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            List<Long> pids = keywordProducts(kw);
            List<Long> batchIds = batchMapper.selectList(new LambdaQueryWrapper<InvBatchDO>().select(InvBatchDO::getId)
                            .eq(InvBatchDO::getTenantId, PiStore.tenantId()).like(InvBatchDO::getBatchNo, kw).last("LIMIT 200"))
                    .stream().map(InvBatchDO::getId).toList();
            w.and(x -> {
                x.like(InvMoveDO::getDocNo, kw);
                if (pids != null && !pids.isEmpty()) {
                    x.or().in(InvMoveDO::getProductId, pids);
                }
                if (!batchIds.isEmpty()) {
                    x.or().in(InvMoveDO::getBatchId, batchIds);
                }
            });
        }
        long total = moveMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        int size = size(q.getPageSize());
        w.last("ORDER BY move_time DESC, id DESC LIMIT " + (long) (page(q.getPage()) - 1) * size + ", " + size);
        return PageResult.of(total, moves.toVos(moveMapper.selectList(w)));
    }

    // ---------------------------------------------------------------- 库存台账

    public PageResult<LedgerRowVO> ledger(LedgerQuery q) {
        return ledger(q, size(q.getPageSize()), page(q.getPage()));
    }

    private PageResult<LedgerRowVO> ledger(LedgerQuery q, int size, int page) {
        int tenant = PiStore.tenantId();
        YearMonth ym = month(q.getMonth());
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
        boolean all = q.getWarehouseId() == null;
        if (!all) {
            warehouses.require(q.getWarehouseId());
        }
        // 入库：入库、调入（全部仓库合计时调入调出抵消，不计）、商品合并转入；出库同理。转入转出（合格↔不良）与预占不计
        String in = all ? "(op_type = 1 OR (op_type = 5 AND biz_type = 'MERGE'))" : "(op_type IN (1, 3) OR (op_type = 5 AND biz_type = 'MERGE'))";
        String out = all ? "(op_type = 2 OR (op_type = 6 AND biz_type = 'MERGE'))" : "(op_type IN (2, 4) OR (op_type = 6 AND biz_type = 'MERGE'))";
        String period = "move_time >= ? AND move_time < ?";
        StringBuilder where = new StringBuilder(" WHERE tenant_id = ? AND op_type NOT IN (7, 8) AND move_time < ?");
        List<Object> whereArgs = new ArrayList<>(List.of(tenant, Timestamp.valueOf(end)));
        if (!all) {
            where.append(" AND warehouse_id = ?");
            whereArgs.add(q.getWarehouseId());
        }
        List<Long> pids = keywordProducts(q.getKeyword());
        if (pids != null) {
            if (pids.isEmpty()) {
                return PageResult.of(0L, List.of());
            }
            where.append(" AND product_id IN (").append(marks(pids.size())).append(')');
            whereArgs.addAll(pids);
        }
        String grouped = "SELECT product_id,"
                + " SUM(CASE WHEN move_time < ? THEN quantity ELSE 0 END) oq,"
                + " SUM(CASE WHEN move_time < ? THEN amount_cny ELSE 0 END) oa,"
                + " SUM(CASE WHEN " + period + " AND " + in + " THEN quantity ELSE 0 END) iq,"
                + " SUM(CASE WHEN " + period + " AND " + in + " THEN amount_cny ELSE 0 END) ia,"
                + " SUM(CASE WHEN " + period + " AND " + out + " THEN -quantity ELSE 0 END) xq,"
                + " SUM(CASE WHEN " + period + " AND " + out + " THEN -amount_cny ELSE 0 END) xa,"
                + " SUM(quantity) cq, SUM(amount_cny) ca"
                + " FROM inv_move" + where + " GROUP BY product_id"
                + " HAVING oq <> 0 OR oa <> 0 OR iq <> 0 OR xq <> 0 OR cq <> 0 OR ca <> 0";
        Timestamp s = Timestamp.valueOf(start);
        Timestamp e = Timestamp.valueOf(end);
        List<Object> args = new ArrayList<>(List.of(s, s, s, e, s, e, s, e, s, e));
        args.addAll(whereArgs);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + grouped + ") x", Long.class, args.toArray());
        if (total == null || total == 0) {
            return PageResult.of(0L, List.of());
        }
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add((long) (page - 1) * size);
        pageArgs.add(size);
        List<LedgerRowVO> rows = jdbc.query(grouped + " ORDER BY product_id DESC LIMIT ?, ?", (rs, i) -> {
            LedgerRowVO v = new LedgerRowVO();
            v.setProductId(rs.getLong("product_id"));
            v.setOpeningQty(rs.getInt("oq"));
            v.setOpeningAmount(money(rs.getBigDecimal("oa")));
            v.setInQty(rs.getInt("iq"));
            v.setInAmount(money(rs.getBigDecimal("ia")));
            v.setOutQty(rs.getInt("xq"));
            v.setOutAmount(money(rs.getBigDecimal("xa")));
            v.setClosingQty(rs.getInt("cq"));
            v.setClosingAmount(money(rs.getBigDecimal("ca")));
            return v;
        }, pageArgs.toArray());
        Map<Long, InventoryProducts.Label> labels = products.labels(rows.stream().map(LedgerRowVO::getProductId).toList());
        String whName = all ? "全部仓库" : warehouses.require(q.getWarehouseId()).getName();
        for (LedgerRowVO v : rows) {
            InventoryProducts.Label l = labels.getOrDefault(v.getProductId(), InventoryProducts.Label.NONE);
            v.setModel(l.model());
            v.setBrand(l.brand());
            v.setCategory(l.category());
            v.setWarehouseId(q.getWarehouseId());
            v.setWarehouseName(whName);
        }
        return PageResult.of(total, rows);
    }

    /** 台账导出 Excel（最多 5000 行） */
    public byte[] exportLedger(LedgerQuery q) {
        List<LedgerRowVO> rows = ledger(q, EXPORT_LIMIT, 1).getRecords();
        String[] head = {"品牌", "型号", "品类", "仓库", "期初数量", "期初金额", "入库数量", "入库金额", "出库数量", "出库金额", "期末数量", "期末金额"};
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("库存台账 " + month(q.getMonth()));
            Row h = sheet.createRow(0);
            for (int i = 0; i < head.length; i++) {
                h.createCell(i).setCellValue(head[i]);
            }
            int r = 1;
            for (LedgerRowVO v : rows) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(v.getBrand());
                row.createCell(1).setCellValue(v.getModel());
                row.createCell(2).setCellValue(v.getCategory());
                row.createCell(3).setCellValue(v.getWarehouseName());
                row.createCell(4).setCellValue(v.getOpeningQty());
                row.createCell(5).setCellValue(v.getOpeningAmount().doubleValue());
                row.createCell(6).setCellValue(v.getInQty());
                row.createCell(7).setCellValue(v.getInAmount().doubleValue());
                row.createCell(8).setCellValue(v.getOutQty());
                row.createCell(9).setCellValue(v.getOutAmount().doubleValue());
                row.createCell(10).setCellValue(v.getClosingQty());
                row.createCell(11).setCellValue(v.getClosingAmount().doubleValue());
            }
            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("库存台账导出失败", ex);
        }
    }

    public static YearMonth month(String m) {
        if (!StringUtils.hasText(m)) {
            return YearMonth.from(LocalDate.now());
        }
        try {
            return YearMonth.parse(m.trim());
        } catch (DateTimeParseException e) {
            throw new BizException("月份格式应为 2026-11", e);
        }
    }

    // ---------------------------------------------------------------- 工具

    /** 仓库筛选：null 表示全部仓库 */
    private List<Long> warehouseFilter(Long warehouseId, String scope) {
        if (warehouseId != null) {
            return List.of(warehouses.require(warehouseId).getId());
        }
        if ("own".equals(scope)) {
            return List.of(warehouses.own().getId());
        }
        if ("forwarders".equals(scope)) {
            return warehouses.all().stream().filter(w -> w.getType() == InvConstants.WH_FORWARDER).map(InvWarehouseDO::getId).toList();
        }
        return null;
    }

    /** 关键词匹配的商品（型号或品牌）；没有关键词返回 null。商品是平台共享数据，不带租户条件 */
    private List<Long> keywordProducts(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String like = "%" + keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.queryForList("SELECT p.id FROM product p LEFT JOIN product_brand b ON b.id = p.brand_id"
                + " WHERE p.mpn_display LIKE ? OR p.mpn_raw LIKE ? OR b.brand_name LIKE ? LIMIT " + KEYWORD_PRODUCT_LIMIT,
                Long.class, like, like, like);
    }

    private static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    private static int size(Integer s) {
        return s == null || s <= 0 ? 20 : Math.min(s, MAX_PAGE_SIZE);
    }

    private static int page(Integer p) {
        return Math.max(1, p == null ? 1 : p);
    }

    private static String marks(int n) {
        return String.join(",", java.util.Collections.nCopies(n, "?"));
    }

    private static Object[] concat(Object first, List<?>... rest) {
        List<Object> out = new ArrayList<>();
        out.add(first);
        for (List<?> r : rest) {
            out.addAll(r);
        }
        return out.toArray();
    }
}
