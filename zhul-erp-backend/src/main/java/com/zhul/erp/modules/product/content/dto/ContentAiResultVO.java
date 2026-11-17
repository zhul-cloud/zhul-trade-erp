package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

/** AI 生成结果：两份原文、核实说明、自评分与写入情况；canFacts / canMarketing 为当前用户能否写入 */
@Data
public class ContentAiResultVO {
    private Long id;
    private Long productId;
    private Integer status;
    private String factsFileName;
    private String factsMarkdown;
    private String marketingFileName;
    private String marketingMarkdown;
    private String summary;
    private Integer selfScore;
    private boolean factsImported;
    private boolean marketingImported;
    private boolean canFacts;
    private boolean canMarketing;
}
