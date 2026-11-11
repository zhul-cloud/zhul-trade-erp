package com.zhul.erp.modules.inquiry.customerinquiry.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** AI 解析输出 */
@Data
public class AiParseOutputDTO {
    private List<AiParseGroupDTO> groups = new ArrayList<>();
    /** 大附件分批解析时没能解析的部分（某个附件、某页或某批）；没有时为空 */
    private List<Unparsed> unparsed = new ArrayList<>();

    /** 未解析的部分：文件名、范围（如「第 15 页」，整个文件时为空）、原因 */
    @Data
    public static class Unparsed {
        private String file;
        private String range;
        private String reason;
    }
}
