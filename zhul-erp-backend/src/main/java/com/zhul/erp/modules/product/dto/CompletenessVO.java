package com.zhul.erp.modules.product.dto;

import lombok.Data;

import java.util.List;

/** 商品档案完整度（10 个模块），只对平台账号返回。 */
@Data
public class CompletenessVO {
    /** 已完成的模块数 */
    private Integer done;
    private Integer total;
    /** 按固定顺序列出全部 10 个模块及其是否完成 */
    private List<Module> modules;
    /** 未完成模块的 key，顺序同 modules */
    private List<String> missing;

    @Data
    public static class Module {
        /** basic、media、specifications、logistics、customs、referencePrice、relationships、documents、applications、faq */
        private String key;
        private Boolean done;
    }
}
