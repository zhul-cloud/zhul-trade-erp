package com.zhul.erp.modules.aitask.dto;

import lombok.Data;

/** 一种连接方式的设置；Key 只返回是否已填写与脱敏值 */
@Data
public class AiProfileVO {
    /** 是否已保存过这一种的设置 */
    private boolean saved;
    private boolean hasKey;
    private String apiKeyMasked = "";
    private String parseModel = "";
    private String translateModel = "";
    private String parseEffort = "";
    private String translateEffort = "";
    private Integer maxWebSearches;
}
