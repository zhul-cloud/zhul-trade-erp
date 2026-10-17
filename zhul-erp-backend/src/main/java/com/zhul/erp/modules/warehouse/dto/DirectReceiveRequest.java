package com.zhul.erp.modules.warehouse.dto;

import com.zhul.erp.modules.media.dto.AttachmentModelsRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDate;
import java.util.List;

/** 直接收货：采购员还没登记发货，仓库选采购订单直接验收 */
@Data
public class DirectReceiveRequest {
    @NotNull(message = "请选择采购订单")
    private Long poId;
    @NotNull(message = "请填写收货日期")
    private LocalDate receivedDate;
    @Size(max = 300, message = "差异说明不能超过300个字")
    private String note;
    @NotEmpty
    @Valid
    private List<Line> items;
    private List<Long> attachmentIds;
    /** 不良品照片（有不良数量时才能上传），素材库「到货异常」 */
    private List<Long> defectAttachmentIds;
    /** 每个附件标注的型号；不提交时默认为这次入库的全部型号 */
    @Valid
    private List<AttachmentModelsRequest> attachmentModels;

    @Data
    public static class Line {
        @NotNull
        private Long poItemId;
        @NotNull(message = "请填写实收数量")
        private Integer receivedQty;
        @NotNull(message = "请填写合格数量")
        private Integer qualifiedQty;
        /** 不再使用：不良 = 实收 − 合格，由系统计算 */
        private Integer defectiveQty;
        @Size(max = 300, message = "说明不能超过300个字")
        private String note;
    }
}
