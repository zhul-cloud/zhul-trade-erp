package com.zhul.erp.modules.website.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 发布单：一个站点一次发布的商品与执行结果；JSON 列以字符串保存 */
@Data
@TableName("website_release")
public class WebsiteReleaseDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long siteId;
    private String releaseNo;
    /** 1-排队中、2-执行中、3-已完成、4-失败、5-部署失败 */
    private Integer status;
    private Integer itemCount;
    /** 发给发布服务的任务号：发布单号-第几次执行 */
    private String jobNo;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String commitSha;
    private String failedStep;
    private String error;
    private String warnings;
    private LocalDateTime deletedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
