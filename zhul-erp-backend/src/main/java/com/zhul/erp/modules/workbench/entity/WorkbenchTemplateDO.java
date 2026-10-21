package com.zhul.erp.modules.workbench.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 工作台模板：一个岗位看板由哪些组件组成（所有公司共用） */
@Data
@TableName("workbench_template")
public class WorkbenchTemplateDO {

    @TableId(type = IdType.AUTO)
    private Integer id;
    private String code;
    private String name;
    /** 职级排序，数字越小职级越高 */
    private Integer seniority;
    /** 组件清单 JSON：[{"key":组件编码,"size":"full|half"}] */
    private String widgets;
    private String description;
    private Integer sort;
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
