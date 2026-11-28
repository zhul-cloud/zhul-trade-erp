package com.zhul.erp.modules.website.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 站点商品：一个商品在一个站点上的链接别名、售价、原有内容与上次发布快照；JSON 列以字符串保存 */
@Data
@TableName("website_product")
public class WebsiteProductDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long siteId;
    private Long productId;
    private Long listingTaskId;
    private String slug;
    /** 0-否、1-是：第一次发布成功或导入后锁定 */
    private Integer slugLocked;
    /** 手工售价、计算售价可以改回空：更新时总是写入，空值也写 */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal manualPrice;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private BigDecimal calcPrice;
    private String priceSource;
    /** 手工改的站点状态（instock、discont、legacy），空表示按生命周期 */
    private String siteStatus;
    private String originalContent;
    private LocalDateTime importedAt;
    private String erpFields;
    private String liveUrls;
    private String lastSnapshot;
    private Long lastReleaseId;
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
