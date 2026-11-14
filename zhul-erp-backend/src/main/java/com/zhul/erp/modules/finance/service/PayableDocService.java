package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.PayableVO;
import com.zhul.erp.modules.finance.entity.PayableDO;
import com.zhul.erp.modules.finance.repository.PayableMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import com.zhul.erp.modules.system.doclog.DocRefVO;
import com.zhul.erp.framework.security.PermissionChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 应付单详情、备注与附件（spec finance/accounts-payable「应付单详情」；change finance-ap-fx-docs） */
@Service
@RequiredArgsConstructor
public class PayableDocService {

    private static final String MENU = "/finance/payables";
    private static final Map<Integer, String> SOURCE_TYPES = Map.of(
            FinanceConstants.SOURCE_PURCHASE_ORDER, DocumentType.PO.name(),
            FinanceConstants.SOURCE_STATEMENT, DocumentType.ST.name(),
            FinanceConstants.SOURCE_EXPENSE, DocumentType.FE.name());

    private final PayableQueryService query;
    private final PayableMapper payableMapper;
    private final AttachmentService attachments;
    private final DocLogs docLogs;
    private final PermissionChecker perm;

    public PayableVO detail(Long id) {
        PayableVO vo = query.detail(id);
        PayableDO p = payableMapper.selectById(id);
        vo.setRemark(p.getRemark());
        vo.setFiles(attachments.listOf(AttachmentService.PAYABLE, id));
        vo.setRelated(related(vo));
        vo.setLogs(docLogs.list(DocumentType.AP, id));
        vo.setEditable(perm.canAccessMenu(MENU));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public PayableVO remark(Long id, String remark) {
        PayableDO p = editable(id);
        String text = remark == null ? "" : remark.trim();
        if (text.length() > 500) {
            throw new BizException("备注最多 500 字");
        }
        p.setRemark(text);
        payableMapper.updateById(p);
        docLogs.add(DocumentType.AP, id, "修改备注", text.isEmpty() ? "清空备注" : text);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PayableVO attach(Long id, List<Long> ids) {
        editable(id);
        attachments.attach(AttachmentService.PAYABLE, id, ids);
        docLogs.add(DocumentType.AP, id, "上传附件", ids.size() + " 个");
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public PayableVO removeAttachment(Long id, Long attachmentId) {
        editable(id);
        attachments.remove(AttachmentService.PAYABLE, id, attachmentId);
        docLogs.add(DocumentType.AP, id, "删除附件", "");
        return detail(id);
    }

    /** 应付单的备注附件不受状态限制（已付清、已关闭后也常要补发票） */
    private PayableDO editable(Long id) {
        PayableDO p = payableMapper.selectOne(new LambdaQueryWrapper<PayableDO>()
                .eq(PayableDO::getId, id)
                .eq(PayableDO::getTenantId, PiStore.tenantId())
                .isNull(PayableDO::getDeletedAt)
                .last("FOR UPDATE"));
        if (p == null) {
            throw new BizException("应付单不存在");
        }
        return p;
    }

    private List<DocRefVO> related(PayableVO vo) {
        List<DocRefVO> out = new ArrayList<>();
        String type = SOURCE_TYPES.get(vo.getSourceType());
        if (type != null && vo.getSourceId() != null) {
            out.add(new DocRefVO(type, vo.getSourceId(), vo.getSourceNo(), null, null));
        }
        Set<Long> seen = new HashSet<>();
        for (PayableVO.PaymentLine l : vo.getPayments()) {
            if (seen.add(l.getPaymentId())) {
                out.add(new DocRefVO(DocumentType.PY.name(), l.getPaymentId(), l.getPaymentNo(),
                        l.isVoided() ? 2 : 1, l.isVoided() ? "已作废" : "有效"));
            }
        }
        return out;
    }
}
