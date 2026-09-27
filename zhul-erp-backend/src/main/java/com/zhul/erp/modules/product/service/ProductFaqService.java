package com.zhul.erp.modules.product.service;

import com.zhul.erp.modules.product.dto.FaqVO;
import com.zhul.erp.modules.product.dto.SaveFaqRequest;

import java.util.List;

public interface ProductFaqService {

    /** 租户账号读不到来源为"AI 辅助生成待审核"的 FAQ，过滤在服务端完成 */
    List<FaqVO> list(Long productId);

    /** 平台账号手动新增，来源恒为"人工撰写" */
    FaqVO create(Long productId, SaveFaqRequest req);

    /** 修改问题和答案，不改变来源 */
    FaqVO update(Long productId, Long itemId, SaveFaqRequest req);

    void delete(Long productId, Long itemId);

    /** 审核确认待审核 FAQ：来源 3→2，记录审核人和审核时间；只适用于待审核的 FAQ */
    FaqVO approve(Long productId, Long itemId);
}
