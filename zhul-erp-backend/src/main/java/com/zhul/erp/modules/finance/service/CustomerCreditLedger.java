package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.entity.CustomerCreditConsumptionDO;
import com.zhul.erp.modules.finance.entity.CustomerCreditEntryDO;
import com.zhul.erp.modules.finance.repository.CustomerCreditConsumptionMapper;
import com.zhul.erp.modules.finance.repository.CustomerCreditEntryMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 客户余额流水（spec finance/customer-credit）：余额只通过流水变化，可用余额 = 账户全部流水之和。
 * <ul>
 *   <li>转入（售后退款、代收、多收、正数调整、释放）是一笔「批次」，人民币价值生成时锁定；</li>
 *   <li>转出（抵扣占用、提现、负数调整、冲销）按批次的 lot_time 先进先出消耗，消耗明细记录每笔取了多少价值；</li>
 *   <li>释放：把消耗明细标为已释放，被消耗批次的剩余随之恢复（先进先出顺序与价值不变），同时记一条「抵扣释放」流水
 *       使流水之和仍等于可用余额；释放流水本身不是批次；</li>
 *   <li>冲销：来源单据作废时整笔撤回一笔还没被消耗的转入。</li>
 * </ul>
 * 精度：原币与人民币 HALF_UP 两位；一笔批次被消耗完时取剩余人民币，避免尾差累积。
 * 并发：同一客户的余额操作先锁客户行（SELECT … FOR UPDATE），在锁内重新计算可用余额；事务边界由调用方的 Service 声明。
 */
@Component
@RequiredArgsConstructor
public class CustomerCreditLedger {

    private static final MathContext MC = MathContext.DECIMAL128;

    private final CustomerCreditEntryMapper entryMapper;
    private final CustomerCreditConsumptionMapper consumptionMapper;
    private final CustomerMapper customerMapper;
    private final CurrentUserResolver currentUser;

    /** 余额账户：客户 + 币种 + 事业部（NULL 表示公司没有设置事业部或公共） */
    public record Account(Long customerId, String currencyCode, Integer businessUnitId) {
    }

    /** 来源单据 */
    public record Source(String type, Long id, String no) {
    }

    /** 转入：金额大于 0，人民币价值由调用方按口径算好后锁定 */
    @Transactional(rollbackFor = Exception.class)
    public CustomerCreditEntryDO credit(Account a, int type, BigDecimal amount, BigDecimal amountCny, Source s, String remark) {
        BigDecimal amt = money(amount);
        if (amt.signum() <= 0) {
            throw new BizException("转入金额必须大于 0");
        }
        lock(a.customerId());
        return insert(a, type, amt, money(amountCny), LocalDateTime.now(), s, null, remark);
    }

    /** 转出：按先进先出消耗同一账户的批次；可用余额不足时抛出「可用余额只有 …」 */
    @Transactional(rollbackFor = Exception.class)
    public CustomerCreditEntryDO consume(Account a, int type, BigDecimal amount, Source s, String remark) {
        return consume(a, type, amount, s, remark, null);
    }

    /** 转出：先用 preferredLotId 这一笔（如「退回客户」时刚转入的多收），不够再按先进先出 */
    @Transactional(rollbackFor = Exception.class)
    public CustomerCreditEntryDO consume(Account a, int type, BigDecimal amount, Source s, String remark, Long preferredLotId) {
        BigDecimal amt = money(amount);
        if (amt.signum() <= 0) {
            throw new BizException("金额必须大于 0");
        }
        lock(a.customerId());
        List<Lot> lots = new ArrayList<>(lots(a));
        if (preferredLotId != null) {
            lots.sort(Comparator.comparing((Lot l) -> !l.entry().getId().equals(preferredLotId)));
        }
        BigDecimal available = lots.stream().map(Lot::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (available.compareTo(amt) < 0) {
            throw new BizException("可用余额只有 " + a.currencyCode() + " " + fmt(available) + occupiedHint(a));
        }
        CustomerCreditEntryDO out = insert(a, type, amt.negate(), BigDecimal.ZERO, LocalDateTime.now(), s, null, remark);
        BigDecimal left = amt;
        BigDecimal cny = BigDecimal.ZERO;
        for (Lot lot : lots) {
            if (left.signum() <= 0) {
                break;
            }
            if (lot.remaining().signum() <= 0) {
                continue;
            }
            BigDecimal piece = left.min(lot.remaining());
            BigDecimal pieceCny = piece.compareTo(lot.remaining()) == 0 ? lot.remainingCny()
                    : money(lot.remainingCny().multiply(piece, MC).divide(lot.remaining(), MC));
            CustomerCreditConsumptionDO c = new CustomerCreditConsumptionDO();
            c.setTenantId(PiStore.tenantId());
            c.setOutEntryId(out.getId());
            c.setInEntryId(lot.entry().getId());
            c.setAmount(piece);
            c.setAmountCny(pieceCny);
            c.setReleased(0);
            consumptionMapper.insert(c);
            left = left.subtract(piece);
            cny = cny.add(pieceCny);
        }
        out.setAmountCny(cny.negate());
        entryMapper.updateById(out);
        return out;
    }

    /** 释放一笔转出（PI 关闭、作废，抵扣作废）：按消耗明细原样退回，已释放的不重复释放 */
    @Transactional(rollbackFor = Exception.class)
    public List<CustomerCreditEntryDO> release(CustomerCreditEntryDO out, int type, String remark) {
        lock(out.getCustomerId());
        List<CustomerCreditConsumptionDO> rows = consumptionMapper.selectList(new LambdaQueryWrapper<CustomerCreditConsumptionDO>()
                .eq(CustomerCreditConsumptionDO::getOutEntryId, out.getId())
                .eq(CustomerCreditConsumptionDO::getReleased, 0)
                .isNull(CustomerCreditConsumptionDO::getDeletedAt));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, CustomerCreditEntryDO> ins = byId(rows.stream().map(CustomerCreditConsumptionDO::getInEntryId).toList());
        Account a = account(out);
        Source s = new Source(out.getSourceType(), out.getSourceId(), out.getSourceNo());
        List<CustomerCreditEntryDO> created = new ArrayList<>(rows.size());
        for (CustomerCreditConsumptionDO c : rows) {
            CustomerCreditEntryDO in = ins.get(c.getInEntryId());
            created.add(insert(a, type, c.getAmount(), c.getAmountCny(), in.getLotTime(), s, out.getId(), remark));
        }
        consumptionMapper.update(null, new LambdaUpdateWrapper<CustomerCreditConsumptionDO>()
                .in(CustomerCreditConsumptionDO::getId, rows.stream().map(CustomerCreditConsumptionDO::getId).toList())
                .set(CustomerCreditConsumptionDO::getReleased, 1));
        return created;
    }

    /** 冲销一笔还没被使用的转入（来源单据作废）；已被消耗时提示占用它的单据 */
    @Transactional(rollbackFor = Exception.class)
    public CustomerCreditEntryDO reverse(CustomerCreditEntryDO in, String remark) {
        lock(in.getCustomerId());
        CustomerCreditEntryDO fresh = entryMapper.selectById(in.getId());
        BigDecimal used = consumed(List.of(fresh.getId())).getOrDefault(fresh.getId(), Totals.ZERO).amount();
        if (used.signum() > 0) {
            throw new BizException("余额已被 " + usedBy(fresh) + " 使用，请先处理该单据");
        }
        CustomerCreditEntryDO out = insert(account(fresh), FinanceConstants.CREDIT_REVERSE, fresh.getAmount().negate(),
                fresh.getAmountCny().negate(), LocalDateTime.now(),
                new Source(fresh.getSourceType(), fresh.getSourceId(), fresh.getSourceNo()), fresh.getId(), remark);
        CustomerCreditConsumptionDO c = new CustomerCreditConsumptionDO();
        c.setTenantId(PiStore.tenantId());
        c.setOutEntryId(out.getId());
        c.setInEntryId(fresh.getId());
        c.setAmount(fresh.getAmount());
        c.setAmountCny(fresh.getAmountCny());
        c.setReleased(0);
        consumptionMapper.insert(c);
        return out;
    }

    /** 账户的可用余额（原币）与人民币价值 */
    public Totals balance(Account a) {
        List<CustomerCreditEntryDO> rows = entryMapper.selectList(accountWrapper(a));
        return new Totals(rows.stream().map(CustomerCreditEntryDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                rows.stream().map(CustomerCreditEntryDO::getAmountCny).reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** 一笔转入是否还没被使用（没有未释放的消耗） */
    public boolean unused(CustomerCreditEntryDO in) {
        return consumed(List.of(in.getId())).getOrDefault(in.getId(), Totals.ZERO).amount().signum() == 0;
    }

    /** 消耗了某笔转入的第一张单据编号（提示用） */
    public String usedBy(CustomerCreditEntryDO in) {
        List<CustomerCreditConsumptionDO> rows = consumptionMapper.selectList(new LambdaQueryWrapper<CustomerCreditConsumptionDO>()
                .eq(CustomerCreditConsumptionDO::getInEntryId, in.getId())
                .eq(CustomerCreditConsumptionDO::getReleased, 0)
                .isNull(CustomerCreditConsumptionDO::getDeletedAt)
                .orderByAsc(CustomerCreditConsumptionDO::getId));
        for (CustomerCreditConsumptionDO c : rows) {
            CustomerCreditEntryDO out = entryMapper.selectById(c.getOutEntryId());
            if (out != null && out.getEntryType() != FinanceConstants.CREDIT_REVERSE) {
                return out.getSourceNo().isEmpty() ? FinanceConstants.CREDIT_TYPE_NAMES.get(out.getEntryType()) : out.getSourceNo();
            }
        }
        return "其他单据";
    }

    public CustomerCreditEntryDO require(Long id) {
        CustomerCreditEntryDO e = id == null ? null : entryMapper.selectById(id);
        if (e == null || e.getDeletedAt() != null || !Objects.equals(e.getTenantId(), PiStore.tenantId())) {
            throw new BizException("余额流水不存在");
        }
        return e;
    }

    /** 锁客户行：同一客户的余额操作排队执行 */
    public void lock(Long customerId) {
        CustomerDO c = customerMapper.selectOne(new LambdaQueryWrapper<CustomerDO>()
                .eq(CustomerDO::getId, customerId)
                .eq(CustomerDO::getTenantId, PiStore.tenantId())
                .last("FOR UPDATE"));
        if (c == null) {
            throw new BizException("客户不存在");
        }
    }

    public static Account account(CustomerCreditEntryDO e) {
        return new Account(e.getCustomerId(), e.getCurrencyCode(), e.getBusinessUnitId());
    }

    public record Totals(BigDecimal amount, BigDecimal amountCny) {
        public static final Totals ZERO = new Totals(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    // ---------------------------------------------------------------- 内部

    /** 一笔批次与它的剩余（原币、人民币） */
    private record Lot(CustomerCreditEntryDO entry, BigDecimal remaining, BigDecimal remainingCny) {
    }

    private List<Lot> lots(Account a) {
        List<CustomerCreditEntryDO> ins = entryMapper.selectList(accountWrapper(a).gt(CustomerCreditEntryDO::getAmount, 0)
                .ne(CustomerCreditEntryDO::getEntryType, FinanceConstants.CREDIT_RELEASE));
        Map<Long, Totals> used = consumed(ins.stream().map(CustomerCreditEntryDO::getId).toList());
        return ins.stream()
                .sorted(Comparator.comparing(CustomerCreditEntryDO::getLotTime).thenComparing(CustomerCreditEntryDO::getId))
                .map(e -> {
                    Totals u = used.getOrDefault(e.getId(), Totals.ZERO);
                    return new Lot(e, e.getAmount().subtract(u.amount()), e.getAmountCny().subtract(u.amountCny()));
                })
                .toList();
    }

    /** 每笔转入还没释放的消耗合计 */
    private Map<Long, Totals> consumed(List<Long> inIds) {
        if (inIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Totals> out = new HashMap<>();
        consumptionMapper.selectList(new LambdaQueryWrapper<CustomerCreditConsumptionDO>()
                        .in(CustomerCreditConsumptionDO::getInEntryId, inIds)
                        .eq(CustomerCreditConsumptionDO::getReleased, 0)
                        .isNull(CustomerCreditConsumptionDO::getDeletedAt))
                .forEach(c -> out.merge(c.getInEntryId(), new Totals(c.getAmount(), c.getAmountCny()),
                        (x, y) -> new Totals(x.amount().add(y.amount()), x.amountCny().add(y.amountCny()))));
        return out;
    }

    private Map<Long, CustomerCreditEntryDO> byId(List<Long> ids) {
        return entryMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(CustomerCreditEntryDO::getId, e -> e));
    }

    private String occupiedHint(Account a) {
        List<CustomerCreditEntryDO> occupied = entryMapper.selectList(accountWrapper(a)
                .eq(CustomerCreditEntryDO::getEntryType, FinanceConstants.CREDIT_OCCUPY)
                .orderByDesc(CustomerCreditEntryDO::getId)
                .last("LIMIT 1"));
        return occupied.isEmpty() ? "" : "，其余被 " + occupied.get(0).getSourceNo() + " 等占用";
    }

    private LambdaQueryWrapper<CustomerCreditEntryDO> accountWrapper(Account a) {
        LambdaQueryWrapper<CustomerCreditEntryDO> w = new LambdaQueryWrapper<CustomerCreditEntryDO>()
                .eq(CustomerCreditEntryDO::getTenantId, PiStore.tenantId())
                .eq(CustomerCreditEntryDO::getCustomerId, a.customerId())
                .eq(CustomerCreditEntryDO::getCurrencyCode, a.currencyCode())
                .isNull(CustomerCreditEntryDO::getDeletedAt);
        if (a.businessUnitId() == null) {
            w.isNull(CustomerCreditEntryDO::getBusinessUnitId);
        } else {
            w.eq(CustomerCreditEntryDO::getBusinessUnitId, a.businessUnitId());
        }
        return w;
    }

    private CustomerCreditEntryDO insert(Account a, int type, BigDecimal amount, BigDecimal amountCny, LocalDateTime lotTime,
                                         Source s, Long related, String remark) {
        CustomerCreditEntryDO e = new CustomerCreditEntryDO();
        e.setTenantId(PiStore.tenantId());
        e.setCustomerId(a.customerId());
        e.setCurrencyCode(a.currencyCode());
        e.setBusinessUnitId(a.businessUnitId());
        e.setEntryType(type);
        e.setAmount(amount);
        e.setAmountCny(amountCny);
        e.setLotTime(lotTime);
        e.setSourceType(s == null || s.type() == null ? "" : s.type());
        e.setSourceId(s == null ? null : s.id());
        e.setSourceNo(s == null || s.no() == null ? "" : s.no());
        e.setRelatedEntryId(related);
        String r = remark == null ? "" : remark.trim();
        e.setRemark(r.length() > 500 ? r.substring(0, 500) : r);
        e.setOperatorId(currentUser.resolve());
        entryMapper.insert(e);
        return e;
    }

    public static BigDecimal money(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(2, RoundingMode.HALF_UP);
    }

    public static String fmt(BigDecimal v) {
        return String.format("%,.2f", v);
    }
}
