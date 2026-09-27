package com.zhul.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品FAQ表 */
@Data
@TableName("product_faq")
public class ProductFaqDO {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID（0=平台级共享，本模块所有数据均为0） */
    private Integer tenantId;
    /** 商品ID，关联product.id */
    private Long productId;
    /** 问题（纯文本） */
    private String question;
    /** 答案（纯文本）；只描述商品本身，不含卖家自己的质保/库存/发货/联系方式承诺 */
    private String answer;
    /** 来源（1-品类通用模板生成、2-人工撰写或已人工审核确认、3-AI辅助生成待审核）；3的记录不对租户账号返回，需平台账号审核后改为2 */
    private Integer source;
    /** 审核人；由待审核确认为2时填写 */
    private String reviewedBy;
    /** 审核时间 */
    private LocalDateTime reviewedAt;
    /** 排序 */
    private Integer sortOrder;
    /** 软删除时间，NULL表示未删除 */
    private LocalDateTime deletedAt;
    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 创建人 */
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    /** 更新人 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
