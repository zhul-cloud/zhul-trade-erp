package com.zhul.erp.modules.inventory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvPeriodDO;
import com.zhul.erp.modules.inventory.repository.InvPeriodMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * 结账锁定（spec finance/period-close「结账锁定」）：业务日期所在月份已结账时，库存过账一律拒绝，错误码 PERIOD_CLOSED。
 * 过账层（InventoryPosting）统一调用，各业务服务不必各自判断。
 */
@Service
@RequiredArgsConstructor
public class InventoryPeriods {

    public static final String ERROR_CODE = "PERIOD_CLOSED";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("yyyy-MM");

    /** 业务类型 → 提示里的动作 */
    private static final Map<String, String> VERBS = Map.ofEntries(
            Map.entry(InvConstants.BIZ_PURCHASE_IN, "入库"),
            Map.entry(InvConstants.BIZ_PURCHASE_IN_REVERSE, "冲销"),
            Map.entry(InvConstants.BIZ_DISCOUNT_ACCEPT, "折价接收"),
            Map.entry(InvConstants.BIZ_DISCOUNT_ACCEPT_UNDO, "重新打开"),
            Map.entry(InvConstants.BIZ_HANDOVER, "交货代"),
            Map.entry(InvConstants.BIZ_HANDOVER_UNDO, "撤销交货代"),
            Map.entry(InvConstants.BIZ_SALES_OUT, "出运"),
            Map.entry(InvConstants.BIZ_SALES_OUT_UNDO, "撤销出运"),
            Map.entry(InvConstants.BIZ_RETURN_IN, "验收"),
            Map.entry(InvConstants.BIZ_RETURN_IN_UNDO, "撤回"),
            Map.entry(InvConstants.BIZ_PURCHASE_RETURN_OUT, "寄出"),
            Map.entry(InvConstants.BIZ_OTHER_OUT, "出库"),
            Map.entry(InvConstants.BIZ_OTHER_OUT_VOID, "作废"),
            Map.entry(InvConstants.BIZ_SYSTEM_IN, "入库"),
            Map.entry(InvConstants.BIZ_MERGE, "合并商品"),
            Map.entry(InvConstants.BIZ_OPENING, "生成期初"));

    private final InvPeriodMapper periodMapper;

    public static String month(LocalDate date) {
        return date.format(MONTH);
    }

    public static YearMonth parse(String month) {
        return YearMonth.parse(month, MONTH);
    }

    /** 某月份是否已结账 */
    public boolean closed(String month) {
        return periodMapper.selectCount(new LambdaQueryWrapper<InvPeriodDO>()
                .eq(InvPeriodDO::getTenantId, PiStore.tenantId())
                .eq(InvPeriodDO::getPeriodMonth, month)
                .eq(InvPeriodDO::getStatus, InvConstants.PERIOD_CLOSED)
                .isNull(InvPeriodDO::getDeletedAt)) > 0;
    }

    /** 业务日期所在月份已结账时报 PERIOD_CLOSED；bizType 决定提示里的动作 */
    public void requireOpen(LocalDate date, String bizType) {
        String m = month(date == null ? LocalDate.now() : date);
        if (closed(m)) {
            throw closedError(m, VERBS.getOrDefault(bizType, "改动"));
        }
    }

    public static BizException closedError(String month, String verb) {
        return BizException.of(ERROR_CODE, month + " 已结账，不能" + verb + "业务日期在 " + month + " 的单据", Map.of("month", month));
    }
}
