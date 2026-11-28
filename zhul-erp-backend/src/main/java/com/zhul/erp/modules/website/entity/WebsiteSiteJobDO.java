package com.zhul.erp.modules.website.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 站点的导入、比对任务；JSON 列以字符串保存 */
@Data
@TableName("website_site_job")
public class WebsiteSiteJobDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long siteId;
    /** import-导入现有商品、compare-一致性比对 */
    private String jobType;
    private String jobNo;
    /** 1-执行中、2-已完成、3-失败、4-已确认 */
    private Integer status;
    private String result;
    private String accepted;
    private String error;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
