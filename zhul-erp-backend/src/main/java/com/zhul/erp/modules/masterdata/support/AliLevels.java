package com.zhul.erp.modules.masterdata.support;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.masterdata.constants.CustomerConstants;
import org.springframework.util.StringUtils;

import java.util.Objects;

/** 阿里等级（客户与商机共用）：只有来源为阿里巴巴国际站时保存，其余清空 */
public final class AliLevels {

    private AliLevels() {
    }

    public static String normalize(Integer channel, String level) {
        if (!Objects.equals(channel, CustomerConstants.CHANNEL_ALIBABA) || !StringUtils.hasText(level)) {
            return "";
        }
        if (!CustomerConstants.ALI_LEVELS.contains(level.trim())) {
            throw new BizException("阿里等级不正确");
        }
        return level.trim();
    }
}
