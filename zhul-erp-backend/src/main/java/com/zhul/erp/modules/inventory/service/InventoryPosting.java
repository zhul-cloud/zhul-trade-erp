package com.zhul.erp.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvBalanceDO;
import com.zhul.erp.modules.inventory.entity.InvBatchDO;
import com.zhul.erp.modules.inventory.entity.InvMoveDO;
import com.zhul.erp.modules.inventory.entity.InvReservationDO;
import com.zhul.erp.modules.inventory.entity.InvWarehouseDO;
import com.zhul.erp.modules.inventory.repository.InvBalanceMapper;
import com.zhul.erp.modules.inventory.repository.InvBatchMapper;
import com.zhul.erp.modules.inventory.repository.InvMoveMapper;
import com.zhul.erp.modules.inventory.repository.InvReservationMapper;
import com.zhul.erp.modules.inventory.repository.InvWarehouseMapper;
import com.zhul.erp.modules.product.entity.ProductDO;
import com.zhul.erp.modules.product.repository.ProductMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * 库存过账服务（openspec inventory-foundation 决策 2）：只给业务服务调用，在调用方事务里执行。
 * 每次过账：按余额键（仓库 → 商品 → 批次 → 状态）的固定顺序加锁，先算出每行的净变动再校验，
 * 结存、预占都不能为负，预占不能超过结存；然后写流水、改余额、改预占。任何一步不满足就抛错，整个业务动作回滚。
 * 金额：数量 × 批次人民币单价，HALF_UP 两位；预占、释放不计金额。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryPosting {

    private final InvMoveMapper moveMapper;
    private final InvBalanceMapper balanceMapper;
    private final InvReservationMapper reservationMapper;
    private final InvBatchMapper batchMapper;
    private final InvWarehouseMapper warehouseMapper;
    private final ProductMapper productMapper;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;

    /** 已并入其他商品的商品 → 最终的主商品（沿并入链找到底）；没并入的不在结果里 */
    Map<Long, Long> currentProducts(java.util.Collection<Long> productIds) {
        Map<Long, Long> out = new HashMap<>();
        List<Long> ids = productIds.stream().filter(Objects::nonNull).distinct().toList();
        for (int depth = 0; depth < 5 && !ids.isEmpty(); depth++) {
            List<Long> next = new ArrayList<>();
            for (com.zhul.erp.modules.product.entity.ProductDO p : productMapper.selectBatchIds(ids)) {
                if (p.getMergedIntoId() != null) {
                    next.add(p.getMergedIntoId());
                    out.replaceAll((k, v) -> v.equals(p.getId()) ? p.getMergedIntoId() : v);
                    out.putIfAbsent(p.getId(), p.getMergedIntoId());
                }
            }
            ids = next;
        }
        return out;
    }

    /** 余额键 */
    public record Loc(long warehouseId, long productId, long batchId, int status) {
    }

    private static final Comparator<Loc> LOCK_ORDER = Comparator.comparingLong(Loc::warehouseId)
            .thenComparingLong(Loc::productId).thenComparingLong(Loc::batchId).thenComparingInt(Loc::status);

    /** 来源单据与业务类型 */
    public record Doc(String bizType, String bizLabel, String docType, Long docId, String docNo, Long docItemId) {
        public static Doc of(String bizType, String docType, Long docId, String docNo) {
            return new Doc(bizType, "", docType, docId, docNo, null);
        }

        public Doc item(Long itemId) {
            return new Doc(bizType, bizLabel, docType, docId, docNo, itemId);
        }

        public Doc label(String l) {
            return new Doc(bizType, l == null ? "" : l, docType, docId, docNo, docItemId);
        }

        public Doc biz(String b) {
            return new Doc(b, bizLabel, docType, docId, docNo, docItemId);
        }
    }

    /** 预占对象 */
    public record Target(int type, long id) {
        public static Target soItem(Long id) {
            return id == null ? null : new Target(InvConstants.TARGET_SO_ITEM, id);
        }

        public static Target returnItem(Long id) {
            return id == null ? null : new Target(InvConstants.TARGET_RETURN_ITEM, id);
        }
    }

    /**
     * 一笔流水：qty 为正数，方向由操作类型决定。target 不为空时：预占、释放改该对象的预占；
     * 入库、调入、转入同时预占给该对象；出库、调出、转出出的是该对象预占的货，同时减少预占。
     */
    public record Line(Loc loc, int op, int qty, Target target, Doc doc, Long reverseOf) {
        public Line {
            if (qty <= 0) {
                throw new IllegalArgumentException("过账数量必须为正数");
            }
            if (InvConstants.reservationOnly(op) && target == null) {
                throw new IllegalArgumentException("预占、释放必须指定预占对象");
            }
        }

        public static Line of(Loc loc, int op, int qty, Target target, Doc doc) {
            return new Line(loc, op, qty, target, doc, null);
        }

        int signed() {
            return InvConstants.positive(op) ? qty : -qty;
        }
    }

    /** 过账；lines 为空时什么都不做。返回写入的流水（与 lines 顺序一致）。 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<InvMoveDO> post(List<Line> lines) {
        if (lines == null || lines.isEmpty()) {
            return List.of();
        }
        int tenantId = PiStore.tenantId();
        // 1) 汇总每个余额键、每个预占的净变动
        Map<Loc, int[]> balanceDelta = new TreeMap<>(LOCK_ORDER);
        Map<ResKey, Integer> resDelta = new LinkedHashMap<>();
        for (Line l : lines) {
            int s = l.signed();
            int[] d = balanceDelta.computeIfAbsent(l.loc(), k -> new int[2]);
            if (!InvConstants.reservationOnly(l.op())) {
                d[0] += s;
            }
            if (l.target() != null) {
                d[1] += s;
                resDelta.merge(new ResKey(l.target(), l.loc()), s, Integer::sum);
            }
        }
        // 2) 按固定顺序锁余额行并校验结果
        Map<Loc, InvBalanceDO> locked = new HashMap<>();
        for (Map.Entry<Loc, int[]> e : balanceDelta.entrySet()) {
            InvBalanceDO b = lockBalance(tenantId, e.getKey());
            int onHand = b.getOnHand() + e.getValue()[0];
            int reserved = b.getReserved() + e.getValue()[1];
            if (onHand < 0 || reserved < 0 || reserved > onHand) {
                throw shortage(e.getKey(), b, e.getValue());
            }
            locked.put(e.getKey(), b);
        }
        List<Map.Entry<ResKey, Integer>> resEntries = new ArrayList<>(resDelta.entrySet());
        resEntries.sort(Comparator.<Map.Entry<ResKey, Integer>, Loc>comparing(x -> x.getKey().loc(), LOCK_ORDER)
                .thenComparingInt(x -> x.getKey().target().type()).thenComparingLong(x -> x.getKey().target().id()));
        for (Map.Entry<ResKey, Integer> e : resEntries) {
            if (e.getValue() == 0) {
                continue;
            }
            InvReservationDO r = lockReservation(tenantId, e.getKey());
            int q = r.getQuantity() + e.getValue();
            if (q < 0) {
                throw new BizException("预占不足：" + describe(e.getKey().loc()) + " 预占 " + r.getQuantity() + "，要减少 " + (-e.getValue()));
            }
            r.setQuantity(q);
            reservationMapper.updateById(r);
        }
        // 3) 写流水、改余额
        LocalDateTime now = LocalDateTime.now();
        Long operator = currentUser.resolve();
        String operatorName = operator == null ? "系统" : Objects.requireNonNullElse(lookups.userNames(List.of(operator)).get(operator), "");
        Map<Long, BigDecimal> prices = batchPrices(balanceDelta.keySet().stream().map(Loc::batchId).toList());
        List<InvMoveDO> out = new ArrayList<>(lines.size());
        for (Line l : lines) {
            InvMoveDO m = new InvMoveDO();
            m.setTenantId(tenantId);
            m.setMoveTime(now);
            m.setWarehouseId(l.loc().warehouseId());
            m.setProductId(l.loc().productId());
            m.setBatchId(l.loc().batchId());
            m.setStockStatus(l.loc().status());
            m.setOpType(l.op());
            m.setQuantity(l.signed());
            m.setTargetType(l.target() == null ? null : l.target().type());
            m.setTargetId(l.target() == null ? null : l.target().id());
            BigDecimal price = prices.getOrDefault(l.loc().batchId(), BigDecimal.ZERO);
            m.setUnitPriceCny(price);
            m.setAmountCny(InvConstants.reservationOnly(l.op()) ? BigDecimal.ZERO.setScale(2)
                    : price.multiply(BigDecimal.valueOf(l.signed())).setScale(2, RoundingMode.HALF_UP));
            m.setBizType(l.doc().bizType());
            m.setBizLabel(Objects.requireNonNullElse(l.doc().bizLabel(), ""));
            m.setDocType(Objects.requireNonNullElse(l.doc().docType(), ""));
            m.setDocId(l.doc().docId());
            m.setDocNo(Objects.requireNonNullElse(l.doc().docNo(), ""));
            m.setDocItemId(l.doc().docItemId());
            m.setReverseOf(l.reverseOf());
            m.setOperatorId(operator);
            m.setOperatorName(operatorName);
            try {
                moveMapper.insert(m);
            } catch (DuplicateKeyException e) {
                throw new BizException("这笔库存流水已经冲销过了，不能重复冲销", e);
            }
            out.add(m);
        }
        for (Map.Entry<Loc, int[]> e : balanceDelta.entrySet()) {
            InvBalanceDO b = locked.get(e.getKey());
            b.setOnHand(b.getOnHand() + e.getValue()[0]);
            b.setReserved(b.getReserved() + e.getValue()[1]);
            b.setLastMoveTime(now);
            b.setVersion(b.getVersion() + 1);
            balanceMapper.updateById(b);
        }
        return out;
    }

    /** 某张单据还没被冲销的流水（不含冲销流水本身）；bizTypes 为空表示全部业务类型 */
    public List<InvMoveDO> activeMoves(String docType, Long docId, Collection<String> bizTypes) {
        LambdaQueryWrapper<InvMoveDO> w = new LambdaQueryWrapper<InvMoveDO>()
                .eq(InvMoveDO::getTenantId, PiStore.tenantId())
                .eq(InvMoveDO::getDocType, docType)
                .eq(InvMoveDO::getDocId, docId)
                .isNull(InvMoveDO::getReverseOf);
        if (bizTypes != null && !bizTypes.isEmpty()) {
            w.in(InvMoveDO::getBizType, bizTypes);
        }
        List<InvMoveDO> moves = moveMapper.selectList(w.orderByAsc(InvMoveDO::getId));
        if (moves.isEmpty()) {
            return moves;
        }
        Set<Long> reversed = new java.util.HashSet<>();
        moveMapper.selectList(new LambdaQueryWrapper<InvMoveDO>()
                        .select(InvMoveDO::getReverseOf)
                        .eq(InvMoveDO::getTenantId, PiStore.tenantId())
                        .in(InvMoveDO::getReverseOf, moves.stream().map(InvMoveDO::getId).toList()))
                .forEach(m -> reversed.add(m.getReverseOf()));
        return moves.stream().filter(m -> !reversed.contains(m.getId())).toList();
    }

    /**
     * 按原流水逐条生成数量相反、操作类型相反的流水，业务类型改为 reverseBiz。
     * 原流水是带预占对象的入库、调入、转入或预占时，只冲掉该对象现在还剩的预占，其余按没有预占的货反向
     * （例如入库后订单已取消、预占已释放，冲销入库时不再去减预占）。
     */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public List<InvMoveDO> reverse(List<InvMoveDO> originals, String reverseBiz) {
        List<Line> lines = new ArrayList<>(originals.size());
        Map<ResKey, Integer> left = new HashMap<>();
        Map<Long, Long> current = currentProducts(originals.stream().map(InvMoveDO::getProductId).toList());
        for (InvMoveDO m : originals) {
            if (m.getReverseOf() != null) {
                throw new BizException("冲销流水不能再冲销");
            }
            Target t = m.getTargetType() == null ? null : new Target(m.getTargetType(), m.getTargetId());
            Doc d = new Doc(reverseBiz, m.getBizLabel(), m.getDocType(), m.getDocId(), m.getDocNo(), m.getDocItemId());
            // 原商品已并入其他商品时，库存已转到主商品上，反向记在主商品
            Loc loc = new Loc(m.getWarehouseId(), current.getOrDefault(m.getProductId(), m.getProductId()), m.getBatchId(),
                    m.getStockStatus());
            int op = InvConstants.opposite(m.getOpType());
            int qty = Math.abs(m.getQuantity());
            if (t == null || m.getQuantity() < 0) {
                lines.add(new Line(loc, op, qty, t, d, m.getId()));
                continue;
            }
            ResKey key = new ResKey(t, loc);
            int still = left.computeIfAbsent(key, k -> reservedOf(k));
            int withTarget = Math.min(qty, still);
            left.put(key, still - withTarget);
            if (withTarget > 0) {
                lines.add(new Line(loc, op, withTarget, t, d, m.getId()));
            }
            if (qty > withTarget && !InvConstants.reservationOnly(op)) {
                lines.add(new Line(loc, op, qty - withTarget, null, d, withTarget > 0 ? null : m.getId()));
            }
        }
        return post(lines);
    }

    private int reservedOf(ResKey k) {
        InvReservationDO r = reservationMapper.selectOne(new LambdaQueryWrapper<InvReservationDO>()
                .eq(InvReservationDO::getTenantId, PiStore.tenantId())
                .eq(InvReservationDO::getTargetType, k.target().type())
                .eq(InvReservationDO::getTargetId, k.target().id())
                .eq(InvReservationDO::getWarehouseId, k.loc().warehouseId())
                .eq(InvReservationDO::getBatchId, k.loc().batchId())
                .eq(InvReservationDO::getStockStatus, k.loc().status()));
        return r == null ? 0 : r.getQuantity();
    }

    /** 某个预占对象当前的预占（数量 > 0），按批次先进先出 */
    public List<InvReservationDO> reservations(Target target) {
        List<InvReservationDO> list = reservationMapper.selectList(new LambdaQueryWrapper<InvReservationDO>()
                .eq(InvReservationDO::getTenantId, PiStore.tenantId())
                .eq(InvReservationDO::getTargetType, target.type())
                .eq(InvReservationDO::getTargetId, target.id())
                .gt(InvReservationDO::getQuantity, 0));
        list.sort(Comparator.comparingLong(InvReservationDO::getBatchId).thenComparingLong(InvReservationDO::getWarehouseId));
        return list;
    }

    // ---------------------------------------------------------------- 内部

    private record ResKey(Target target, Loc loc) {
    }

    private InvBalanceDO lockBalance(int tenantId, Loc k) {
        InvBalanceDO b = selectBalanceForUpdate(tenantId, k);
        if (b != null) {
            return b;
        }
        InvBalanceDO n = new InvBalanceDO();
        n.setTenantId(tenantId);
        n.setWarehouseId(k.warehouseId());
        n.setProductId(k.productId());
        n.setBatchId(k.batchId());
        n.setStockStatus(k.status());
        n.setOnHand(0);
        n.setReserved(0);
        n.setVersion(0);
        try {
            balanceMapper.insert(n);
        } catch (DuplicateKeyException e) {
            // 并发插入了同一行：下面按唯一键再锁一次
            log.debug("库存余额并发插入，改为加锁读取：{}", k);
        }
        b = selectBalanceForUpdate(tenantId, k);
        if (b == null) {
            throw new BizException("库存余额加锁失败，请重试");
        }
        return b;
    }

    private InvBalanceDO selectBalanceForUpdate(int tenantId, Loc k) {
        return balanceMapper.selectOne(new LambdaQueryWrapper<InvBalanceDO>()
                .eq(InvBalanceDO::getTenantId, tenantId)
                .eq(InvBalanceDO::getWarehouseId, k.warehouseId())
                .eq(InvBalanceDO::getProductId, k.productId())
                .eq(InvBalanceDO::getBatchId, k.batchId())
                .eq(InvBalanceDO::getStockStatus, k.status())
                .last("FOR UPDATE"));
    }

    private InvReservationDO lockReservation(int tenantId, ResKey k) {
        InvReservationDO r = selectReservationForUpdate(tenantId, k);
        if (r != null) {
            return r;
        }
        InvReservationDO n = new InvReservationDO();
        n.setTenantId(tenantId);
        n.setTargetType(k.target().type());
        n.setTargetId(k.target().id());
        n.setWarehouseId(k.loc().warehouseId());
        n.setProductId(k.loc().productId());
        n.setBatchId(k.loc().batchId());
        n.setStockStatus(k.loc().status());
        n.setQuantity(0);
        try {
            reservationMapper.insert(n);
        } catch (DuplicateKeyException e) {
            log.debug("库存预占并发插入，改为加锁读取：{}", k);
        }
        r = selectReservationForUpdate(tenantId, k);
        if (r == null) {
            throw new BizException("库存预占加锁失败，请重试");
        }
        return r;
    }

    private InvReservationDO selectReservationForUpdate(int tenantId, ResKey k) {
        return reservationMapper.selectOne(new LambdaQueryWrapper<InvReservationDO>()
                .eq(InvReservationDO::getTenantId, tenantId)
                .eq(InvReservationDO::getTargetType, k.target().type())
                .eq(InvReservationDO::getTargetId, k.target().id())
                .eq(InvReservationDO::getWarehouseId, k.loc().warehouseId())
                .eq(InvReservationDO::getBatchId, k.loc().batchId())
                .eq(InvReservationDO::getStockStatus, k.loc().status())
                .last("FOR UPDATE"));
    }

    private Map<Long, BigDecimal> batchPrices(List<Long> batchIds) {
        Map<Long, BigDecimal> map = new HashMap<>();
        if (!batchIds.isEmpty()) {
            batchMapper.selectBatchIds(batchIds).forEach(b -> map.put(b.getId(), b.getUnitPriceCny()));
        }
        return map;
    }

    private BizException shortage(Loc k, InvBalanceDO b, int[] delta) {
        String where = describe(k);
        if (b.getOnHand() + delta[0] < 0) {
            return new BizException("库存不足：" + where + " 结存 " + b.getOnHand() + "，要出 " + (-delta[0]));
        }
        if (b.getReserved() + delta[1] < 0) {
            return new BizException("预占不足：" + where + " 预占 " + b.getReserved() + "，要减少 " + (-delta[1]));
        }
        int free = b.getOnHand() - b.getReserved();
        return new BizException("库存不足：" + where + " 没有预占的只有 " + Math.max(0, free) + "，预占给订单或退货的货不能出");
    }

    /** 型号 · 仓库 · 批次 · 状态，用于报错 */
    String describe(Loc k) {
        ProductDO p = productMapper.selectById(k.productId());
        InvWarehouseDO w = warehouseMapper.selectById(k.warehouseId());
        InvBatchDO b = batchMapper.selectById(k.batchId());
        return (p == null ? "商品 " + k.productId() : Objects.requireNonNullElse(p.getMpnDisplay(), p.getMpnRaw()))
                + "（" + (w == null ? "" : w.getName()) + "，批次 " + (b == null ? k.batchId() : b.getBatchNo())
                + "，" + InvConstants.STATUS_NAMES.get(k.status()) + "）";
    }
}
