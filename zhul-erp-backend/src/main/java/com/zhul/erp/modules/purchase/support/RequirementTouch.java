package com.zhul.erp.modules.purchase.support;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.framework.security.SecurityUtils;
import com.zhul.erp.modules.purchase.entity.PurchaseRequirementDO;
import com.zhul.erp.modules.purchase.repository.PurchaseRequirementMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/** 需求的数量或状态在采购单行上变化时（排入、移出草稿、下单、取消），刷新需求的更新时间与更新人 */
@Component
@RequiredArgsConstructor
public class RequirementTouch {

    private final PurchaseRequirementMapper requirementMapper;

    public void touch(Collection<Long> requirementIds) {
        List<Long> ids = requirementIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        String who = SecurityUtils.getCurrentUsername();
        requirementMapper.update(null, new LambdaUpdateWrapper<PurchaseRequirementDO>()
                .set(PurchaseRequirementDO::getUpdateTime, LocalDateTime.now())
                .set(PurchaseRequirementDO::getUpdateBy, who == null || who.isBlank() ? "sys" : who)
                .in(PurchaseRequirementDO::getId, ids));
    }
}
