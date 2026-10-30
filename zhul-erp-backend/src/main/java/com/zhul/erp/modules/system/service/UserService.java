package com.zhul.erp.modules.system.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.system.dto.CreateUserRequest;
import com.zhul.erp.modules.system.dto.UpdateUserRequest;
import com.zhul.erp.modules.system.dto.UserPageQuery;
import com.zhul.erp.modules.system.dto.UserStatsVO;
import com.zhul.erp.modules.system.dto.UserVO;

public interface UserService {
    /** 用户、账号状态：1-启用（只有启用的账号能登录），0-禁用，2-已删除（软删除，用户列表和统计不再显示） */
    int STATUS_ENABLED = 1;
    int STATUS_DELETED = 2;

    PageResult<UserVO> listUsers(UserPageQuery query);
    UserStatsVO getUserStats();
    void updateUserStatus(Integer userId, Integer status);
    void createUser(CreateUserRequest request);
    void updateUser(Integer userId, UpdateUserRequest request);
    void deleteUser(Integer userId);
    void resetPassword(Integer userId, String newPassword);
}
