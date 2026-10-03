package com.zhul.erp.modules.inquiry.sourcing.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 导入询价结果的文件记录，用于追溯 */
@Data
@TableName("sourcing_import")
public class SourcingImportDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long taskId;
    private String fileName;
    private String fileKey;
    private Long importedBy;
    private Long onBehalfOf;
    private Integer rowCount;
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
