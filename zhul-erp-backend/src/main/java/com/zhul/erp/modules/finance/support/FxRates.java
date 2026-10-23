package com.zhul.erp.modules.finance.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.entity.FxSettlementAllocationDO;
import com.zhul.erp.modules.finance.entity.FxSettlementDO;
import com.zhul.erp.modules.finance.repository.FxSettlementAllocationMapper;
import com.zhul.erp.modules.finance.repository.FxSettlementMapper;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.service.ExchangeRateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 结汇相关的汇率（spec finance/fx-settlement）：外币到账未结汇的部分按估算汇率计，
 * 估算汇率 = 同一事业部该币种最近一张有效结汇单的汇率（迁移生成的除外）；还没有结汇单时用系统汇率。
 * 各事业部分别取（spec finance/fx-settlement「结汇按事业部分别先进先出」）；公司没有事业部时事业部为空。
 */
@Component
@RequiredArgsConstructor
public class FxRates {

    public static final String CNY = "CNY";

    private final FxSettlementMapper settlementMapper;
    private final FxSettlementAllocationMapper allocationMapper;
    private final ExchangeRateService exchangeRateService;

    /** 估算汇率（6 位小数）；人民币为 1 */
    public BigDecimal estimate(Integer businessUnitId, String currency) {
        if (CNY.equals(currency)) {
            return BigDecimal.ONE.setScale(6);
        }
        FxSettlementDO latest = latest(businessUnitId, currency);
        return latest != null ? latest.getExchangeRate() : exchangeRateService.require(currency).rate();
    }

    /** 同一事业部该币种最近一张有效结汇单（不含迁移生成的） */
    public FxSettlementDO latest(Integer businessUnitId, String currency) {
        return settlementMapper.selectOne(new LambdaQueryWrapper<FxSettlementDO>()
                .eq(FxSettlementDO::getTenantId, PiStore.tenantId())
                .eq(businessUnitId != null, FxSettlementDO::getBusinessUnitId, businessUnitId)
                .isNull(businessUnitId == null, FxSettlementDO::getBusinessUnitId)
                .eq(FxSettlementDO::getCurrencyCode, currency)
                .eq(FxSettlementDO::getStatus, FinanceConstants.FX_VALID)
                .eq(FxSettlementDO::getMigrated, 0)
                .isNull(FxSettlementDO::getDeletedAt)
                .orderByDesc(FxSettlementDO::getId)
                .last("LIMIT 1"));
    }

    /** 已有结汇分摊的到账不能作废 */
    public void requireNotSettled(PaymentReceiptDO r) {
        if (r.getSettledAmount() == null || r.getSettledAmount().signum() == 0) {
            return;
        }
        FxSettlementAllocationDO a = allocationMapper.selectOne(new LambdaQueryWrapper<FxSettlementAllocationDO>()
                .eq(FxSettlementAllocationDO::getReceiptId, r.getId())
                .isNull(FxSettlementAllocationDO::getDeletedAt)
                .orderByDesc(FxSettlementAllocationDO::getId)
                .last("LIMIT 1"));
        FxSettlementDO s = a == null ? null : settlementMapper.selectById(a.getSettlementId());
        throw new BizException("这笔到账已结汇" + (s == null ? "" : "（" + s.getSettleNo() + "）") + "，请先作废结汇单");
    }
}
