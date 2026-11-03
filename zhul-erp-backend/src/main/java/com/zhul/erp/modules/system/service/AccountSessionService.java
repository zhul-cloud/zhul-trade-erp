package com.zhul.erp.modules.system.service;

import com.zhul.erp.modules.system.entity.AccountAccessTokenDO;

/** 登录会话撤销：删除 Redis 里的 AccessToken、RefreshToken，并把会话记录标记失效 */
public interface AccountSessionService {

    /** 撤销一个会话 */
    void revoke(AccountAccessTokenDO session);

    /** 撤销用户的全部会话：停用、删除、管理员重置密码后旧登录立即失效 */
    void revokeAllOfUser(Integer userId);
}
