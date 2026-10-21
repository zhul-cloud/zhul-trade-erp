package com.zhul.erp.modules.listing.repository;

import com.zhul.erp.modules.listing.entity.ListingTaskDO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/** 列表行：任务加上查询时算出的三个提醒 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ListingTaskRow extends ListingTaskDO {
    /** 待发布、本型号没有用于发品的素材、也没选参考素材 */
    private Boolean needMedia;
    /** 已发布、之后营销内容又写入过 */
    private Boolean contentUpdated;
    /** 用参考素材发布、本型号已有用于发品的素材 */
    private Boolean needReplace;
}
