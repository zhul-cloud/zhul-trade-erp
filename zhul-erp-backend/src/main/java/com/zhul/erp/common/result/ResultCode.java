package com.zhul.erp.common.result;

import lombok.Getter;

@Getter
public enum ResultCode {
    SUCCESS(0, "ok"),
    FAIL(500, "系统错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    PARAM_ERROR(400, "参数错误"),
    USER_NOT_FOUND(1001, "用户不存在"),
    PASSWORD_ERROR(1002, "密码错误"),
    ACCOUNT_DISABLED(1003, "账号已禁用"),
    TENANT_EXPIRED(1004, "租户已到期"),
    TENANT_DISABLED(1005, "租户已禁用"),
    TOKEN_INVALID(1006, "Token无效"),
    DUPLICATE_KEY(1007, "数据已存在"),
    INVALID_CREDENTIALS(1008, "用户名或密码错误"),
    ACCOUNT_LOCKED(1009, "账号已被锁定"),
    LAST_ATTEMPT_WARNING(1010, "用户名或密码错误，再失败1次账号将被锁定30分钟"),
    SEND_TOO_FREQUENT(1011, "发送过于频繁，请稍后再试"),
    RESET_TOKEN_EXPIRED(1012, "验证码已过期，请重新发送"),
    RESET_CODE_INVALID(1013, "验证码错误，请重新输入"),
    RESET_CODE_FAIL_LIMIT(1014, "验证码错误次数过多，请重新发送验证码"),
    SAME_AS_OLD_PASSWORD(1015, "新密码不能与原密码相同"),
    NO_EMAIL_BOUND(1016, "该账号未绑定邮箱，请联系管理员重置密码"),
    REFRESH_TOKEN_INVALID(1017, "登录已过期，请重新登录");

    private final Integer code;
    private final String message;

    ResultCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
