package com.zhul.erp.modules.inquiry.sourcing.service;

import com.zhul.erp.modules.inquiry.sourcing.dto.ReviewApproveRequest;
import com.zhul.erp.modules.inquiry.sourcing.dto.ReviewDetailVO;
import com.zhul.erp.modules.inquiry.sourcing.dto.ReviewRejectRequest;

/** 兼职回价审核：采购负责人逐型号选推荐、作废记录后通过，或退回兼职修改 */
public interface QuoteReviewService {

    /** 任务中待审核的型号，按型号 + 兼职采购分组 */
    ReviewDetailVO detail(Long taskId);

    void approve(Long taskId, ReviewApproveRequest req);

    void reject(Long taskId, ReviewRejectRequest req);
}
