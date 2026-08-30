package com.zhul.erp.common.constants;

public interface RedisKeyConstants {
    String TOKEN_PREFIX = "zhul:erp:token:";
    String RATE_PREFIX = "zhul:erp:rate:";
    String DICT_PREFIX = "zhul:erp:dict:";
    String LOCK_PREFIX = "zhul:erp:lock:";
    String CAPTCHA_PREFIX = "zhul:erp:captcha:";
    String REFRESH_TOKEN_PREFIX = "zhul:erp:refresh:";
    String LOGIN_IDEMPOTENCY_PREFIX = "zhul:erp:lock:login-idempotency:";
    String RESET_CODE_COOLDOWN_PREFIX = "zhul:erp:lock:reset-code-cooldown:";
    String RESET_VERIFY_TOKEN_PREFIX = "zhul:erp:reset-verify:";
}
