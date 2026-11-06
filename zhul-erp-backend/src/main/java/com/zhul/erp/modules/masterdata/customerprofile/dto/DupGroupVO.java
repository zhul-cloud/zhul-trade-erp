package com.zhul.erp.modules.masterdata.customerprofile.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 疑似重复的一组客户 */
@Data
public class DupGroupVO {
    private String groupKey;
    /** NAME-同名同国家、EMAIL-同邮箱、PHONE-同 WhatsApp 或电话；MANUAL-手动合并选客户（第一个是当前客户） */
    private String reason;
    private String reasonText;
    private List<Member> members = new ArrayList<>();

    @Data
    public static class Member {
        private Long id;
        private String customerCode;
        private String name;
        private String country;
        private Long ownerId;
        private String ownerName;
        private Integer profileDone;
        private Long inquiryCount;
        private Long orderCount;
        /** 手动合并选客户时，与当前客户相同或相近之处，如「名称相近」「邮箱相同」；没有为空 */
        private String hint;
    }
}
