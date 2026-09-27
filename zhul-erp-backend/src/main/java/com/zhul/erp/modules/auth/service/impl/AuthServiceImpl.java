package com.zhul.erp.modules.auth.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.constants.RedisKeyConstants;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.ResultCode;
import com.zhul.erp.common.utils.IpUtils;
import com.zhul.erp.common.utils.JwtUtils;
import com.zhul.erp.modules.auth.dto.LoginRequest;
import com.zhul.erp.modules.auth.dto.LoginResponse;
import com.zhul.erp.modules.auth.dto.RefreshTokenResponse;
import com.zhul.erp.modules.auth.dto.ResetPasswordRequest;
import com.zhul.erp.modules.auth.dto.SendResetCodeRequest;
import com.zhul.erp.modules.auth.dto.VerifyResetCodeRequest;
import com.zhul.erp.modules.auth.dto.VerifyResetCodeResponse;
import com.zhul.erp.modules.auth.service.AuthService;
import com.zhul.erp.modules.system.entity.AccountAccessTokenDO;
import com.zhul.erp.modules.system.entity.AccountDO;
import com.zhul.erp.modules.system.entity.AccountLocalAuthDO;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.AccountAccessTokenMapper;
import com.zhul.erp.modules.system.repository.AccountLocalAuthMapper;
import com.zhul.erp.modules.system.repository.AccountMapper;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.tenant.entity.TenantDO;
import com.zhul.erp.modules.tenant.repository.TenantMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final AccountMapper accountMapper;
    private final AccountLocalAuthMapper accountLocalAuthMapper;
    private final AccountAccessTokenMapper accountAccessTokenMapper;
    private final UserBasicMapper userBasicMapper;
    private final TenantMapper tenantMapper;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;
    private final LogService logService;
    private final ObjectMapper objectMapper;
    private final LoginFailureTracker loginFailureTracker;

    private static final long EXPIRES_NORMAL = 7200L;
    private static final long EXPIRES_REMEMBER = 604800L;
    private static final int MAX_FAIL_COUNT = 5;
    private static final int LOCK_MINUTES = 30;
    private static final int RESET_CODE_TTL_MINUTES = 10;
    private static final int RESET_CODE_COOLDOWN_SECONDS = 60;
    private static final int RESET_CODE_MAX_FAIL = 3;
    private static final String REFRESH_COOKIE_NAME = "rft";
    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        // 0. 幂等键去重：命中缓存直接返回上一次的成功结果
        String idemKey = RedisKeyConstants.LOGIN_IDEMPOTENCY_PREFIX + request.getIdempotencyKey();
        String cached = redisTemplate.opsForValue().get(idemKey);
        if (StringUtils.hasText(cached)) {
            try {
                return objectMapper.readValue(cached, LoginResponse.class);
            } catch (Exception ignored) {
                // 缓存内容异常时忽略，按正常流程重新处理
            }
        }

        // 1. 查账号
        AccountDO account = accountMapper.selectOne(
            new LambdaQueryWrapper<AccountDO>()
                .eq(AccountDO::getUsername, request.getUsername())
                .last("LIMIT 1")
        );
        if (account == null) {
            // 账号不存在时无法确定 tenant_id，登录日志按租户隔离要求不予记录；
            // 对外统一返回“用户名或密码错误”，避免暴露账号是否存在
            throw new BizException(ResultCode.INVALID_CREDENTIALS);
        }
        if (account.getStatus() == 0) {
            recordLoginFailure(account, httpRequest, "账号已禁用");
            throw new BizException(ResultCode.ACCOUNT_DISABLED);
        }

        // 2. 锁定状态判断
        AccountLocalAuthDO localAuth = accountLocalAuthMapper.selectOne(
            new LambdaQueryWrapper<AccountLocalAuthDO>()
                .eq(AccountLocalAuthDO::getAccountId, account.getId())
                .last("LIMIT 1")
        );
        LocalDateTime now = LocalDateTime.now();
        // 在自动解锁的原地重置修改 localAuth 之前，先记录“重置前”的失败状态，
        // 否则第 4 步的持久化判断会读到已被清零的内存对象，误判为“无需写库”。
        boolean hadFailureState = localAuth != null
                && ((localAuth.getFailCount() != null && localAuth.getFailCount() > 0) || localAuth.getUnlockAt() != null);
        if (localAuth != null && localAuth.getUnlockAt() != null) {
            if (localAuth.getUnlockAt().isAfter(now)) {
                recordLoginFailure(account, httpRequest, "账号已锁定");
                throw new BizException(ResultCode.ACCOUNT_LOCKED,
                        String.format("账号已被锁定，请于 %s 后重试或联系管理员", localAuth.getUnlockAt().format(HHMM)));
            }
            // 锁定已到期：自动解锁（内存态先清零，供后续密码校验使用正确的 fail_count 基数）
            if (localAuth.getFailCount() != null && localAuth.getFailCount() >= MAX_FAIL_COUNT) {
                localAuth.setFailCount(0);
                localAuth.setUnlockAt(null);
            }
        }

        // 3. 验证密码
        if (localAuth == null || !BCrypt.checkpw(request.getPassword(), localAuth.getPassword())) {
            handlePasswordFailure(account, localAuth, httpRequest);
            // handlePasswordFailure 内部必定抛出异常，此处不会执行到
            throw new BizException(ResultCode.INVALID_CREDENTIALS);
        }

        // 4. 登录成功：清除失败计数
        if (hadFailureState) {
            accountLocalAuthMapper.update(null, new LambdaUpdateWrapper<AccountLocalAuthDO>()
                    .eq(AccountLocalAuthDO::getId, localAuth.getId())
                    .set(AccountLocalAuthDO::getFailCount, 0)
                    .set(AccountLocalAuthDO::getUnlockAt, null));
        }

        // 5. 生成 JWT AccessToken
        long expiresIn = EXPIRES_NORMAL;
        Map<String, Object> claims = new HashMap<>();
        claims.put("accountId", account.getId());
        claims.put("userId", account.getUserId());
        claims.put("tenantId", account.getTenantId());
        String token = jwtUtils.generateToken(claims, account.getUsername(), expiresIn);
        redisTemplate.opsForValue().set(
            RedisKeyConstants.TOKEN_PREFIX + token,
            String.valueOf(account.getId()),
            expiresIn, TimeUnit.SECONDS
        );

        // 6. remember_me：签发 RefreshToken，写入 HttpOnly Cookie
        String refreshToken = null;
        if (Boolean.TRUE.equals(request.getRememberMe())) {
            refreshToken = UUID.randomUUID().toString().replace("-", "");
            redisTemplate.opsForValue().set(
                RedisKeyConstants.REFRESH_TOKEN_PREFIX + refreshToken,
                String.valueOf(account.getId()),
                EXPIRES_REMEMBER, TimeUnit.SECONDS
            );
            setRefreshCookie(httpResponse, refreshToken, httpRequest.isSecure());
        }

        String deviceInfo = summarizeUserAgent(httpRequest.getHeader("User-Agent"));
        String clientIp = IpUtils.getClientIp(httpRequest);
        AccountAccessTokenDO tokenRow = new AccountAccessTokenDO();
        tokenRow.setUserId(account.getUserId());
        tokenRow.setAccountId(account.getId());
        tokenRow.setAccessToken(token);
        tokenRow.setRefreshToken(refreshToken);
        tokenRow.setDeviceInfo(deviceInfo);
        tokenRow.setLoginIp(clientIp);
        tokenRow.setStatus(1);
        tokenRow.setExpiryTime(now.plusSeconds(expiresIn));
        accountAccessTokenMapper.insert(tokenRow);

        // 7. 更新登录状态
        accountMapper.update(null, new LambdaUpdateWrapper<AccountDO>()
            .eq(AccountDO::getId, account.getId())
            .set(AccountDO::getLastLoginTime, now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
            .set(AccountDO::getLoginStatus, 1)
        );

        // 8. 查用户信息
        UserBasicDO userBasic = userBasicMapper.selectById(account.getUserId());

        log.info("用户登录成功，username={}, tenantId={}", account.getUsername(), account.getTenantId());
        logService.recordLoginLog(account.getTenantId(), account.getId(), account.getUsername(), 1,
                clientIp, httpRequest.getHeader("User-Agent"), token, null);

        LoginResponse response = LoginResponse.builder()
            .accessToken(token)
            .expiresIn(expiresIn)
            .username(account.getUsername())
            .nickname(userBasic != null ? userBasic.getNickname() : account.getUsername())
            .avatarUrl(userBasic != null ? userBasic.getAvatarUrl() : "")
            .isAdmin(account.getAdminFlag() != null && account.getAdminFlag() == 1)
            .tenantName(resolveTenantName(account.getTenantId()))
            .build();

        try {
            redisTemplate.opsForValue().set(idemKey, objectMapper.writeValueAsString(response), 10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("登录幂等结果缓存失败", e);
        }
        return response;
    }

    /**
     * 密码校验失败：更新失败计数/锁定状态，记录登录日志，抛出对应的业务异常。
     * 本方法必定抛出异常。
     */
    private void handlePasswordFailure(AccountDO account, AccountLocalAuthDO localAuth, HttpServletRequest httpRequest) {
        if (localAuth == null) {
            recordLoginFailure(account, httpRequest, "密码错误");
            throw new BizException(ResultCode.INVALID_CREDENTIALS);
        }
        int newFailCount = (localAuth.getFailCount() == null ? 0 : localAuth.getFailCount()) + 1;

        if (newFailCount >= MAX_FAIL_COUNT) {
            LocalDateTime unlockAt = LocalDateTime.now().plusMinutes(LOCK_MINUTES);
            loginFailureTracker.recordFailure(localAuth.getId(), newFailCount, unlockAt);
            recordLoginFailure(account, httpRequest, "账号已锁定");
            throw new BizException(ResultCode.ACCOUNT_LOCKED,
                    String.format("账号已被锁定，请于 %s 后重试或联系管理员", unlockAt.format(HHMM)));
        }

        loginFailureTracker.recordFailure(localAuth.getId(), newFailCount, null);
        recordLoginFailure(account, httpRequest, "密码错误");
        if (newFailCount == MAX_FAIL_COUNT - 1) {
            throw new BizException(ResultCode.LAST_ATTEMPT_WARNING);
        }
        int remaining = MAX_FAIL_COUNT - newFailCount;
        throw new BizException(ResultCode.INVALID_CREDENTIALS, "用户名或密码错误，还可尝试 " + remaining + " 次");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefreshTokenResponse refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String refreshToken = extractCookie(httpRequest, REFRESH_COOKIE_NAME);
        if (!StringUtils.hasText(refreshToken)) {
            throw new BizException(ResultCode.REFRESH_TOKEN_INVALID);
        }
        String accountIdStr = redisTemplate.opsForValue().get(RedisKeyConstants.REFRESH_TOKEN_PREFIX + refreshToken);
        if (accountIdStr == null) {
            throw new BizException(ResultCode.REFRESH_TOKEN_INVALID);
        }
        AccountAccessTokenDO tokenRow = accountAccessTokenMapper.selectOne(
                new LambdaQueryWrapper<AccountAccessTokenDO>()
                        .eq(AccountAccessTokenDO::getRefreshToken, refreshToken)
                        .isNull(AccountAccessTokenDO::getDeletedAt)
                        .last("LIMIT 1"));
        if (tokenRow == null) {
            throw new BizException(ResultCode.REFRESH_TOKEN_INVALID);
        }
        AccountDO account = accountMapper.selectById(tokenRow.getAccountId());
        if (account == null || account.getStatus() == 0) {
            throw new BizException(ResultCode.REFRESH_TOKEN_INVALID);
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("accountId", account.getId());
        claims.put("userId", account.getUserId());
        claims.put("tenantId", account.getTenantId());
        String newToken = jwtUtils.generateToken(claims, account.getUsername(), EXPIRES_NORMAL);

        redisTemplate.delete(RedisKeyConstants.TOKEN_PREFIX + tokenRow.getAccessToken());
        redisTemplate.opsForValue().set(
                RedisKeyConstants.TOKEN_PREFIX + newToken,
                String.valueOf(account.getId()),
                EXPIRES_NORMAL, TimeUnit.SECONDS
        );

        tokenRow.setAccessToken(newToken);
        tokenRow.setExpiryTime(LocalDateTime.now().plusSeconds(EXPIRES_NORMAL));
        accountAccessTokenMapper.updateById(tokenRow);

        log.info("Token 刷新成功，accountId={}", account.getId());
        return RefreshTokenResponse.builder().accessToken(newToken).expiresIn(EXPIRES_NORMAL).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(String accessToken, HttpServletResponse httpResponse) {
        if (StringUtils.hasText(accessToken)) {
            redisTemplate.delete(RedisKeyConstants.TOKEN_PREFIX + accessToken);
            AccountAccessTokenDO row = accountAccessTokenMapper.selectOne(
                    new LambdaQueryWrapper<AccountAccessTokenDO>()
                            .eq(AccountAccessTokenDO::getAccessToken, accessToken)
                            .isNull(AccountAccessTokenDO::getDeletedAt)
                            .last("LIMIT 1"));
            if (row != null) {
                if (StringUtils.hasText(row.getRefreshToken())) {
                    redisTemplate.delete(RedisKeyConstants.REFRESH_TOKEN_PREFIX + row.getRefreshToken());
                }
                row.setStatus(0);
                row.setDeletedAt(LocalDateTime.now());
                accountAccessTokenMapper.updateById(row);
            }
        }
        clearRefreshCookie(httpResponse);
        log.info("用户退出登录");
    }

    @Override
    public void sendResetCode(SendResetCodeRequest request) {
        String cooldownKey = RedisKeyConstants.RESET_CODE_COOLDOWN_PREFIX + request.getUsername();
        Long ttl = redisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
        if (ttl != null && ttl > 0) {
            throw new BizException(ResultCode.SEND_TOO_FREQUENT, "发送过于频繁，请 " + ttl + " 秒后再试");
        }

        AccountDO account = accountMapper.selectOne(
                new LambdaQueryWrapper<AccountDO>()
                        .eq(AccountDO::getUsername, request.getUsername())
                        .last("LIMIT 1"));
        if (account == null) {
            // 防止账号枚举：账号不存在时静默返回，不设置冷却（不消耗真实发送额度）
            return;
        }
        if (!StringUtils.hasText(account.getEmail())) {
            throw new BizException(ResultCode.NO_EMAIL_BOUND);
        }
        if (!account.getEmail().equalsIgnoreCase(request.getEmail().trim())) {
            // 邮箱不匹配：同样静默返回，防止枚举正确邮箱
            return;
        }

        AccountLocalAuthDO localAuth = accountLocalAuthMapper.selectOne(
                new LambdaQueryWrapper<AccountLocalAuthDO>()
                        .eq(AccountLocalAuthDO::getAccountId, account.getId())
                        .last("LIMIT 1"));
        if (localAuth == null) {
            return;
        }

        String code = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        accountLocalAuthMapper.update(null, new LambdaUpdateWrapper<AccountLocalAuthDO>()
                .eq(AccountLocalAuthDO::getId, localAuth.getId())
                .set(AccountLocalAuthDO::getResetToken, code)
                .set(AccountLocalAuthDO::getResetTokenExpiresAt, LocalDateTime.now().plusMinutes(RESET_CODE_TTL_MINUTES))
                .set(AccountLocalAuthDO::getResetFailCount, 0));

        redisTemplate.opsForValue().set(cooldownKey, "1", RESET_CODE_COOLDOWN_SECONDS, TimeUnit.SECONDS);

        // 未接入真实邮件服务商（见 PRD 默认假设 3），此处以日志方式模拟“发送”，
        // 便于本地/测试环境验证找回密码流程。
        log.info("[模拟邮件发送] 找回密码验证码 username={}, email={}, code={}, 10分钟内有效",
                account.getUsername(), account.getEmail(), code);
    }

    @Override
    public VerifyResetCodeResponse verifyResetCode(VerifyResetCodeRequest request) {
        AccountDO account = accountMapper.selectOne(
                new LambdaQueryWrapper<AccountDO>()
                        .eq(AccountDO::getUsername, request.getUsername())
                        .last("LIMIT 1"));
        if (account == null) {
            throw new BizException(ResultCode.RESET_CODE_INVALID);
        }
        AccountLocalAuthDO localAuth = accountLocalAuthMapper.selectOne(
                new LambdaQueryWrapper<AccountLocalAuthDO>()
                        .eq(AccountLocalAuthDO::getAccountId, account.getId())
                        .last("LIMIT 1"));
        if (localAuth == null || !StringUtils.hasText(localAuth.getResetToken())
                || localAuth.getResetTokenExpiresAt() == null
                || localAuth.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.RESET_TOKEN_EXPIRED);
        }

        if (!localAuth.getResetToken().equals(request.getCode().trim())) {
            int newFailCount = (localAuth.getResetFailCount() == null ? 0 : localAuth.getResetFailCount()) + 1;
            if (newFailCount >= RESET_CODE_MAX_FAIL) {
                accountLocalAuthMapper.update(null, new LambdaUpdateWrapper<AccountLocalAuthDO>()
                        .eq(AccountLocalAuthDO::getId, localAuth.getId())
                        .set(AccountLocalAuthDO::getResetToken, null)
                        .set(AccountLocalAuthDO::getResetTokenExpiresAt, null)
                        .set(AccountLocalAuthDO::getResetFailCount, 0));
                throw new BizException(ResultCode.RESET_CODE_FAIL_LIMIT);
            }
            accountLocalAuthMapper.update(null, new LambdaUpdateWrapper<AccountLocalAuthDO>()
                    .eq(AccountLocalAuthDO::getId, localAuth.getId())
                    .set(AccountLocalAuthDO::getResetFailCount, newFailCount));
            throw new BizException(ResultCode.RESET_CODE_INVALID);
        }

        // 校验通过：验证码一次性使用，作废并签发短期 verify_token
        accountLocalAuthMapper.update(null, new LambdaUpdateWrapper<AccountLocalAuthDO>()
                .eq(AccountLocalAuthDO::getId, localAuth.getId())
                .set(AccountLocalAuthDO::getResetToken, null)
                .set(AccountLocalAuthDO::getResetTokenExpiresAt, null)
                .set(AccountLocalAuthDO::getResetFailCount, 0));

        String verifyToken = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(
                RedisKeyConstants.RESET_VERIFY_TOKEN_PREFIX + verifyToken,
                String.valueOf(account.getId()),
                RESET_CODE_TTL_MINUTES, TimeUnit.MINUTES
        );
        return new VerifyResetCodeResponse(verifyToken);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BizException("两次输入的密码不一致");
        }
        String redisKey = RedisKeyConstants.RESET_VERIFY_TOKEN_PREFIX + request.getVerifyToken();
        String accountIdStr = redisTemplate.opsForValue().get(redisKey);
        if (accountIdStr == null) {
            throw new BizException(ResultCode.RESET_TOKEN_EXPIRED, "重置凭证已失效，请重新发起找回密码");
        }

        AccountLocalAuthDO localAuth = accountLocalAuthMapper.selectOne(
                new LambdaQueryWrapper<AccountLocalAuthDO>()
                        .eq(AccountLocalAuthDO::getAccountId, Integer.valueOf(accountIdStr))
                        .last("LIMIT 1"));
        if (localAuth == null) {
            throw new BizException(ResultCode.RESET_TOKEN_EXPIRED, "重置凭证已失效，请重新发起找回密码");
        }
        if (BCrypt.checkpw(request.getNewPassword(), localAuth.getPassword())) {
            throw new BizException(ResultCode.SAME_AS_OLD_PASSWORD);
        }

        accountLocalAuthMapper.update(null, new LambdaUpdateWrapper<AccountLocalAuthDO>()
                .eq(AccountLocalAuthDO::getId, localAuth.getId())
                .set(AccountLocalAuthDO::getPassword, BCrypt.hashpw(request.getNewPassword()))
                .set(AccountLocalAuthDO::getFailCount, 0)
                .set(AccountLocalAuthDO::getUnlockAt, null));

        redisTemplate.delete(redisKey);
        log.info("密码重置成功，accountId={}", accountIdStr);
    }

    private void recordLoginFailure(AccountDO account, HttpServletRequest httpRequest, String failReason) {
        logService.recordLoginLog(account.getTenantId(), account.getId(), account.getUsername(), 0,
                IpUtils.getClientIp(httpRequest), httpRequest.getHeader("User-Agent"), null, failReason);
    }

    /** tenantId=0 是平台级账号，没有对应的 tenant 行，固定展示"平台管理" */
    private String resolveTenantName(Integer tenantId) {
        if (tenantId == null || tenantId == 0) {
            return "平台管理";
        }
        TenantDO tenant = tenantMapper.selectById(tenantId);
        return tenant != null ? tenant.getName() : "";
    }

    private String summarizeUserAgent(String userAgentHeader) {
        try {
            UserAgent ua = UserAgentUtil.parse(userAgentHeader);
            if (ua == null) {
                return "未知设备";
            }
            String browser = ua.getBrowser() != null && !ua.getBrowser().isUnknown()
                    ? ua.getBrowser().toString() + " " + ua.getVersion() : "未知浏览器";
            String os = ua.getOs() != null && !ua.getOs().isUnknown() ? ua.getOs().toString() : "未知系统";
            return browser + " / " + os;
        } catch (Exception e) {
            return "未知设备";
        }
    }

    private void setRefreshCookie(HttpServletResponse response, String value, boolean secure) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(EXPIRES_REMEMBER)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
