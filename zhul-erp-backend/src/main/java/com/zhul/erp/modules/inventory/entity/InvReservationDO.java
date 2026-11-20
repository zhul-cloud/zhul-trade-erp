package com.zhul.erp.modules.inventory.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 库存预占 */
@Data
@TableName("inv_reservation")
public class InvReservationDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    /** 1-销售订单型号行、2-采购退货单行 */
    private Integer targetType;
    private Long targetId;
    private Long warehouseId;
    private Long productId;
    private Long batchId;
    private Integer stockStatus;
    private Integer quantity;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT)
    private String createBy;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private String updateBy;
}
