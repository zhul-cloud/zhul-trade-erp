package com.zhul.erp.modules.crm.service;

import com.zhul.erp.framework.storage.FileStorageService.FileLink;
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

    /** 删除商机（软删除）：有关联询盘时不能删除；客户档案保留 */
    void delete(Long id);

    PageResult<OpportunityVO> page(OpportunityPageQuery query);

    OpportunitySummaryVO summary();

    OpportunityDetailVO detail(Long id);

    /** groupBy：channel / owner / date */
    OpportunityStatsVO stats(LocalDate from, LocalDate to, String groupBy);

    OpportunityUploadVO uploadAttachment(MultipartFile file);

    /** 附件所在的存储与文件键：附件必须属于本租户、数据权限内的该商机（转询盘时复制附件用） */
    AttachmentFile attachmentFile(Long id, Long attachmentId);

    /** 附件的临时访问链接：download=true 时强制下载，否则预览 */
    FileLink attachmentLink(Long id, Long attachmentId, boolean download);

    /**
     * 询盘单关联来源商机时调用：返回该商机的客户 ID；商机不存在、不属于本租户或不在数据权限内时抛业务异常
     */
    Long customerIdOf(Long opportunityId);

    /**
     * 从商机创建了询盘单后调用（与询盘写入同一事务）：已结束的商机不能再创建询盘；
     * 还没到有效阶段的（S1、S2）自动推进到第一个有效阶段并记入阶段记录
     */
    void onInquiryCreated(Long opportunityId, String inquiryCode);

    /**
     * 关联询盘生成报价单：商机进行中且还在「已报价」之前时推进到「已报价」，之后的阶段不回退；
     * 商机已结束、不存在或已删除时不处理，不影响报价。
     */
    void onQuoted(Long opportunityId, String quotationNo);

    /**
     * 自动推进客户进行中的商机（记跟进勾选、PI 发送）：只前进不回退；from 不为空时只在 from 及以后推进。
     * 不单独开事务，在调用方事务内执行。返回推进后的阶段；没有推进返回 null。
     */
    String autoAdvance(Long customerId, String toStage, String from, String note);

    /**
     * 收款到账自动赢单（按客户关联，design D10）：客户进行中的商机标记赢单，不在最后一个进行中阶段时先补一条推进记录。
     * 不单独开事务，在调用方事务内执行。返回赢单的商机 ID；没有进行中商机返回 null。
     */
    Long autoWin(Long customerId, String piNo);

    /** 新建询盘时可关联的商机：这个客户进行中、数据权限内的商机，最近更新的在前，带附件 */
    List<OpportunityDetailVO> activeOfCustomer(Long customerId);

    /** 询盘详情回显来源商机：本租户、未删除且在数据权限内时返回，否则返回 null */
    OpportunityVO findVisible(Long id);

    record AttachmentFile(String storage, String fileKey, String fileName, String contentType) {
    }
}
