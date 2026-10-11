package com.zhul.erp.modules.product.content.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品内容任务（按公司，记录各平台营销写入；没有记录的商品视为营销未写入） */
@Data
@TableName("product_content_task")
public class ProductContentTaskDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long productId;
    /** 1-待生成、2-进行中、3-已完成 */
    private Integer status;
    private LocalDateTime zhAt;
    private String zhBy;
    /** 独立站英文、俄文 */
    private LocalDateTime enAt;
    private String enBy;
    private LocalDateTime ruAt;
    private String ruBy;
    private LocalDateTime alibabaAt;
    private String alibabaBy;
    private LocalDateTime micAt;
    private String micBy;
    private LocalDateTime socialAt;
    private String socialBy;
    private LocalDateTime downloadedAt;
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
