package com.zhul.erp.modules.aitask.exception;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.ResultCode;
import com.zhul.erp.modules.aitask.constants.AiConfigConstants;

/** 当前租户选用的平台订阅不可用（未授权或平台未登录）；提示不说明原因，不自动切换其他方式 */
public class AiUnavailableException extends BizException {
    public AiUnavailableException() {
        super(ResultCode.PARAM_ERROR, AiConfigConstants.UNAVAILABLE_MESSAGE);
    }
}
