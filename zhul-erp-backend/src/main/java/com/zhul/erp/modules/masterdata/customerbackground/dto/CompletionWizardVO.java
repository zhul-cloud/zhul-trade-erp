package com.zhul.erp.modules.masterdata.customerbackground.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 档案补全右侧：一个客户的四步补全向导（spec crm/customer-background「档案补全」）。
 * 每一步只列这个客户还缺的核心项，已经有值的不显示；items 为空即这一步已完成。
 */
@Data
public class CompletionWizardVO {
    private Long customerId;
    private String customerCode;
    private String name;
    private String country;
    /** 完整度：已完成的模块数（满分 5） */
    private Integer done;
    /** 联系人字段对应的联系人（主联系人，没有主联系人时取第一个）；没有联系人为空 */
    private Long contactId;
    private String contactName;
    private List<Step> steps = new ArrayList<>();

    @Data
    public static class Step {
        /** 1-系统已经知道、2-AI 背调能查到、3-你自己就能判断、4-只能问客户 */
        private Integer step;
        private String name;
        private List<Item> items = new ArrayList<>();
        /** 第①步：系统已自动补上的项（如「常用品牌（询盘推导）」） */
        private List<String> autoFilled;
        /** 第①步：国家资料缺失，提示「去维护国家资料」 */
        private Boolean countryProfileMissing;
        /** 第②步：180 天内最近一份背调报告（不含正文），没有为空 */
        private BackgroundReportDetailVO report;
        /** 第④步：全部问法拼成一段，供「复制全部」 */
        private String copyAll;
    }

    @Data
    public static class Item {
        /** 画像字段键；联系人姓名为 name（模块 4） */
        private String key;
        private String label;
        private Integer module;
        /** 是否联系人字段（写在 contactId 对应的联系人上） */
        private Boolean contact;
        /** 「已经问了，等回复」用的字段来源键：客户字段为 key，联系人字段为 contact.{联系人ID}.{key} */
        private String sourceKey;
        /** 第④步：英文问法 */
        private String question;
        private LocalDateTime askedAt;
        private Integer askedDays;
        private String askedByName;
    }
}
