package com.zhul.erp.modules.system.migration.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 历史数据迁移批次（openspec migrate-feishu-history） */
@Data
@TableName("migration_batch")
public class MigrationBatchDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private String batchNo;
    private String bundleHash;
    /** 1-干跑、2-正式执行 */
    private Integer runMode;
    /** 1-运行中、2-成功、3-失败、4-已回滚 */
    private Integer status;
    /** 各阶段、各类数据的新建、跳过、失败数量（JSON） */
    private String stageStats;
    /** 与旧报表的核对结果（JSON） */
    private String verifyResult;
    private String errorText;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private Long operatorId;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
