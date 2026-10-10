package com.zhul.erp.modules.crm.service;

import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.crm.dto.ChangeStageRequest;
import com.zhul.erp.modules.crm.dto.CloseOpportunityRequest;
import com.zhul.erp.modules.crm.dto.OpportunityDetailVO;
import com.zhul.erp.modules.crm.dto.OpportunityPageQuery;
import com.zhul.erp.modules.crm.dto.OpportunityStageVO;
import com.zhul.erp.modules.crm.dto.OpportunityStatsVO;
import com.zhul.erp.modules.crm.dto.OpportunitySummaryVO;
import com.zhul.erp.modules.crm.dto.OpportunityUploadVO;
import com.zhul.erp.modules.crm.dto.OpportunityVO;
import com.zhul.erp.modules.crm.dto.RegisterOpportunityRequest;
import com.zhul.erp.modules.crm.dto.UpdateOpportunityRequest;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public interface OpportunityService {

    List<OpportunityStageVO> stages();

    /** 登记商机并创建客户；客户已存在时抛 CUSTOMER_DUPLICATE。返回商机 ID */
    Long register(RegisterOpportunityRequest req);

    void update(Long id, UpdateOpportunityRequest req);

    void changeStage(Long id, ChangeStageRequest req);

    void close(Long id, CloseOpportunityRequest req);

    void reopen(Long id);

    PageResult<OpportunityVO> page(OpportunityPageQuery query);

    OpportunitySummaryVO summary();

    OpportunityDetailVO detail(Long id);

    /** groupBy：channel / owner / date */
    OpportunityStatsVO stats(LocalDate from, LocalDate to, String groupBy);

    OpportunityUploadVO uploadAttachment(MultipartFile file);

    /** 下载用：附件必须属于本租户、数据权限内的该商机 */
    AttachmentFile attachmentFile(Long id, Long attachmentId);

    /**
     * 客户询盘关联来源商机时调用：返回该商机的客户 ID；商机不存在、不属于本租户或不在数据权限内时抛业务异常
     */
    Long customerIdOf(Long opportunityId);

    /**
     * 从商机创建了客户询盘后调用（与询盘写入同一事务）：已结束的商机不能再创建询盘；
     * 还没到有效阶段的（S1、S2）自动推进到第一个有效阶段并记入阶段记录
     */
    void onInquiryCreated(Long opportunityId, String inquiryCode);

    /** 询盘详情回显来源商机：本租户、未删除且在数据权限内时返回，否则返回 null */
    OpportunityVO findVisible(Long id);

    record AttachmentFile(Path path, String fileName, String contentType) {
    }
}
