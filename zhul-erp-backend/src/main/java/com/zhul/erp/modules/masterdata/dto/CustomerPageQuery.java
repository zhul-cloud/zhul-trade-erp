package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

@Data
public class CustomerPageQuery {
    private Integer page = 1;
    private Integer pageSize = 10;
    /** 名称关键词（旧参数，保留兼容），同 name */
    private String keyword;
    private String customerCode;
    /** 模糊匹配英文名称、中文名称、简称 */
    private String name;
    /** 主要联系人姓名，模糊匹配 */
    private String contactName;
    private String country;
    private Integer customerRole;
    /** 经营等级（0-未成交、1-S、2-A、3-B、4-C） */
    private Integer bizGrade;
    private Integer potentialRating;
    /** 建档状态：done-已建档、pending-待完善 */
    private String profileStatus;
    /** 缺的模块（1–5），多选时要全部都缺 */
    private java.util.List<Integer> missingModules;
    /** 排序字段：updateTime（默认）、bizGrade、potentialRating */
    private String sortField;
    /** asc / desc，默认 desc */
    private String sortOrder;
    private Integer sourceChannel;
    private Long ownerId;
    private Integer status;
}
