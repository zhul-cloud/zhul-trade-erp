package com.zhul.erp.modules.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.modules.system.entity.AccountAccessTokenDO;
import com.zhul.erp.modules.system.repository.AccountAccessTokenMapper;
import com.zhul.erp.modules.system.service.AccountSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSessionServiceImpl implements AccountSessionService {

    private final AccountAccessTokenMapper accountAccessTokenMapper;
    private final StringRedisTemplate redisTemplate;

    @Override
    public void revoke(AccountAccessTokenDO session) {
        redisTemplate.delete(RedisKeyConstants.TOKEN_PREFIX + session.getAccessToken());
        // RefreshToken 也要删，否则「记住我」会话仍可通过 /auth/refresh 静默续期
        if (StringUtils.hasText(session.getRefreshToken())) {
            redisTemplate.delete(RedisKeyConstants.REFRESH_TOKEN_PREFIX + session.getRefreshToken());
        }
        session.setStatus(0);
        session.setDeletedAt(LocalDateTime.now());
        accountAccessTokenMapper.updateById(session);
    }

    @Override
    public void revokeAllOfUser(Integer userId) {
        List<AccountAccessTokenDO> sessions = accountAccessTokenMapper.selectList(
                new LambdaQueryWrapper<AccountAccessTokenDO>()
                        .eq(AccountAccessTokenDO::getUserId, userId)
                        .isNull(AccountAccessTokenDO::getDeletedAt));
        sessions.forEach(this::revoke);
        if (!sessions.isEmpty()) {
            log.info("撤销用户全部登录会话，userId={}, count={}", userId, sessions.size());
        }
    }
}
