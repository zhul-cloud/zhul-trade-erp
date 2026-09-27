package com.zhul.erp.modules.masterdata.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 供应商附件。文件在私有目录，fileKey 不对外返回，只能经下载接口访问。 */
@Data
@TableName("supplier_attachment")
public class SupplierAttachmentDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Integer tenantId;
    private Long supplierId;
    /** 附件类型（1-营业执照、2-开户许可证、3-资质证书、4-合同、5-其他） */
    private Integer category;
    private String fileName;
    /** 私有存储目录下的相对路径 */
    private String fileKey;
    private Long fileSize;
    private String contentType;
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
