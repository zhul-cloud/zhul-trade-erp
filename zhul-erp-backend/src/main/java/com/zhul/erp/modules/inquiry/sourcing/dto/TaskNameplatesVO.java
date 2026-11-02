package com.zhul.erp.modules.inquiry.sourcing.dto;

import com.zhul.erp.modules.inquiry.customerinquiry.dto.AttachmentVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 询价任务能看到的铭牌图片：任务内型号对应的，以及同一询盘单里未对应型号的（spec inquiry/sourcing-quote「采购查看铭牌图片」） */
@Data
public class TaskNameplatesVO {
    private List<ItemPlates> items = new ArrayList<>();
    private List<AttachmentVO> unmatched = new ArrayList<>();

    @Data
    public static class ItemPlates {
        private Long itemId;
        private List<AttachmentVO> files = new ArrayList<>();
    }
}
