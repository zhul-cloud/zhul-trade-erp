package com.zhul.erp.modules.product.candidate.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品候选的一条来源：所属租户 + 来源单据 */
@Data
@TableName("product_candidate_source")
public class ProductCandidateSourceDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long candidateId;
    /** 1-询盘、2-采购回填真实型号、3-采购回价有货、4-成交、5-手动、6-导入 */
    private Integer sourceType;
    private Long customerInquiryId;
    private Long inquiryItemId;
    private Long soId;
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
