package com.zhul.erp.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.masterdata.constants.CustomerConstants;
import com.zhul.erp.modules.system.dto.BankAccountVO;
import com.zhul.erp.modules.system.dto.SaveBankAccountRequest;
import com.zhul.erp.modules.system.entity.BankAccountDO;
import com.zhul.erp.modules.system.repository.BankAccountMapper;
import com.zhul.erp.modules.system.service.BankAccountService;
import com.zhul.erp.modules.system.service.BusinessUnitService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class BankAccountServiceImpl implements BankAccountService {

    private static final String MENU = "收款账户";
    public static final int TYPE_PUBLIC = 1;
    public static final int TYPE_PRIVATE = 2;

    private final BankAccountMapper mapper;
    private final LogService logService;
    private final BusinessUnitService businessUnitService;

    @Override
    public List<BankAccountVO> list() {
        return rows().stream()
                .sorted(Comparator.comparing(BankAccountDO::getCurrencyCode).thenComparing(BankAccountDO::getId))
                .map(a -> toVo(a, false)).toList();
    }

    @Override
    public List<BankAccountVO> enabledOptions() {
        return rows().stream().filter(a -> Objects.equals(a.getStatus(), 1))
                .sorted(Comparator.comparing(BankAccountDO::getCurrencyCode).thenComparing(a -> -a.getIsDefault()))
                .map(a -> toVo(a, true)).toList();
    }

    @Override
    public BankAccountVO get(Integer id) {
        return toVo(require(id), true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BankAccountVO create(SaveBankAccountRequest req) {
        BankAccountDO a = new BankAccountDO();
        a.setTenantId(tenantId());
        apply(a, req);
        a.setBusinessUnitId(unitOf(req));
        a.setStatus(1);
        // 同一事业部该币种的第一个启用对公账户自动成为默认；对私账户不当默认
        a.setIsDefault(isPublic(a) && defaultFor(a.getBusinessUnitId(), a.getCurrencyCode()) == null ? 1 : 0);
        mapper.insert(a);
        logService.recordOperateLog(MENU, "新增收款账户", null, logView(a));
        return toVo(a, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public BankAccountVO update(Integer id, SaveBankAccountRequest req) {
        BankAccountDO a = require(id);
        Map<String, Object> before = logView(a);
        String oldCurrency = a.getCurrencyCode();
        Integer oldUnit = a.getBusinessUnitId();
        apply(a, req);
        Integer newUnit = unitOf(req);
        boolean moved = !oldCurrency.equals(a.getCurrencyCode()) || !Objects.equals(oldUnit, newUnit);
        if ((moved || !isPublic(a)) && Objects.equals(a.getIsDefault(), 1)) {
            // 换了币种或事业部：原分组的默认交给剩余的第一个启用账户，本账户在新分组里没有默认时才成为默认
            a.setIsDefault(0);
            mapper.updateById(a);
            promoteFirst(oldUnit, oldCurrency, a.getId());
        }
        if (!Objects.equals(oldUnit, newUnit)) {
            mapper.update(null, new LambdaUpdateWrapper<BankAccountDO>()
                    .set(BankAccountDO::getBusinessUnitId, newUnit).eq(BankAccountDO::getId, a.getId()));
            a.setBusinessUnitId(newUnit);
        }
        if (Objects.equals(a.getStatus(), 1) && isPublic(a) && defaultFor(a.getBusinessUnitId(), a.getCurrencyCode()) == null) {
            a.setIsDefault(1);
        }
        mapper.updateById(a);
        logService.recordOperateLog(MENU, "修改收款账户", before, logView(a));
        return toVo(a, true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setDefault(Integer id) {
        BankAccountDO a = require(id);
        if (!Objects.equals(a.getStatus(), 1)) {
            throw new BizException("停用的账户不能设为默认，请先启用");
        }
        if (!isPublic(a)) {
            throw new BizException("对私账户不能设为默认");
        }
        for (BankAccountDO other : rows()) {
            if (sameGroup(other, a.getBusinessUnitId(), a.getCurrencyCode()) && Objects.equals(other.getIsDefault(), 1) && !other.getId().equals(id)) {
                other.setIsDefault(0);
                mapper.updateById(other);
            }
        }
        a.setIsDefault(1);
        mapper.updateById(a);
        logService.recordOperateLog(MENU, "设为默认收款账户", null, logView(a));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setEnabled(Integer id, boolean enabled) {
        BankAccountDO a = require(id);
        if (!enabled && Objects.equals(a.getIsDefault(), 1)) {
            boolean othersEnabled = rows().stream().anyMatch(o -> !o.getId().equals(id) && sameGroup(o, a.getBusinessUnitId(), a.getCurrencyCode())
                    && Objects.equals(o.getStatus(), 1) && isPublic(o));
            if (othersEnabled) {
                throw new BizException("这是 " + a.getCurrencyCode() + " 的默认账户，请先把另一个账户设为默认");
            }
            a.setIsDefault(0);
        }
        a.setStatus(enabled ? 1 : 0);
        if (enabled && isPublic(a) && defaultFor(a.getBusinessUnitId(), a.getCurrencyCode()) == null) {
            a.setIsDefault(1);
        }
        mapper.updateById(a);
        logService.recordOperateLog(MENU, enabled ? "启用收款账户" : "停用收款账户", null, logView(a));
    }

    @Override
    public BankAccountDO defaultFor(Integer businessUnitId, String currencyCode) {
        return rows().stream().filter(a -> sameGroup(a, businessUnitId, currencyCode) && Objects.equals(a.getStatus(), 1)
                && Objects.equals(a.getIsDefault(), 1)).findFirst().orElse(null);
    }

    @Override
    public BankAccountDO requireEnabled(Integer id) {
        BankAccountDO a = id == null ? null : mapper.selectById(id);
        if (a == null || !Objects.equals(a.getTenantId(), tenantId()) || !Objects.equals(a.getStatus(), 1)) {
            throw new BizException("收款账户不存在或已停用，请重新选择");
        }
        return a;
    }

    private void promoteFirst(Integer unitId, String currency, Integer excludeId) {
        rows().stream().filter(o -> !o.getId().equals(excludeId) && sameGroup(o, unitId, currency)
                        && Objects.equals(o.getStatus(), 1) && isPublic(o))
                .min(Comparator.comparing(BankAccountDO::getId))
                .ifPresent(o -> {
                    o.setIsDefault(1);
                    mapper.updateById(o);
                });
    }

    private static void apply(BankAccountDO a, SaveBankAccountRequest r) {
        String currency = r.getCurrencyCode().trim().toUpperCase(Locale.ROOT);
        if (!CustomerConstants.CURRENCIES.contains(currency)) {
            throw new BizException("不支持的币种：" + r.getCurrencyCode());
        }
        a.setCurrencyCode(currency);
        int type = r.getAccountType() == null ? TYPE_PUBLIC : r.getAccountType();
        if (type != TYPE_PUBLIC && type != TYPE_PRIVATE) {
            throw new BizException("账户类型不正确");
        }
        a.setAccountType(type);
        a.setBankName(r.getBankName().trim());
        a.setAccountName(r.getAccountName().trim());
        a.setAccountNo(r.getAccountNo().trim());
        a.setSwiftCode(trim(r.getSwiftCode()).toUpperCase(Locale.ROOT));
        a.setCountry(trim(r.getCountry()));
        a.setBankAddress(trim(r.getBankAddress()));
        a.setBankCode(trim(r.getBankCode()));
        a.setBranchCode(trim(r.getBranchCode()));
        a.setRemark(trim(r.getRemark()));
    }

    /** 默认账户按「事业部 + 币种」分组（spec system/bank-account「收款账户归属事业部」）；公司没有事业部时事业部都为空 */
    private static boolean sameGroup(BankAccountDO a, Integer unitId, String currency) {
        return a.getCurrencyCode().equals(currency) && Objects.equals(a.getBusinessUnitId(), unitId);
    }

    /** 公司设置了事业部时必选，没有时为空 */
    private Integer unitOf(SaveBankAccountRequest req) {
        if (!businessUnitService.enabled()) {
            return null;
        }
        if (req.getBusinessUnitId() == null) {
            throw new BizException("请选择收款账户所属的事业部");
        }
        return businessUnitService.require(req.getBusinessUnitId());
    }

    private static boolean isPublic(BankAccountDO a) {
        return !Objects.equals(a.getAccountType(), TYPE_PRIVATE);
    }

    private BankAccountDO require(Integer id) {
        BankAccountDO a = id == null ? null : mapper.selectById(id);
        if (a == null || !Objects.equals(a.getTenantId(), tenantId())) {
            throw new BizException("收款账户不存在");
        }
        return a;
    }

    private List<BankAccountDO> rows() {
        return mapper.selectList(new LambdaQueryWrapper<BankAccountDO>().eq(BankAccountDO::getTenantId, tenantId()));
    }

    /** 日志里账号脱敏 */
    private static Map<String, Object> logView(BankAccountDO a) {
        return Map.of("currency", a.getCurrencyCode(), "bankName", a.getBankName(), "accountNo", BankAccountService.mask(a.getAccountNo()),
                "default", Objects.equals(a.getIsDefault(), 1), "enabled", Objects.equals(a.getStatus(), 1));
    }

    private static BankAccountVO toVo(BankAccountDO a, boolean full) {
        BankAccountVO vo = new BankAccountVO();
        vo.setId(a.getId());
        vo.setAccountType(a.getAccountType() == null ? TYPE_PUBLIC : a.getAccountType());
        vo.setCurrencyCode(a.getCurrencyCode());
        vo.setBankName(a.getBankName());
        vo.setAccountName(a.getAccountName());
        vo.setAccountNo(full ? a.getAccountNo() : null);
        vo.setAccountNoMasked(BankAccountService.mask(a.getAccountNo()));
        vo.setSwiftCode(a.getSwiftCode());
        vo.setCountry(a.getCountry());
        vo.setBankAddress(a.getBankAddress());
        vo.setBankCode(a.getBankCode());
        vo.setBranchCode(a.getBranchCode());
        vo.setRemark(a.getRemark());
        vo.setBusinessUnitId(a.getBusinessUnitId());
        vo.setIsDefault(Objects.equals(a.getIsDefault(), 1));
        vo.setEnabled(Objects.equals(a.getStatus(), 1));
        vo.setUpdateTime(a.getUpdateTime());
        vo.setUpdateBy(a.getUpdateBy());
        return vo;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private static int tenantId() {
        Integer t = TenantContext.getTenantId();
        return t == null ? 0 : t;
    }
}
