package com.zhul.erp.modules.warehouse.dto;

import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import lombok.Data;
import java.util.List;

/** 发货单详情 */
@Data
public class ShipmentVO {
    private ShipmentListVO shipment;
    private List<AttachmentVO> attachments;
    /** 在途时可以修改、作废 */
    private Boolean editable;
}
