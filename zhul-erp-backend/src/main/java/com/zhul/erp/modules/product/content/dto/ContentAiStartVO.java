package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 发起结果：新建了几条记录，跳过的商品与原因 */
@Data
public class ContentAiStartVO {
    private int created;
    private List<Skipped> skipped = new ArrayList<>();

    @Data
    public static class Skipped {
        private Long productId;
        private String reason;
    }
}
