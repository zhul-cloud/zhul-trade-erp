package com.zhul.erp.modules.warehouse.dto;

import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.system.doclog.DocLogVO;
import com.zhul.erp.modules.system.doclog.DocRefVO;
import lombok.Data;
import java.util.List;

/** 发货单详情 */
@Data
public class ShipmentVO {
    private ShipmentListVO shipment;
    private List<AttachmentVO> attachments;
    /** 在途时可以修改、作废 */
    private Boolean editable;
    /** 关联单据：采购订单、采购入库单、直发的出运单、采购需求对应的销售订单 */
    private List<DocRefVO> related;
    private List<DocLogVO> logs;
}
