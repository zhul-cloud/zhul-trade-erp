package com.zhul.erp.modules.sales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.utils.MigratedGuard;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.FileStorageService.FileLink;
import com.zhul.erp.framework.storage.StorageArea;
import com.zhul.erp.framework.storage.StorageKeys;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.entity.FxSettlementAllocationDO;
import com.zhul.erp.modules.finance.entity.FxSettlementDO;
import com.zhul.erp.modules.finance.repository.FxSettlementAllocationMapper;
import com.zhul.erp.modules.finance.repository.FxSettlementMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.ReceiptDocVO;
import com.zhul.erp.modules.sales.dto.ReceiptVO;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.sales.support.ReceiptDocs;
import com.zhul.erp.modules.sales.support.ReceiptRows;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import com.zhul.erp.modules.system.doclog.DocRefVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 收款单详情、备注与补充附件（change finance-cash-docs）。
 * 有「收款管理」菜单的看全部、可维护备注附件；其他人只读查看自己数据权限内 PI 或订单的收款单，看不到未认领的。
 */
@Service
@RequiredArgsConstructor
public class ReceiptDocService {

    public static final String MENU = "/finance/receipts";
    private static final String NOT_FOUND = "收款单不存在";

    private final PaymentReceiptMapper receiptMapper;
    private final ProformaInvoiceMapper piMapper;
    private final SalesOrderMapper orderMapper;
    private final FxSettlementAllocationMapper allocationMapper;
    private final FxSettlementMapper settlementMapper;
    private final ReceiptRows receiptRows;
    private final ReceiptDocs receiptDocs;
    private final AttachmentService attachments;
    private final DocLogs docLogs;
    private final FileStorageService fileStorage;
    private final PermissionChecker perm;
    private final DataScopeResolver dataScopeResolver;
    private final ObjectMapper objectMapper;

    public ReceiptDocVO detail(Long id) {
        PaymentReceiptDO r = visible(id);
        ReceiptDocVO vo = new ReceiptDocVO();
        vo.setReceipt(receiptRows.of(List.of(r)).get(0));
        vo.setFiles(files(r).stream().map(f -> {
            ReceiptDocVO.DocFile d = new ReceiptDocVO.DocFile();
            d.setIndex(f.index());
            d.setFileName(f.file().getFileName());
            d.setSource(f.source());
            return d;
        }).toList());
        vo.setAttachments(attachments.listOf(AttachmentService.PAYMENT_RECEIPT, id));
        List<ReceiptDocVO.Settlement> settlements = settlements(r);
        vo.setSettlements(settlements);
        if (!"CNY".equals(r.getCurrencyCode()) && r.getSettleStatus() != null
                && r.getSettleStatus() != SalesConstants.SETTLE_NA) {
            BigDecimal settled = settlements.stream().map(ReceiptDocVO.Settlement::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal settledCny = settlements.stream().map(ReceiptDocVO.Settlement::getAmountCny).reduce(BigDecimal.ZERO, BigDecimal::add);
            vo.setUnsettledAmount(nz(r.getNetAmount()).subtract(settled));
            vo.setUnsettledAmountCny(nz(r.getNetAmountCny()).subtract(settledCny));
        }
        vo.setRelated(related(r, settlements));
        vo.setLogs(docLogs.list(DocumentType.SK, id));
        vo.setEditable(finance() && r.getStatus() == SalesConstants.RECORD_VALID && !MigratedGuard.isMigrated(r.getMigrated()));
        return vo;
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiptDocVO remark(Long id, String remark) {
        PaymentReceiptDO r = editable(id);
        String text = remark == null ? "" : remark.trim();
        if (text.length() > 500) {
            throw new BizException("备注最多 500 字");
        }
        r.setNote(text);
        receiptMapper.updateById(r);
        receiptDocs.log(r, "修改备注", text.isEmpty() ? "清空备注" : text);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiptDocVO attach(Long id, List<Long> ids) {
        PaymentReceiptDO r = editable(id);
        attachments.attach(AttachmentService.PAYMENT_RECEIPT, id, ids);
        receiptDocs.log(r, "上传附件", ids.size() + " 个");
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public ReceiptDocVO removeAttachment(Long id, Long attachmentId) {
        PaymentReceiptDO r = editable(id);
        attachments.remove(AttachmentService.PAYMENT_RECEIPT, id, attachmentId);
        receiptDocs.log(r, "删除附件", "");
        return detail(id);
    }

    /** 到账凭证与水单的访问链接：index 与详情里 files 的 index 一致 */
    public FileLink fileLink(Long id, int index) {
        PaymentReceiptDO r = visible(id);
        Indexed f = files(r).stream().filter(x -> x.index() == index).findFirst()
                .orElseThrow(() -> new BizException("附件不存在"));
        if (!StorageKeys.owned(StorageArea.FINANCE, f.file().getFileKey(), r.getTenantId())) {
            throw new BizException("附件不存在");
        }
        return fileStorage.link(f.file().getStorage(), f.file().getFileKey(), f.file().getFileName(), false);
    }

    // ---------------------------------------------------------------- 可见范围

    private boolean finance() {
        return perm.canAccessMenu(MENU);
    }

    private PaymentReceiptDO visible(Long id) {
        PaymentReceiptDO r = id == null ? null : receiptMapper.selectById(id);
        if (r == null || r.getDeletedAt() != null || !Objects.equals(r.getTenantId(), PiStore.tenantId())
                || r.getKind() != SalesConstants.KIND_RECEIPT) {
            throw new BizException(NOT_FOUND);
        }
        if (finance() || ownerVisible(r)) {
            return r;
        }
        throw new BizException(NOT_FOUND);
    }

    private boolean ownerVisible(PaymentReceiptDO r) {
        if (r.getSoId() != null) {
            SalesOrderDO so = orderMapper.selectById(r.getSoId());
            return so != null && dataScopeResolver.current().canSee(so.getOwnerId());
        }
        ProformaInvoiceDO pi = r.getPiId() == null ? null : piMapper.selectById(r.getPiId());
        return pi != null && dataScopeResolver.current().canSee(pi.getOwnerId());
    }

    private PaymentReceiptDO editable(Long id) {
        visible(id);
        if (!finance()) {
            throw new BizException("没有维护收款单的权限");
        }
        PaymentReceiptDO r = receiptMapper.selectOne(new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getId, id).last("FOR UPDATE"));
        MigratedGuard.requireEditable(r.getMigrated());
        if (r.getStatus() != SalesConstants.RECORD_VALID) {
            throw new BizException("收款单已作废");
        }
        return r;
    }

    // ---------------------------------------------------------------- 组装

    private record Indexed(int index, String source, ReceiptVO.SlipFile file) { }

    /** 先列到账自己的文件，再列对应水单的文件 */
    private List<Indexed> files(PaymentReceiptDO r) {
        List<Indexed> out = new ArrayList<>();
        for (ReceiptVO.SlipFile f : parse(r.getFileKeys())) {
            out.add(new Indexed(out.size(), "RECEIPT", f));
        }
        PaymentReceiptDO slip = r.getSlipId() == null ? null : receiptMapper.selectById(r.getSlipId());
        if (slip != null && slip.getDeletedAt() == null && Objects.equals(slip.getTenantId(), r.getTenantId())) {
            for (ReceiptVO.SlipFile f : parse(slip.getFileKeys())) {
                out.add(new Indexed(out.size(), "SLIP", f));
            }
        }
        return out;
    }

    private List<ReceiptVO.SlipFile> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ReceiptVO.SlipFile>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("到账附件解析失败", e);
        }
    }

    private List<ReceiptDocVO.Settlement> settlements(PaymentReceiptDO r) {
        List<FxSettlementAllocationDO> allocs = allocationMapper.selectList(new LambdaQueryWrapper<FxSettlementAllocationDO>()
                .eq(FxSettlementAllocationDO::getReceiptId, r.getId())
                .isNull(FxSettlementAllocationDO::getDeletedAt)
                .orderByAsc(FxSettlementAllocationDO::getId));
        if (allocs.isEmpty()) {
            return List.of();
        }
        Map<Long, FxSettlementDO> heads = settlementMapper.selectBatchIds(allocs.stream().map(FxSettlementAllocationDO::getSettlementId)
                .distinct().toList()).stream().collect(Collectors.toMap(FxSettlementDO::getId, Function.identity()));
        List<ReceiptDocVO.Settlement> out = new ArrayList<>(allocs.size());
        for (FxSettlementAllocationDO a : allocs) {
            FxSettlementDO h = heads.get(a.getSettlementId());
            if (h == null || h.getDeletedAt() != null || h.getStatus() != FinanceConstants.FX_VALID) {
                continue;
            }
            ReceiptDocVO.Settlement s = new ReceiptDocVO.Settlement();
            s.setSettlementId(h.getId());
            s.setSettleNo(h.getSettleNo());
            s.setSettleDate(h.getSettleDate());
            s.setAmount(a.getAmount());
            s.setExchangeRate(h.getExchangeRate());
            s.setAmountCny(a.getAmountCny());
            out.add(s);
        }
        return out;
    }

    private List<DocRefVO> related(PaymentReceiptDO r, List<ReceiptDocVO.Settlement> settlements) {
        List<DocRefVO> out = new ArrayList<>();
        ProformaInvoiceDO pi = r.getPiId() == null ? null : piMapper.selectById(r.getPiId());
        if (pi != null) {
            out.add(new DocRefVO(DocumentType.PI.name(), pi.getId(), pi.getPiNo(), null, null));
            SalesOrderDO so = orderMapper.selectOne(new LambdaQueryWrapper<SalesOrderDO>()
                    .eq(SalesOrderDO::getPiId, pi.getId()).isNull(SalesOrderDO::getDeletedAt)
                    .orderByDesc(SalesOrderDO::getId).last("LIMIT 1"));
            if (so != null) {
                out.add(new DocRefVO(DocumentType.SO.name(), so.getId(), so.getSoNo(), null, null));
            }
        }
        SalesOrderDO own = r.getSoId() == null ? null : orderMapper.selectById(r.getSoId());
        if (own != null) {
            out.add(new DocRefVO(DocumentType.SO.name(), own.getId(), own.getSoNo(), null, null));
        }
        settlements.stream().collect(Collectors.toMap(ReceiptDocVO.Settlement::getSettlementId, s -> s, (a, b) -> a,
                        java.util.LinkedHashMap::new)).values()
                .forEach(s -> out.add(new DocRefVO(DocumentType.JH.name(), s.getSettlementId(), s.getSettleNo(),
                        FinanceConstants.FX_VALID, "有效")));
        return out;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
