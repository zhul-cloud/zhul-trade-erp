package com.zhul.erp.modules.aitask.exception;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.ResultCode;
import com.zhul.erp.modules.aitask.constants.AiConfigConstants;

/** 当前租户已配置的连接方式都不可用（订阅未授权或未登录、暂停中）；提示不说明原因 */
public class AiUnavailableException extends BizException {
    public AiUnavailableException() {
        super(ResultCode.PARAM_ERROR, AiConfigConstants.UNAVAILABLE_MESSAGE);
    }
}
