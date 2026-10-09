package com.zhul.erp.modules.purchase.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.purchase.dto.UserOptionVO;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采购员候选：本租户启用的用户（只返回 ID 与姓名）。
 * 采购需求的拆分、指派与订单详情改采购员都要用，不要求「用户」菜单权限。
 */
@RestController
@RequiredArgsConstructor
public class PurchaserController {

    private final UserBasicMapper userBasicMapper;

    @GetMapping("/api/v1/purchase/purchasers")
    @PreAuthorize("@perm.canAccessMenu('/purchase/requirements') or @perm.canAccessMenu('/purchase/orders') "
            + "or @perm.canAccessMenu('/sales/orders')")
    public Result<List<UserOptionVO>> list() {
        return Result.ok(userBasicMapper.selectList(new LambdaQueryWrapper<UserBasicDO>()
                        .select(UserBasicDO::getId, UserBasicDO::getName)
                        .eq(UserBasicDO::getTenantId, PiStore.tenantId())
                        .eq(UserBasicDO::getStatus, 1)
                        .orderByAsc(UserBasicDO::getName)
                        .last("LIMIT 500"))
                .stream().map(u -> new UserOptionVO(u.getId().longValue(), u.getName())).toList());
    }
}
