package com.zhul.erp.modules.aitask.exception;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.ResultCode;
import com.zhul.erp.modules.aitask.constants.AiConfigConstants;

/** 当前租户没有 AI 配置（或 API 方式没有填写 Key）；用户发起的 AI 操作直接提示，副作用任务跳过 */
public class AiNotConfiguredException extends BizException {
    public AiNotConfiguredException() {
        super(ResultCode.PARAM_ERROR, AiConfigConstants.NOT_CONFIGURED_MESSAGE);
    }
}
