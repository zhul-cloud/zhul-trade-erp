package com.zhul.erp.modules.system.service;

import com.zhul.erp.modules.system.dto.BankAccountVO;
import com.zhul.erp.modules.system.dto.SaveBankAccountRequest;
import com.zhul.erp.modules.system.entity.BankAccountDO;

import java.util.List;

/** 收款账户：按租户维护，每个币种恰好一个默认（有启用账户时） */
public interface BankAccountService {

    List<BankAccountVO> list();

    /** 开单用：启用的账户（含完整账号） */
    List<BankAccountVO> enabledOptions();

    /** 编辑用：含完整账号 */
    BankAccountVO get(Integer id);

    BankAccountVO create(SaveBankAccountRequest req);

    BankAccountVO update(Integer id, SaveBankAccountRequest req);

    void setDefault(Integer id);

    void setEnabled(Integer id, boolean enabled);

    /** 当前租户某币种的默认启用账户，没有时为 null */
    BankAccountDO defaultFor(String currencyCode);

    /** 当前租户的启用账户，不存在或已停用时抛业务异常 */
    BankAccountDO requireEnabled(Integer id);

    static String mask(String accountNo) {
        if (accountNo == null || accountNo.isBlank()) {
            return "";
        }
        String s = accountNo.replace(" ", "");
        return "****" + s.substring(Math.max(0, s.length() - 4));
    }
}
