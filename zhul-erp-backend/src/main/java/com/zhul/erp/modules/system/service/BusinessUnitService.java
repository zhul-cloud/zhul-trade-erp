package com.zhul.erp.modules.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.crm.entity.OpportunityDO;
import com.zhul.erp.modules.crm.repository.OpportunityMapper;
import com.zhul.erp.modules.finance.entity.ExpenseDO;
import com.zhul.erp.modules.finance.entity.FxSettlementDO;
import com.zhul.erp.modules.finance.entity.OtherIncomeDO;
import com.zhul.erp.modules.finance.repository.ExpenseMapper;
import com.zhul.erp.modules.finance.repository.FxSettlementMapper;
import com.zhul.erp.modules.finance.repository.OtherIncomeMapper;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryMapper;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.system.dto.BusinessUnitVO;
import com.zhul.erp.modules.system.entity.BankAccountDO;
import com.zhul.erp.modules.system.entity.DepartmentDO;
import com.zhul.erp.modules.system.entity.RoleOrgDO;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.BankAccountMapper;
import com.zhul.erp.modules.system.repository.DepartmentMapper;
import com.zhul.erp.modules.system.repository.RoleOrgMapper;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 事业部（spec system/business-unit）：标记为事业部的部门是独立核算单位，其下级部门都属于它，事业部不能嵌套；
 * 没有标记、也不在事业部之下的部门是公共部门。公司没有任何事业部时，各单据的事业部为空，页面与统计按原口径。
 * 用户所属事业部取主部门所在的事业部；单据的事业部在创建时写入，之后不随人员调动变化。
 */
@Service
@RequiredArgsConstructor
public class BusinessUnitService {

    public static final String CONFIG_MIN_SHARE = "finance.bu.min-share-percent";
    private static final int DEFAULT_MIN_SHARE = 20;
    private static final int MAX_DEPTH = 20;

    private final DepartmentMapper departmentMapper;
    private final UserBasicMapper userBasicMapper;
    private final RoleOrgMapper roleOrgMapper;
    private final SysConfigMapper sysConfigMapper;
    private final DataScopeResolver dataScopeResolver;
    private final CustomerMapper customerMapper;
    private final OpportunityMapper opportunityMapper;
    private final CustomerInquiryMapper inquiryMapper;
    private final QuotationMapper quotationMapper;
    private final ProformaInvoiceMapper piMapper;
    private final SalesOrderMapper orderMapper;
    private final PaymentReceiptMapper receiptMapper;
    private final BankAccountMapper bankAccountMapper;
    private final FxSettlementMapper settlementMapper;
    private final ExpenseMapper expenseMapper;
    private final OtherIncomeMapper incomeMapper;

    /** 事业部：部门编号、名称、开始核算月份 */
    public record Unit(Integer id, String name, YearMonth startMonth) {
    }

    /**
     * 当前用户在统计页能看的事业部。
     *
     * @param units     可选的事业部
     * @param total     能否看全公司合计
     */
    public record StatsScope(List<Unit> units, boolean total) {
    }

    // ---------------------------------------------------------------- 查询

    /** 本公司的事业部，按开始核算月份、部门排序 */
    public List<Unit> units() {
        return departmentMapper.selectList(new LambdaQueryWrapper<DepartmentDO>()
                        .eq(DepartmentDO::getTenantId, tenantId())
                        .eq(DepartmentDO::getIsBusinessUnit, 1)
                        .isNull(DepartmentDO::getDeletedAt)
                        .orderByAsc(DepartmentDO::getBuStartMonth)
                        .orderByAsc(DepartmentDO::getSort)
                        .orderByAsc(DepartmentDO::getId))
                .stream()
                .map(d -> new Unit(d.getId(), d.getName(),
                        d.getBuStartMonth() == null ? null : YearMonth.from(d.getBuStartMonth())))
                .toList();
    }

    /** 公司是否设置了事业部（没有时一切按原口径） */
    public boolean enabled() {
        return departmentMapper.selectCount(new LambdaQueryWrapper<DepartmentDO>()
                .eq(DepartmentDO::getTenantId, tenantId())
                .eq(DepartmentDO::getIsBusinessUnit, 1)
                .isNull(DepartmentDO::getDeletedAt)) > 0;
    }

    /** 部门所在的事业部（自身或最近的上级）；公共部门为 null */
    public Integer unitOfDept(Integer deptId) {
        if (deptId == null || deptId == 0) {
            return null;
        }
        Map<Integer, DepartmentDO> all = deptMap();
        DepartmentDO d = all.get(deptId);
        for (int i = 0; d != null && i < MAX_DEPTH; i++) {
            if (Integer.valueOf(1).equals(d.getIsBusinessUnit())) {
                return d.getId();
            }
            d = all.get(d.getPid());
        }
        return null;
    }

    /** 用户（user_basic.id）主部门所在的事业部 */
    public Integer unitOfUser(Long userId) {
        if (userId == null) {
            return null;
        }
        UserBasicDO u = userBasicMapper.selectById(userId.intValue());
        return u == null ? null : unitOfDept(u.getDeptId());
    }

    /** 客户的事业部（下游单据创建时跟随客户） */
    public Integer unitOfCustomer(Long customerId) {
        if (customerId == null) {
            return null;
        }
        CustomerDO c = customerMapper.selectById(customerId);
        return c == null ? null : c.getBusinessUnitId();
    }

    /**
     * 新客户的事业部：负责人属于事业部时取负责人的；负责人在公共部门时用登记人选的（必选）。
     * 公司没有事业部时为 null。
     */
    public Integer forNewCustomer(Long ownerId, Integer chosen) {
        if (!enabled()) {
            return null;
        }
        Integer own = unitOfUser(ownerId);
        if (own != null) {
            return own;
        }
        if (chosen == null) {
            throw new BizException("负责人不属于任何事业部，请选择客户所属的事业部");
        }
        return require(chosen);
    }

    /**
     * 费用单、其他收入等可选「公共」的单据：chosen 为空表示公共；公司没有事业部时一律为 null。
     */
    public Integer forOptional(Integer chosen) {
        if (chosen == null || !enabled()) {
            return null;
        }
        return require(chosen);
    }

    /** 必须是本公司的事业部 */
    public Integer require(Integer unitId) {
        if (unitId == null || units().stream().noneMatch(u -> u.id().equals(unitId))) {
            throw new BizException("所选事业部不存在");
        }
        return unitId;
    }

    /** 事业部名称（事业部编号 → 名称） */
    public Map<Integer, String> names() {
        Map<Integer, String> out = new HashMap<>();
        units().forEach(u -> out.put(u.id(), u.name()));
        return out;
    }

    /** 本公司的事业部设置与当前用户的可见范围 */
    public BusinessUnitVO view() {
        List<Unit> all = units();
        BusinessUnitVO vo = new BusinessUnitVO();
        vo.setEnabled(!all.isEmpty());
        vo.setUnits(all.stream().map(u -> {
            BusinessUnitVO.Unit x = new BusinessUnitVO.Unit();
            x.setId(u.id());
            x.setName(u.name());
            x.setStartMonth(u.startMonth() == null ? null : u.startMonth().toString());
            return x;
        }).toList());
        vo.setMinSharePercent(minSharePercent());
        StatsScope scope = statsScope();
        vo.setStatsUnitIds(scope.units().stream().map(Unit::id).toList());
        vo.setStatsTotal(scope.total());
        vo.setMyUnitId(unitOfUser(dataScopeResolver.current().selfId()));
        return vo;
    }

    // ---------------------------------------------------------------- 统计可见范围

    /**
     * 统计页可看的事业部（spec access/data-scope「统计按事业部限定可见范围」）：
     * 全部 → 所有事业部 + 合计；自定义部门都在同一事业部内 → 只有它；其他 → 数据范围里的人所在的事业部，不看合计。
     */
    public StatsScope statsScope() {
        List<Unit> all = units();
        DataScope scope = dataScopeResolver.current();
        if (all.isEmpty() || scope.isAll()) {
            return new StatsScope(all, true);
        }
        Set<Integer> ids = new LinkedHashSet<>();
        UserBasicDO self = scope.selfId() == null ? null : userBasicMapper.selectById(scope.selfId().intValue());
        if (scope.type() == DataScope.Type.CUSTOM && self != null && StringUtils.hasText(self.getRoleCode())) {
            Integer single = singleUnitOfRole(self.getRoleCode());
            if (single != null) {
                ids.add(single);
            }
        }
        if (ids.isEmpty()) {
            Set<Long> owners = new HashSet<>(scope.ownerIds());
            if (scope.selfId() != null) {
                owners.add(scope.selfId());
            }
            for (Long owner : owners) {
                Integer u = unitOfUser(owner);
                if (u != null) {
                    ids.add(u);
                }
            }
        }
        return new StatsScope(all.stream().filter(u -> ids.contains(u.id())).toList(), false);
    }

    /**
     * 统计接口实际使用的事业部：null 表示全公司合计。没有指定时，能看合计的看合计，否则看第一个可看的事业部；
     * 指定了看不了的事业部（或看不了合计却要合计）时拒绝。
     */
    public Integer statsUnit(Integer requested) {
        StatsScope s = statsScope();
        if (s.units().isEmpty() && s.total()) {
            return null;
        }
        if (requested == null) {
            if (s.total()) {
                return null;
            }
            if (s.units().isEmpty()) {
                throw new BizException("你的数据范围里没有任何事业部，无权查看统计");
            }
            return s.units().get(0).id();
        }
        if (s.units().stream().noneMatch(u -> u.id().equals(requested))) {
            throw new BizException("无权查看该事业部");
        }
        return requested;
    }

    /** 角色自定义的部门都落在同一个事业部内时返回它；有公共部门或跨事业部时为 null */
    private Integer singleUnitOfRole(String roleCode) {
        Set<String> codes = new HashSet<>();
        roleOrgMapper.selectList(new LambdaQueryWrapper<RoleOrgDO>().eq(RoleOrgDO::getRoleCode, roleCode))
                .forEach(r -> codes.add(r.getOrgCode()));
        if (codes.isEmpty()) {
            return null;
        }
        Set<Integer> units = new HashSet<>();
        for (DepartmentDO d : deptMap().values()) {
            if (codes.contains(d.getCode())) {
                units.add(unitOfDept(d.getId()));
            }
        }
        return units.size() == 1 ? units.iterator().next() : null;
    }

    // ---------------------------------------------------------------- 设置

    /**
     * 标记或取消事业部。不能嵌套；已有单据的不能取消。公司标记第一个事业部时，已有单据全部归入它。
     */
    @Transactional(rollbackFor = Exception.class)
    public void mark(Integer deptId, boolean on, String startMonth) {
        Map<Integer, DepartmentDO> all = deptMap();
        DepartmentDO dept = all.get(deptId);
        if (dept == null) {
            throw new BizException("部门不存在");
        }
        if (!on) {
            if (!Integer.valueOf(1).equals(dept.getIsBusinessUnit())) {
                return;
            }
            if (referenced(deptId)) {
                throw new BizException("该事业部已有单据，不能取消事业部标记");
            }
            departmentMapper.update(null, new LambdaUpdateWrapper<DepartmentDO>()
                    .set(DepartmentDO::getIsBusinessUnit, 0)
                    .set(DepartmentDO::getBuStartMonth, null)
                    .eq(DepartmentDO::getId, deptId));
            return;
        }
        LocalDate start = parseMonth(startMonth);
        for (DepartmentDO p = all.get(dept.getPid()); p != null; p = all.get(p.getPid())) {
            if (Integer.valueOf(1).equals(p.getIsBusinessUnit())) {
                throw new BizException("上级部门「" + p.getName() + "」已是事业部，事业部不能嵌套");
            }
        }
        for (DepartmentDO d : all.values()) {
            if (!d.getId().equals(deptId) && Integer.valueOf(1).equals(d.getIsBusinessUnit()) && isUnder(all, d, deptId)) {
                throw new BizException("下级部门「" + d.getName() + "」已是事业部，事业部不能嵌套");
            }
        }
        boolean first = !enabled();
        dept.setIsBusinessUnit(1);
        dept.setBuStartMonth(start);
        departmentMapper.updateById(dept);
        if (first) {
            backfill(deptId);
        }
    }

    /** 公共费用最低分摊比例（%） */
    public int minSharePercent() {
        List<SysConfigDO> rows = sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, CONFIG_MIN_SHARE)
                .in(SysConfigDO::getTenantId, List.of(0, tenantId()))
                .isNull(SysConfigDO::getDeletedAt));
        return rows.stream()
                .max(Comparator.comparing(SysConfigDO::getTenantId))
                .map(SysConfigDO::getConfigValue)
                .map(v -> {
                    try {
                        return Integer.parseInt(v.trim());
                    } catch (NumberFormatException e) {
                        return DEFAULT_MIN_SHARE;
                    }
                })
                .orElse(DEFAULT_MIN_SHARE);
    }

    /** 改最低分摊比例：0–100 且 × 事业部数不超过 100%；租户第一次改时生成租户自己的设置 */
    @Transactional(rollbackFor = Exception.class)
    public void setMinSharePercent(int percent) {
        if (percent < 0 || percent > 100) {
            throw new BizException("最低分摊比例须在 0% 到 100% 之间");
        }
        int count = Math.max(units().size(), 1);
        if (percent * count > 100) {
            throw new BizException("最低分摊比例 × 事业部数不能超过 100%（当前 " + count + " 个事业部，最多 " + 100 / count + "%）");
        }
        SysConfigDO own = sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, CONFIG_MIN_SHARE)
                .eq(SysConfigDO::getTenantId, tenantId())
                .isNull(SysConfigDO::getDeletedAt)
                .last("LIMIT 1"));
        if (own != null) {
            own.setConfigValue(String.valueOf(percent));
            sysConfigMapper.updateById(own);
            return;
        }
        SysConfigDO row = new SysConfigDO();
        row.setTenantId(tenantId());
        row.setConfigKey(CONFIG_MIN_SHARE);
        row.setConfigName("事业部最低分摊比例（%）");
        row.setConfigValue(String.valueOf(percent));
        row.setConfigType("INTEGER");
        row.setIsBuiltin(1);
        row.setIsEncrypted(0);
        row.setConfigGroup("finance");
        row.setRemark("公共费用按销售额比例分给各事业部时，每个事业部至少承担的比例");
        sysConfigMapper.insert(row);
    }

    // ---------------------------------------------------------------- 内部

    /**
     * 第一个事业部：已有单据全部归入它。属于数据归属的一次性补齐，不改各单据的更新时间（列表按更新时间排序，不应因此打乱）。
     */
    private void backfill(Integer unitId) {
        int t = tenantId();
        customerMapper.update(null, new LambdaUpdateWrapper<CustomerDO>().set(CustomerDO::getBusinessUnitId, unitId)
                .eq(CustomerDO::getTenantId, t).isNull(CustomerDO::getBusinessUnitId));
        opportunityMapper.update(null, new LambdaUpdateWrapper<OpportunityDO>().set(OpportunityDO::getBusinessUnitId, unitId)
                .eq(OpportunityDO::getTenantId, t).isNull(OpportunityDO::getBusinessUnitId));
        inquiryMapper.update(null, new LambdaUpdateWrapper<CustomerInquiryDO>().set(CustomerInquiryDO::getBusinessUnitId, unitId)
                .eq(CustomerInquiryDO::getTenantId, t).isNull(CustomerInquiryDO::getBusinessUnitId));
        quotationMapper.update(null, new LambdaUpdateWrapper<QuotationDO>().set(QuotationDO::getBusinessUnitId, unitId)
                .eq(QuotationDO::getTenantId, t).isNull(QuotationDO::getBusinessUnitId));
        piMapper.update(null, new LambdaUpdateWrapper<ProformaInvoiceDO>().set(ProformaInvoiceDO::getBusinessUnitId, unitId)
                .eq(ProformaInvoiceDO::getTenantId, t).isNull(ProformaInvoiceDO::getBusinessUnitId));
        orderMapper.update(null, new LambdaUpdateWrapper<SalesOrderDO>().set(SalesOrderDO::getBusinessUnitId, unitId)
                .eq(SalesOrderDO::getTenantId, t).isNull(SalesOrderDO::getBusinessUnitId));
        receiptMapper.update(null, new LambdaUpdateWrapper<PaymentReceiptDO>().set(PaymentReceiptDO::getBusinessUnitId, unitId)
                .eq(PaymentReceiptDO::getTenantId, t).isNull(PaymentReceiptDO::getBusinessUnitId));
        bankAccountMapper.update(null, new LambdaUpdateWrapper<BankAccountDO>().set(BankAccountDO::getBusinessUnitId, unitId)
                .eq(BankAccountDO::getTenantId, t).isNull(BankAccountDO::getBusinessUnitId));
        settlementMapper.update(null, new LambdaUpdateWrapper<FxSettlementDO>().set(FxSettlementDO::getBusinessUnitId, unitId)
                .eq(FxSettlementDO::getTenantId, t).isNull(FxSettlementDO::getBusinessUnitId));
        expenseMapper.update(null, new LambdaUpdateWrapper<ExpenseDO>().set(ExpenseDO::getBusinessUnitId, unitId)
                .eq(ExpenseDO::getTenantId, t).isNull(ExpenseDO::getBusinessUnitId));
        incomeMapper.update(null, new LambdaUpdateWrapper<OtherIncomeDO>().set(OtherIncomeDO::getBusinessUnitId, unitId)
                .eq(OtherIncomeDO::getTenantId, t).isNull(OtherIncomeDO::getBusinessUnitId));
    }

    /** 是否有单据归属这个事业部 */
    private boolean referenced(Integer unitId) {
        return customerMapper.selectCount(new LambdaQueryWrapper<CustomerDO>().eq(CustomerDO::getBusinessUnitId, unitId)) > 0
                || orderMapper.selectCount(new LambdaQueryWrapper<SalesOrderDO>().eq(SalesOrderDO::getBusinessUnitId, unitId)) > 0
                || receiptMapper.selectCount(new LambdaQueryWrapper<PaymentReceiptDO>().eq(PaymentReceiptDO::getBusinessUnitId, unitId)) > 0
                || bankAccountMapper.selectCount(new LambdaQueryWrapper<BankAccountDO>().eq(BankAccountDO::getBusinessUnitId, unitId)) > 0
                || expenseMapper.selectCount(new LambdaQueryWrapper<ExpenseDO>().eq(ExpenseDO::getBusinessUnitId, unitId)) > 0
                || incomeMapper.selectCount(new LambdaQueryWrapper<OtherIncomeDO>().eq(OtherIncomeDO::getBusinessUnitId, unitId)) > 0
                || settlementMapper.selectCount(new LambdaQueryWrapper<FxSettlementDO>().eq(FxSettlementDO::getBusinessUnitId, unitId)) > 0;
    }

    private static boolean isUnder(Map<Integer, DepartmentDO> all, DepartmentDO d, Integer ancestorId) {
        DepartmentDO p = all.get(d.getPid());
        for (int i = 0; p != null && i < MAX_DEPTH; i++) {
            if (p.getId().equals(ancestorId)) {
                return true;
            }
            p = all.get(p.getPid());
        }
        return false;
    }

    private Map<Integer, DepartmentDO> deptMap() {
        Map<Integer, DepartmentDO> out = new HashMap<>();
        departmentMapper.selectList(new LambdaQueryWrapper<DepartmentDO>()
                        .eq(DepartmentDO::getTenantId, tenantId())
                        .isNull(DepartmentDO::getDeletedAt))
                .forEach(d -> out.put(d.getId(), d));
        return out;
    }

    private static LocalDate parseMonth(String month) {
        if (!StringUtils.hasText(month)) {
            throw new BizException("请选择开始核算月份");
        }
        try {
            return YearMonth.parse(month.trim()).atDay(1);
        } catch (java.time.format.DateTimeParseException e) {
            throw new BizException("开始核算月份格式不正确，应为 年-月", e);
        }
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }

    /** 用于列表显示：事业部编号 → 名称，空为「公共」 */
    public static String label(Map<Integer, String> names, Integer unitId) {
        return unitId == null ? "公共" : Objects.requireNonNullElse(names.get(unitId), "—");
    }

}
