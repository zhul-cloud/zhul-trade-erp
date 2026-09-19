package com.zhul.erp.modules.product.dto;

import lombok.Data;

/** FAQ 的来源不由客户端指定：平台账号手动新增恒为"人工撰写"，修改内容不改变来源。 */
@Data
public class SaveFaqRequest {
    private String question;
    private String answer;
    private Integer sortOrder;
}
