package com.zhul.erp.modules.system.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.system.dto.CreateUserRequest;
import com.zhul.erp.modules.system.dto.UpdateUserRequest;
import com.zhul.erp.modules.system.dto.UserPageQuery;
import com.zhul.erp.modules.system.dto.UserStatsVO;
import com.zhul.erp.modules.system.dto.UserVO;

public interface UserService {
    PageResult<UserVO> listUsers(UserPageQuery query);
    UserStatsVO getUserStats();
    void updateUserStatus(Integer userId, Integer status);
    void createUser(CreateUserRequest request);
    void updateUser(Integer userId, UpdateUserRequest request);
    void deleteUser(Integer userId);
    void resetPassword(Integer userId, String newPassword);
}
