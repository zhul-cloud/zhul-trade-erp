package com.zhul.erp.modules.masterdata.customerbackground.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 背调前检查（spec crm/customer-background「背调前检查」）：充足度与逐项结果 */
@Data
public class BackgroundPrecheckVO {
    /** 充足度：LOW 很少、MEDIUM 一般、HIGH 充足 */
    public static final String LOW = "LOW";
    public static final String MEDIUM = "MEDIUM";
    public static final String HIGH = "HIGH";

    private String level;
    /** 已有的关键信息项数（0–3） */
    private Integer keyCount;
    private List<Item> items = new ArrayList<>(8);

    /** 一个检查项 */
    @Data
    public static class Item {
        /** 状态：OK 已有、MISSING 缺失、WARN 有但用不上（只有免费邮箱） */
        public static final String OK = "OK";
        public static final String MISSING = "MISSING";
        public static final String WARN = "WARN";

        /** basic、website、companyEmail、inquiryText、linkedin、companyInfo */
        private String key;
        private String label;
        /** 关键信息（决定充足度），否则为建议补充 */
        private Boolean keyInfo;
        private String state;
        /** 已有时的显示值 */
        private String value;
        /** 缺失或 WARN 时的原因 */
        private String reason;
        /** 去补充的位置：profile:1 / profile:2（档案编辑页签）、contact（联系人）；没有入口时为空 */
        private String fixTarget;
    }
}
