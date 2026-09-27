package com.zhul.erp.modules.masterdata.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.utils.AesUtils;
import com.zhul.erp.common.utils.SensitiveDataMasker;
import com.zhul.erp.modules.masterdata.constants.SupplierConstants;
import com.zhul.erp.modules.masterdata.dto.SupplierBankAccountRequest;
import com.zhul.erp.modules.masterdata.dto.SupplierBankAccountVO;
import com.zhul.erp.modules.masterdata.entity.SupplierBankAccountDO;
import com.zhul.erp.modules.masterdata.repository.SupplierBankAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 供应商收款账户的保存与读取。保存按 id 合并：带 id 的更新原行、不带的新增、未出现的软删除——
 * 身份证号从不回传明文，只有定位到原行才能在「未重新填写」时沿用原密文。
 */
@Component
@RequiredArgsConstructor
public class SupplierBankAccountSync {

    private final SupplierBankAccountMapper accountMapper;
    private final AesUtils aesUtils;

    /** 按提交的账户列表整体生效；reqs 为 null 时调用方不应调用本方法 */
    public void merge(int tenantId, long supplierId, List<SupplierBankAccountRequest> reqs) {
        if (reqs.size() > SupplierConstants.MAX_ACCOUNTS) {
            throw new BizException("每个供应商最多 " + SupplierConstants.MAX_ACCOUNTS + " 个收款账户");
        }
        long defaults = reqs.stream().filter(SupplierBankAccountRequest::isDefaultAccount).count();
        if (defaults > 1) {
            throw new BizException("只能有一个默认收款账户");
        }
        Map<Long, SupplierBankAccountDO> existing = new HashMap<>(16);
        for (SupplierBankAccountDO row : activeRows(List.of(supplierId))) {
            existing.put(row.getId(), row);
        }
        Set<Long> kept = new HashSet<>(16);
        for (int i = 0; i < reqs.size(); i++) {
            SupplierBankAccountRequest req = reqs.get(i);
            boolean isDefault = defaults == 0 ? i == 0 : req.isDefaultAccount();
            SupplierBankAccountDO row;
            if (req.getId() != null) {
                row = existing.get(req.getId());
                if (row == null || !kept.add(req.getId())) {
                    throw new BizException("收款账户不存在或已被删除，请刷新后重试");
                }
            } else {
                row = new SupplierBankAccountDO();
                row.setTenantId(tenantId);
                row.setSupplierId(supplierId);
                row.setPayeeIdNo("");
            }
            apply(row, req, isDefault, i);
            if (row.getId() == null) {
                accountMapper.insert(row);
            } else {
                accountMapper.updateById(row);
            }
        }
        LocalDateTime now = LocalDateTime.now();
        for (SupplierBankAccountDO row : existing.values()) {
            if (!kept.contains(row.getId())) {
                row.setDeletedAt(now);
                accountMapper.updateById(row);
            }
        }
    }

    private void apply(SupplierBankAccountDO row, SupplierBankAccountRequest req, boolean isDefault, int sort) {
        boolean personal = Objects.equals(req.getAccountType(), SupplierConstants.ACCOUNT_PERSONAL);
        row.setAccountType(req.getAccountType());
        row.setAccountName(req.getAccountName().trim());
        row.setBankName(req.getBankName().trim());
        row.setAccountNo(req.getAccountNo().trim());
        row.setIsDefault(isDefault ? 1 : 0);
        row.setSortOrder(sort);
        if (!personal) {
            // 对公账户没有收款人个人信息，切换类型时一并清掉
            row.setPayeePhone("");
            row.setPayeeIdNo("");
            return;
        }
        row.setPayeePhone(req.getPayeePhone() == null ? "" : req.getPayeePhone().trim());
        if (req.getPayeeIdNo() != null) {
            String idNo = req.getPayeeIdNo().trim().toUpperCase(Locale.ROOT);
            row.setPayeeIdNo(idNo.isEmpty() ? "" : aesUtils.encrypt(idNo));
        }
    }

    /** 按供应商批量读取；plain 为 true 时账号与手机号为明文（编辑取数），身份证号始终脱敏 */
    public Map<Long, List<SupplierBankAccountVO>> load(List<Long> supplierIds, boolean plain) {
        if (supplierIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<SupplierBankAccountVO>> result = new LinkedHashMap<>(16);
        activeRows(supplierIds).stream()
                .sorted(Comparator.comparing(SupplierBankAccountDO::getSortOrder).thenComparing(SupplierBankAccountDO::getId))
                .forEach(row -> result.computeIfAbsent(row.getSupplierId(), k -> new ArrayList<>(4)).add(toVo(row, plain)));
        return result;
    }

    private SupplierBankAccountVO toVo(SupplierBankAccountDO row, boolean plain) {
        SupplierBankAccountVO vo = new SupplierBankAccountVO();
        vo.setId(row.getId());
        vo.setAccountType(row.getAccountType());
        vo.setAccountName(row.getAccountName());
        vo.setBankName(row.getBankName());
        vo.setAccountNo(plain ? row.getAccountNo() : SensitiveDataMasker.maskBankAccount(row.getAccountNo()));
        vo.setPayeePhone(plain ? row.getPayeePhone() : SensitiveDataMasker.maskPhone(row.getPayeePhone()));
        String idNo = row.getPayeeIdNo() == null || row.getPayeeIdNo().isEmpty() ? "" : aesUtils.decrypt(row.getPayeeIdNo());
        vo.setPayeeIdNoMasked(SensitiveDataMasker.maskIdNo(idNo));
        vo.setDefaultAccount(Objects.equals(row.getIsDefault(), 1));
        return vo;
    }

    private List<SupplierBankAccountDO> activeRows(List<Long> supplierIds) {
        return accountMapper.selectList(new LambdaQueryWrapper<SupplierBankAccountDO>()
                .in(SupplierBankAccountDO::getSupplierId, supplierIds)
                .isNull(SupplierBankAccountDO::getDeletedAt));
    }

    /** 导出用的文本（已脱敏）：[对公·默认] 户名 / 开户行 / 6222 **** **** 8888；[对私] … */
    public static String toText(List<SupplierBankAccountVO> accounts) {
        if (accounts == null || accounts.isEmpty()) {
            return "";
        }
        return accounts.stream().map(a -> "[" + SupplierConstants.ACCOUNT_TYPE_LABELS.getOrDefault(a.getAccountType(), "")
                + (a.isDefaultAccount() ? "·默认" : "") + "] " + a.getAccountName() + " / " + a.getBankName()
                + " / " + a.getAccountNo()).collect(Collectors.joining("；"));
    }
}
