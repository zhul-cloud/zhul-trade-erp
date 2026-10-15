package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 询盘单上未完成的追加型号批次 */
@Data
public class AppendVO {
    private Long id;
    /** 1-解析中、2-待确认、3-解析失败 */
    private Integer status;
    /** 1-AI、2-手动 */
    private Integer parseMode;
    private String parseError;
    private String rawContent;
    private LocalDateTime createTime;
}
