package com.zhul.erp.modules.system.doclog;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** 单据操作日志：销售退货单、退货入库单、采购退货单、退款申请等单据详情页的操作记录，与操作人的同一事务写入 */
@Component
@RequiredArgsConstructor
public class DocLogs {

    private static final int MAX = 1000;

    private final DocumentLogMapper mapper;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;

    public void add(DocumentType type, Long docId, String action, String content) {
        DocumentLogDO l = new DocumentLogDO();
        l.setTenantId(PiStore.tenantId());
        l.setDocType(type.name());
        l.setDocId(docId);
        l.setAction(action);
        l.setContent(content == null ? "" : content.length() > MAX ? content.substring(0, MAX) : content);
        Long me = currentUser.resolve();
        l.setOperatorId(me == null ? 0L : me);
        mapper.insert(l);
    }

    /** 新的在前 */
    public List<DocLogVO> list(DocumentType type, Long docId) {
        List<DocumentLogDO> rows = mapper.selectList(new LambdaQueryWrapper<DocumentLogDO>()
                .eq(DocumentLogDO::getTenantId, PiStore.tenantId())
                .eq(DocumentLogDO::getDocType, type.name())
                .eq(DocumentLogDO::getDocId, docId)
                .isNull(DocumentLogDO::getDeletedAt)
                .orderByDesc(DocumentLogDO::getId));
        Map<Long, String> users = lookups.userNames(rows.stream().map(DocumentLogDO::getOperatorId).toList());
        return rows.stream().map(l -> {
            DocLogVO v = new DocLogVO();
            v.setId(l.getId());
            v.setAction(l.getAction());
            v.setContent(l.getContent());
            v.setOperatorName(users.get(l.getOperatorId()));
            v.setCreateTime(l.getCreateTime());
            return v;
        }).toList();
    }
}
