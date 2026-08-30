package com.zhul.erp.modules.system.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class OperateLogDetailVO {
    private Long id;
    private String operatorName;
    private String menu;
    private String operation;
    private Integer result;
    private String ip;
    private LocalDateTime operateTime;

    /** content 字段为 null（无详细内容记录） */
    private Boolean contentEmpty;
    /** content JSON 解析失败，需展示原始字符串 + 异常提示 */
    private Boolean contentBroken;
    private String rawContent;

    private JsonNode before;
    private JsonNode after;
}
