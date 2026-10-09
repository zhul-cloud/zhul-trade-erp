package com.zhul.erp.modules.warehouse.dto;

import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import lombok.Data;

import java.util.List;

/** 处理到货差异时看的验收证据：仓库的差异说明与验收照片、视频 */
@Data
public class EvidenceVO {
    private String note;
    private List<AttachmentVO> attachments;
}
