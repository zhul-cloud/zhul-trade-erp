package com.zhul.erp.modules.auth.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.result.Result;
import com.zhul.erp.modules.auth.dto.LoginRequest;
import com.zhul.erp.modules.auth.dto.LoginResponse;
import com.zhul.erp.modules.auth.dto.RefreshTokenResponse;
import com.zhul.erp.modules.auth.dto.ResetPasswordRequest;
import com.zhul.erp.modules.auth.dto.SendResetCodeRequest;
import com.zhul.erp.modules.auth.dto.VerifyResetCodeRequest;
import com.zhul.erp.modules.auth.dto.VerifyResetCodeResponse;
import com.zhul.erp.modules.auth.service.AuthService;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "认证管理", description = "登录、退出、Token刷新、找回密码")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final MenuService menuService;
    private final UserBasicMapper userBasicMapper;

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request,
                                       HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return Result.ok(authService.login(request, httpRequest, httpResponse));
    }

    @Operation(summary = "刷新AccessToken")
    @PostMapping("/refresh")
    public Result<RefreshTokenResponse> refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        return Result.ok(authService.refresh(httpRequest, httpResponse));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String header = request.getHeader("Authorization");
        String token = StringUtils.hasText(header) && header.startsWith("Bearer ") ? header.substring(7) : null;
        authService.logout(token, response);
        return Result.ok();
    }

    @Operation(summary = "发送找回密码验证码")
    @PostMapping("/password/send-code")
    public Result<Void> sendResetCode(@Valid @RequestBody SendResetCodeRequest request) {
        authService.sendResetCode(request);
        return Result.ok();
    }

    @Operation(summary = "校验找回密码验证码")
    @PostMapping("/password/verify-code")
    public Result<VerifyResetCodeResponse> verifyResetCode(@Valid @RequestBody VerifyResetCodeRequest request) {
        return Result.ok(authService.verifyResetCode(request));
    }

    @Operation(summary = "重置密码")
    @PostMapping("/password/reset")
    public Result<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return Result.ok();
    }

    @Operation(summary = "获取当前用户菜单权限")
    @GetMapping("/menus")
    public Result<List<String>> currentUserMenus() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        UserBasicDO user = userBasicMapper.selectOne(
            new LambdaQueryWrapper<UserBasicDO>()
                .eq(UserBasicDO::getUsername, username)
                .last("LIMIT 1")
        );
        if (user == null || !StringUtils.hasText(user.getRoleCode())) {
            return Result.ok(new ArrayList<>());
        }
        return Result.ok(menuService.getUserMenuPaths(user.getRoleCode()));
    }
}
