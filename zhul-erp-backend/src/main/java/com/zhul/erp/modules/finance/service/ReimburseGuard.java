package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.finance.entity.ReimbursementDO;
import com.zhul.erp.modules.finance.entity.ReimbursementLineDO;
import com.zhul.erp.modules.finance.repository.ReimbursementLineMapper;
import com.zhul.erp.modules.finance.repository.ReimbursementMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 已进报销单的来源（快递、退货运费、费用单、付款）不能撤销、作废或改金额，要先撤回报销单 */
@Component
@RequiredArgsConstructor
public class ReimburseGuard {

    private final ReimbursementLineMapper lineMapper;
    private final ReimbursementMapper reimbursementMapper;

    /** 有效报销明细；没有为空 */
    public ReimbursementLineDO activeLine(int sourceType, Long sourceId) {
        return lineMapper.selectOne(new LambdaQueryWrapper<ReimbursementLineDO>()
                .eq(ReimbursementLineDO::getTenantId, PiStore.tenantId())
                .eq(ReimbursementLineDO::getSourceType, sourceType)
                .eq(ReimbursementLineDO::getSourceId, sourceId)
                .eq(ReimbursementLineDO::getReleaseSeq, 0L)
                .isNull(ReimbursementLineDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    /** what：如「这票快递」「这笔退货运费」 */
    public void requireNotClaimed(int sourceType, Long sourceId, String what) {
        ReimbursementLineDO line = activeLine(sourceType, sourceId);
        if (line != null) {
            ReimbursementDO bx = reimbursementMapper.selectById(line.getReimbursementId());
            throw new BizException(what + "已在报销单 " + (bx == null ? "" : bx.getReimburseNo()) + " 里，请先撤回报销单");
        }
    }
}
