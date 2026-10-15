package com.zhul.erp.modules.product.content.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 确认写入结果：逐个文件独立写入，某个失败不影响其他 */
@Data
public class ContentConfirmVO {
    private int succeeded;
    private int failed;
    private List<Item> items = new ArrayList<>();
    /** 一起上传并自动审核时：自动审核通过的技术问答条数、自动核对资料的商品数 */
    private Integer approvedFaqs;
    private Integer verifiedProducts;

    @Data
    public static class Item {
        private String fileName;
        /** facts 资料包、marketing 营销包 */
        private String type;
        private Long productId;
        private String productLabel;
        private String lang;
        /** 营销包平台：alibaba、mic、site、social；资料包为空 */
        private String platform;
        /** v2 营销文件拆出的部分：keywords（关键词库）或平台编码 */
        private String part;
        private boolean success;
        /** 成功时为写入摘要，失败时为原因 */
        private String message;
    }
}
