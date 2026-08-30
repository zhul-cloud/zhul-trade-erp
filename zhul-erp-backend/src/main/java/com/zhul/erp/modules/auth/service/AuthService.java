package com.zhul.erp.modules.auth.service;

import com.zhul.erp.modules.auth.dto.LoginRequest;
import com.zhul.erp.modules.auth.dto.LoginResponse;
import com.zhul.erp.modules.auth.dto.RefreshTokenResponse;
import com.zhul.erp.modules.auth.dto.ResetPasswordRequest;
import com.zhul.erp.modules.auth.dto.SendResetCodeRequest;
import com.zhul.erp.modules.auth.dto.VerifyResetCodeRequest;
import com.zhul.erp.modules.auth.dto.VerifyResetCodeResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public interface AuthService {
    LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse);
    RefreshTokenResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse);
    void logout(String accessToken, HttpServletResponse httpResponse);
    void sendResetCode(SendResetCodeRequest request);
    VerifyResetCodeResponse verifyResetCode(VerifyResetCodeRequest request);
    void resetPassword(ResetPasswordRequest request);
}
