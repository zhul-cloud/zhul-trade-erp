package com.zhul.erp.modules.masterdata.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 客户列表 / 选择器出参 */
@Data
public class CustomerVO {
    private Long id;
    private String customerCode;
    private String name;
    /** 展示名：有客户名称用名称，否则用联系人名称（经商机登记的客户可能暂缺名称） */
    private String displayName;
    /** 客户名称是否未填写 */
    private boolean nameMissing;
    private String nameCn;
    private String shortName;
    private String country;
    private Integer customerRole;
    /** 潜力评级 1–5，0 为未评 */
    private Integer potentialRating;
    /** 最终经营等级（0-未成交、1-S、2-A、3-B、4-C） */
    private Integer bizGrade;
    private Integer bizGradeCalc;
    /** 人工调整：-1、0、1 */
    private Integer bizGradeAdjust;
    /** 调整原因与调整人，列表悬停显示 */
    private String bizGradeAdjustReason;
    private String bizGradeAdjustByName;
    private java.math.BigDecimal bizGradeScore;
    /** 已完成的档案模块（位） */
    private Integer profileMask;
    private Integer sourceChannel;
    private String aliLevel;
    private Long ownerId;
    private String ownerName;
    private String contactName;
    private String contactPhone;
    private String contactEmail;
    private Integer status;
    private LocalDateTime createTime;
    private String createBy;
    private LocalDateTime updateTime;
    private String updateBy;
}
