package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.entity.ExpenseDO;
import com.zhul.erp.modules.finance.entity.PaymentDO;
import com.zhul.erp.modules.finance.entity.ReimbursementDO;
import com.zhul.erp.modules.finance.entity.ReimbursementLineDO;
import com.zhul.erp.modules.finance.repository.ExpenseMapper;
import com.zhul.erp.modules.finance.repository.PaymentMapper;
import com.zhul.erp.modules.finance.repository.ReimbursementLineMapper;
import com.zhul.erp.modules.finance.repository.ReimbursementMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * 撤回待报销项（openspec po-cancel-refund，spec finance/reimbursement「撤回待报销项」）：撤回即作废该项。
 * 采购货款、已收到退款按付款作废，费用单按费用单作废；快递费、退货运费不在这里撤回；已进有效报销单的不能撤回。
 * 本人可以撤回自己的；财务撤回他人的，权限同各单据的作废权限。
 */
@Service
@RequiredArgsConstructor
public class PendingItemWithdrawService {

    private final PaymentMapper paymentMapper;
    private final ExpenseMapper expenseMapper;
    private final ReimbursementLineMapper lineMapper;
    private final ReimbursementMapper reimbursementMapper;
    private final PaymentService payments;
    private final ExpenseService expenses;
    private final PermissionChecker perm;
    private final CurrentUserResolver currentUser;

    @Transactional(rollbackFor = Exception.class)
    public void withdraw(int type, Long sourceId, String reason) {
        String why = reason.trim();
        switch (type) {
            case FinanceConstants.ITEM_COURIER -> throw new BizException("快递费随快递单处理");
            case FinanceConstants.ITEM_RETURN_FREIGHT -> throw new BizException("退货运费随退货单处理");
            case FinanceConstants.ITEM_PAYMENT, FinanceConstants.ITEM_REFUND -> {
                PaymentDO p = paymentMapper.selectOne(new LambdaQueryWrapper<PaymentDO>()
                        .eq(PaymentDO::getId, sourceId)
                        .eq(PaymentDO::getTenantId, PiStore.tenantId())
                        .isNull(PaymentDO::getDeletedAt));
                if (p == null || !Objects.equals(p.getMethod(), FinanceConstants.METHOD_ADVANCE)) {
                    throw new BizException("待报销项不存在");
                }
                requireNotClaimed(type, sourceId);
                boolean mine = Objects.equals(p.getAdvanceUser(), SecurityUtils.getCurrentUsername());
                if (!mine && !Objects.equals(p.getCreateBy(), SecurityUtils.getCurrentUsername())
                        && !perm.has(FinanceConstants.PERM_REGISTER)) {
                    throw new BizException("只能撤回自己的待报销项");
                }
                payments.voidBySystem(sourceId, "从待报销撤回：" + why);
            }
            case FinanceConstants.ITEM_EXPENSE -> {
                ExpenseDO e = expenseMapper.selectOne(new LambdaQueryWrapper<ExpenseDO>()
                        .eq(ExpenseDO::getId, sourceId)
                        .eq(ExpenseDO::getTenantId, PiStore.tenantId())
                        .isNull(ExpenseDO::getDeletedAt));
                if (e == null || !Objects.equals(e.getMethod(), FinanceConstants.METHOD_ADVANCE)) {
                    throw new BizException("待报销项不存在");
                }
                requireNotClaimed(type, sourceId);
                if (!Objects.equals(e.getAdvanceUserId(), currentUser.resolve()) && !perm.has(FinanceConstants.PERM_EXPENSE_EDIT)) {
                    throw new BizException("只能撤回自己的待报销项");
                }
                expenses.voidExpense(sourceId, "从待报销撤回：" + why);
            }
            default -> throw new BizException("待报销项不存在");
        }
    }

    /** 已在有效报销单（待付款或已付）里的不能撤回 */
    private void requireNotClaimed(int type, Long sourceId) {
        ReimbursementLineDO line = lineMapper.selectOne(new LambdaQueryWrapper<ReimbursementLineDO>()
                .eq(ReimbursementLineDO::getTenantId, PiStore.tenantId())
                .eq(ReimbursementLineDO::getSourceType, type)
                .eq(ReimbursementLineDO::getSourceId, sourceId)
                .eq(ReimbursementLineDO::getReleaseSeq, 0L)
                .isNull(ReimbursementLineDO::getDeletedAt)
                .last("LIMIT 1"));
        if (line != null) {
            ReimbursementDO r = reimbursementMapper.selectById(line.getReimbursementId());
            throw new BizException("这一项已在报销单 " + (r == null ? "" : r.getReimburseNo()) + " 中，请先撤回报销单");
        }
    }
}
