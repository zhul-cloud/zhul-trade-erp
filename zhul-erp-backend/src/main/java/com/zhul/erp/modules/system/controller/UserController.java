package com.zhul.erp.modules.system.controller;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.system.dto.CreateUserRequest;
import com.zhul.erp.modules.system.dto.UpdateUserRequest;
import com.zhul.erp.modules.system.dto.UserPageQuery;
import com.zhul.erp.modules.system.dto.UserStatsVO;
import com.zhul.erp.modules.system.dto.UserVO;
import com.zhul.erp.modules.system.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/v1/system/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "用户列表")
    @GetMapping
    public Result<PageResult<UserVO>> list(UserPageQuery query) {
        return Result.ok(userService.listUsers(query));
    }

    @Operation(summary = "用户统计")
    @GetMapping("/stats")
    public Result<UserStatsVO> stats() {
        return Result.ok(userService.getUserStats());
    }

    @Operation(summary = "修改用户状态")
    @PreAuthorize("@perm.has('system:user:status')")
    @PutMapping("/{userId}/status")
    public Result<Void> updateStatus(@PathVariable Integer userId,
                                     @RequestParam Integer status) {
        userService.updateUserStatus(userId, status);
        return Result.ok();
    }

    @Operation(summary = "新增用户")
    @PreAuthorize("@perm.has('system:user:add')")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody CreateUserRequest request) {
        userService.createUser(request);
        return Result.ok();
    }

    @Operation(summary = "编辑用户")
    @PreAuthorize("@perm.has('system:user:edit')")
    @PutMapping("/{userId}")
    public Result<Void> update(@PathVariable Integer userId, @RequestBody UpdateUserRequest request) {
        userService.updateUser(userId, request);
        return Result.ok();
    }

    @Operation(summary = "删除用户")
    @PreAuthorize("@perm.has('system:user:delete')")
    @DeleteMapping("/{userId}")
    public Result<Void> delete(@PathVariable Integer userId) {
        userService.deleteUser(userId);
        return Result.ok();
    }

    @Operation(summary = "重置密码")
    @PreAuthorize("@perm.has('system:user:resetPwd')")
    @PutMapping("/{userId}/password")
    public Result<Void> resetPassword(@PathVariable Integer userId,
                                      @RequestParam String newPassword) {
        userService.resetPassword(userId, newPassword);
        return Result.ok();
    }
}
