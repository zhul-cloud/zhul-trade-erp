package com.zhul.erp.modules.sales.dto;

import com.zhul.erp.modules.sales.support.ItemRemarks;
import lombok.Data;

import java.time.LocalDateTime;

/** 型号备注：内容、最后修改人与时间（openspec item-remark）；没有备注时内容为空串 */
@Data
public class ItemRemarkVO {
    private String remark;
    private String remarkBy;
    private LocalDateTime remarkAt;

    public static ItemRemarkVO of(ItemRemarks.Remark r) {
        ItemRemarkVO vo = new ItemRemarkVO();
        vo.setRemark(r == null ? "" : r.text());
        vo.setRemarkBy(r == null ? "" : r.by());
        vo.setRemarkAt(r == null ? null : r.at());
        return vo;
    }
}
