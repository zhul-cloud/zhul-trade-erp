package com.zhul.erp.modules.purchase.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.purchase.dto.AssignRequirementsRequest;
import com.zhul.erp.modules.purchase.dto.GeneratePreviewVO;
import com.zhul.erp.modules.purchase.dto.GeneratePurchaseRequest;
import com.zhul.erp.modules.purchase.dto.RequirementPageQuery;
import com.zhul.erp.modules.purchase.dto.RequirementStatsVO;
import com.zhul.erp.modules.purchase.dto.RequirementVO;
import com.zhul.erp.modules.purchase.dto.SplitRequirementRequest;

import java.util.List;

public interface PurchaseRequirementService {

    PageResult<RequirementVO> page(RequirementPageQuery q);

    RequirementStatsVO stats();

    void split(Long id, SplitRequirementRequest req);

    void assign(AssignRequirementsRequest req);

    GeneratePreviewVO preview(List<Long> ids);

    /** 返回生成或追加到的草稿采购单 ID */
    List<Long> generate(GeneratePurchaseRequest req);
}
