package com.zhul.erp.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.masterdata.constants.CustomerConstants;
import com.zhul.erp.modules.system.dto.BankAccountVO;
import com.zhul.erp.modules.system.dto.SaveBankAccountRequest;
import com.zhul.erp.modules.system.entity.BankAccountDO;
import com.zhul.erp.modules.system.repository.BankAccountMapper;
import com.zhul.erp.modules.system.service.BankAccountService;
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

    private final BankAccountMapper mapper;
    private final LogService logService;

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
        a.setStatus(1);
        // 该币种的第一个启用账户自动成为默认
        a.setIsDefault(defaultFor(a.getCurrencyCode()) == null ? 1 : 0);
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
        apply(a, req);
        if (!oldCurrency.equals(a.getCurrencyCode()) && Objects.equals(a.getIsDefault(), 1)) {
            // 换了币种：原币种的默认交给剩余的第一个启用账户，本账户在新币种里没有默认时才成为默认
            a.setIsDefault(0);
            mapper.updateById(a);
            promoteFirst(oldCurrency);
        }
        if (Objects.equals(a.getStatus(), 1) && defaultFor(a.getCurrencyCode()) == null) {
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
        for (BankAccountDO other : rows()) {
            if (other.getCurrencyCode().equals(a.getCurrencyCode()) && Objects.equals(other.getIsDefault(), 1) && !other.getId().equals(id)) {
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
            boolean othersEnabled = rows().stream().anyMatch(o -> !o.getId().equals(id) && o.getCurrencyCode().equals(a.getCurrencyCode())
                    && Objects.equals(o.getStatus(), 1));
            if (othersEnabled) {
                throw new BizException("这是 " + a.getCurrencyCode() + " 的默认账户，请先把另一个账户设为默认");
            }
            a.setIsDefault(0);
        }
        a.setStatus(enabled ? 1 : 0);
        if (enabled && defaultFor(a.getCurrencyCode()) == null) {
            a.setIsDefault(1);
        }
        mapper.updateById(a);
        logService.recordOperateLog(MENU, enabled ? "启用收款账户" : "停用收款账户", null, logView(a));
    }

    @Override
    public BankAccountDO defaultFor(String currencyCode) {
        return rows().stream().filter(a -> a.getCurrencyCode().equals(currencyCode) && Objects.equals(a.getStatus(), 1)
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

    private void promoteFirst(String currency) {
        rows().stream().filter(o -> o.getCurrencyCode().equals(currency) && Objects.equals(o.getStatus(), 1))
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
