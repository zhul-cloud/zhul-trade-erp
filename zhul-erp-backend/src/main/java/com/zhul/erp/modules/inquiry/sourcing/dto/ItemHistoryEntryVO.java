package com.zhul.erp.modules.inquiry.sourcing.dto;

import com.zhul.erp.modules.inquiry.customerinquiry.dto.PriceRecordVO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 型号回价的一条修改记录：某位采购的一次回价（一个版本），或采购负责人指定 / 恢复成本价 */
@Data
public class ItemHistoryEntryVO {
    /** QUOTE-回价版本、COST-成本价调整、REVIEW-审核兼职回价（通过 / 退回） */
    private String type;
    private LocalDateTime time;
    private String operatorName;
    /** QUOTE：首次回价 / 修改回价；COST：指定采购成本价 / 恢复自动 */
    private String action;
    /** QUOTE：是否为该采购当前有效的版本 */
    private Boolean current;
    /** QUOTE：这一版的全部记录（渠道、店铺按「查看货源信息」权限返回） */
    private List<PriceRecordVO> quotes;
    /** COST / REVIEW：说明，如「指定为 ¥940.00（林熙）」「推荐 ¥2,640.00，作废 1 条」 */
    private String note;
}
