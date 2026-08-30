package com.zhul.erp.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.modules.system.entity.AccountLocalAuthDO;
import com.zhul.erp.modules.system.repository.AccountLocalAuthMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 登录失败计数/锁定状态的持久化，使用独立事务（REQUIRES_NEW）。
 * 必须作为独立 Bean 存在：若把该方法直接写在 AuthServiceImpl 内部并让同类方法
 * 互相调用（this.xxx()），Spring AOP 不会走代理，REQUIRES_NEW 不会生效，
 * 登录失败时抛出的业务异常会把这次计数更新一并回滚（此前踩过的坑）。
 */
@Service
@RequiredArgsConstructor
public class LoginFailureTracker {

    private final AccountLocalAuthMapper accountLocalAuthMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Integer localAuthId, int failCount, LocalDateTime unlockAt) {
        accountLocalAuthMapper.update(null, new LambdaUpdateWrapper<AccountLocalAuthDO>()
                .eq(AccountLocalAuthDO::getId, localAuthId)
                .set(AccountLocalAuthDO::getFailCount, failCount)
                .set(AccountLocalAuthDO::getLastFailAt, LocalDateTime.now())
                .set(AccountLocalAuthDO::getUnlockAt, unlockAt));
    }
}
