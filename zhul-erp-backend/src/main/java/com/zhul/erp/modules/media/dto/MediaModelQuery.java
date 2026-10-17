package com.zhul.erp.modules.media.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/** 素材库型号列表的查询条件 */
@Data
public class MediaModelQuery {
    @Size(max = 100)
    private String keyword;
    /** 有无营销实拍：true 有、false 没有、空不限 */
    private Boolean hasMarketing;
    /** 有无用于发品的素材 */
    private Boolean hasListing;
    private Integer page;
    private Integer pageSize;
}
