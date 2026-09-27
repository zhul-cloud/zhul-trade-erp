package com.zhul.erp.modules.product.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 商品平台参考价表（一对一） */
@Data
@TableName("product_reference_price")
public class ProductReferencePriceDO {
    /** 主键 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 租户ID（0=平台级共享，本模块所有数据均为0） */
    private Integer tenantId;
    /** 商品ID，关联product.id；一个商品最多一行 */
    private Long productId;
    /** 参考价（原币），必须大于0；这是平台层面的参考价，不是任何租户的报价或售价 */
    private BigDecimal priceOriginal;
    /** 币种（ISO 4217，如USD/CNY）；金额必须带币种 */
    private String currencyCode;
    /** 汇率（原币→本位币CNY）；币种为CNY时为1；非CNY且汇率未维护时为NULL */
    private BigDecimal exchangeRate;
    /** 参考价（本位币），由原币与汇率计算，HALF_UP保留2位；汇率未维护时为NULL（展示为"未计算"，不是0元） */
    private BigDecimal priceCny;
    /** 价格来源，如 厂商官网目录价、eBay参考价 */
    private String priceSource;
    /** 取价日期；未知为NULL */
    private LocalDate priceDate;
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
