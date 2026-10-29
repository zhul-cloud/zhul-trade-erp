package com.zhul.erp.modules.system.migration.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 历史迁移的新旧编号对照：一条源记录可对应多条目标记录（如询盘单与它的型号行） */
@Data
@TableName("migration_id_map")
public class MigrationIdMapDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long batchId;
    private String sourceTable;
    private String sourceCode;
    private String sourceHash;
    private String targetTable;
    private Long targetId;
    /** 1-有效、2-已回滚 */
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
