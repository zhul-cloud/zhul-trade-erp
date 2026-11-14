package com.zhul.erp.modules.supply.dto;

import com.zhul.erp.modules.system.doclog.DocLogVO;
import lombok.Data;

import java.util.List;
import java.util.Map;

/** 需求对象详情 */
@Data
public class DemandDetailVO {
    private DemandVO demand;
    /** 最近证据（依据），没有为空 */
    private Map<String, Object> evidence;
    private List<ChannelVO> channels;
    private List<BoardVOs.TimelineItem> timeline;
    private List<DocLogVO> logs;
    private List<ProjectVO> projects;
    /** 所在阶段对应的作战地图节点 */
    private List<String> nodes;
    private boolean canEdit;
    private boolean canCoverage;
    private boolean canSeeSupplier;
}
