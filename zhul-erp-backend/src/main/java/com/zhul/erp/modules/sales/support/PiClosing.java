package com.zhul.erp.modules.sales.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.quotation.constants.QuotationConstants;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.repository.QuotationMapper;
import com.zhul.erp.modules.quotation.support.InquiryStatusSync;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.ClosePiRequest;
import com.zhul.erp.modules.sales.entity.PiItemDO;
import com.zhul.erp.modules.sales.entity.PiVersionDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.repository.PiItemMapper;
import com.zhul.erp.modules.sales.repository.PiVersionMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.system.dto.DictItemVO;
import com.zhul.erp.modules.system.service.DictItemService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 关闭与重新打开 PI（客户最终没有付款）。调用方在事务内；加锁顺序：PI → 报价单（ID 升序）→ 客户询盘（ID 升序），
 * 与销售订单推进报价单（QuotationDeals）一致。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PiClosing {

    /** 仍然有效的 PI：草稿、已发送、已转订单 */
    private static final Set<Integer> LIVE = Set.of(SalesConstants.PI_DRAFT, SalesConstants.PI_SENT, SalesConstants.PI_CONVERTED);

    private final ProformaInvoiceMapper piMapper;
    private final PiVersionMapper versionMapper;
    private final PiItemMapper itemMapper;
    private final QuotationMapper quotationMapper;
    private final InquiryStatusSync statusSync;
    private final DictItemService dictItemService;
    private final CurrentUserResolver currentUser;
    private final LogService logService;
    private final PiStore store;

    /** 关闭；返回给业务员的提示（如某张报价单还有其他有效 PI、未改为未成交） */
    public List<String> close(ProformaInvoiceDO pi, ClosePiRequest req) {
        if (pi.getStatus() != SalesConstants.PI_SENT) {
            throw new BizException(pi.getStatus() == SalesConstants.PI_CLOSED ? "PI 已关闭"
                    : "只有已发送的 PI 可以关闭，当前为「" + SalesConstants.PI_STATUS_NAMES.get(pi.getStatus()) + "」");
        }
        if (pi.getReceiptStatus() != SalesConstants.RECEIPT_NONE) {
            throw new BizException("PI 已有水单或到账记录，不能关闭");
        }
        if (!StringUtils.hasText(req.getReason())) {
            throw new BizException("请选择关闭原因");
        }
        DictItemVO reason = dictItemService.listByDictType(QuotationConstants.DICT_LOST_REASON).stream()
                .filter(i -> Objects.equals(i.getStatus(), 1) && req.getReason().equals(i.getItemCode()))
                .findFirst().orElseThrow(() -> new BizException("关闭原因不存在或已停用"));
        String note = req.getNote() == null ? "" : req.getNote().trim();
        if (QuotationConstants.LOST_REASON_OTHER.equals(reason.getItemCode()) && note.isEmpty()) {
            throw new BizException("选择「其他」时请填写说明");
        }
        if (pi.getEditingVersionNo() != null) {
            PiVersionDO v = store.version(pi.getId(), pi.getEditingVersionNo());
            v.setStatus(SalesConstants.VERSION_ABANDONED);
            versionMapper.updateById(v);
            pi.setEditingVersionNo(null);
        }
        pi.setStatus(SalesConstants.PI_CLOSED);
        pi.setCloseReason(reason.getItemCode());
        pi.setCloseReasonName(reason.getItemName());
        pi.setCloseNote(note);
        pi.setClosedAt(LocalDateTime.now());
        pi.setClosedBy(currentUser.resolve());
        piMapper.updateById(pi);

        List<String> notices = new ArrayList<>();
        List<String> lostNos = new ArrayList<>();
        if (!Boolean.FALSE.equals(req.getMarkQuotationLost())) {
            List<QuotationDO> quotations = lockSources(pi);
            Set<Long> inquiryIds = new TreeSet<>();
            quotations.forEach(q -> inquiryIds.addAll(statusSync.inquiryIdsOf(q.getId())));
            statusSync.lock(inquiryIds);
            for (QuotationDO q : quotations) {
                if (q.getStatus() != QuotationConstants.STATUS_SENT) {
                    continue;
                }
                String other = otherLivePi(q.getId(), pi.getId());
                if (other != null) {
                    notices.add(q.getQuotationNo() + " 还有有效的 PI " + other + "，未改为未成交");
                    continue;
                }
                quotationMapper.update(null, new LambdaUpdateWrapper<QuotationDO>()
                        .set(QuotationDO::getStatus, QuotationConstants.STATUS_LOST)
                        .set(QuotationDO::getLostReason, reason.getItemCode())
                        .set(QuotationDO::getLostReasonName, reason.getItemName())
                        .set(QuotationDO::getLostNote, note)
                        .set(QuotationDO::getLostByPiId, pi.getId())
                        .set(QuotationDO::getEditingVersionNo, null)
                        .set(QuotationDO::getClosedAt, LocalDateTime.now())
                        .set(QuotationDO::getUpdateTime, LocalDateTime.now())
                        .eq(QuotationDO::getId, q.getId()));
                lostNos.add(q.getQuotationNo());
            }
            statusSync.sync(inquiryIds);
        }
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("piNo", pi.getPiNo());
        after.put("status", "已关闭");
        after.put("reason", reason.getItemName());
        after.put("note", note);
        after.put("quotationsLost", lostNos);
        logService.recordOperateLog(SalesConstants.MENU_PI, "关闭 PI", Map.of("piNo", pi.getPiNo(), "status", "已发送"), after);
        log.info("关闭 PI，piNo={}, reason={}, quotationsLost={}", pi.getPiNo(), reason.getItemCode(), lostNos);
        return notices;
    }

    /** 重新打开：PI 回到已发送，被这次关闭标为未成交的报价单回到已发送 */
    public void reopen(ProformaInvoiceDO pi, LocalDate validUntil) {
        if (pi.getStatus() != SalesConstants.PI_CLOSED) {
            throw new BizException("只有已关闭的 PI 可以重新打开");
        }
        if (validUntil != null) {
            PiVersionDO v = store.version(pi.getId(), pi.getCurrentVersionNo());
            if (validUntil.isBefore(pi.getCreateTime().toLocalDate())) {
                throw new BizException("有效期不能早于 PI 日期");
            }
            v.setValidUntil(validUntil);
            versionMapper.updateById(v);
            pi.setValidUntil(validUntil);
        }
        pi.setStatus(SalesConstants.PI_SENT);
        pi.setCloseReason("");
        pi.setCloseReasonName("");
        pi.setCloseNote("");
        pi.setClosedAt(null);
        pi.setClosedBy(null);
        piMapper.updateById(pi);

        List<QuotationDO> quotations = quotationMapper.selectList(new LambdaQueryWrapper<QuotationDO>()
                .eq(QuotationDO::getLostByPiId, pi.getId())
                .eq(QuotationDO::getStatus, QuotationConstants.STATUS_LOST)
                .isNull(QuotationDO::getDeletedAt)
                .orderByAsc(QuotationDO::getId));
        quotations.forEach(q -> quotationMapper.lockById(q.getId()));
        Set<Long> inquiryIds = new TreeSet<>();
        quotations.forEach(q -> inquiryIds.addAll(statusSync.inquiryIdsOf(q.getId())));
        statusSync.lock(inquiryIds);
        for (QuotationDO q : quotations) {
            quotationMapper.update(null, new LambdaUpdateWrapper<QuotationDO>()
                    .set(QuotationDO::getStatus, QuotationConstants.STATUS_SENT)
                    .set(QuotationDO::getLostReason, "")
                    .set(QuotationDO::getLostReasonName, "")
                    .set(QuotationDO::getLostNote, "")
                    .set(QuotationDO::getLostByPiId, null)
                    .set(QuotationDO::getClosedAt, null)
                    .set(QuotationDO::getUpdateTime, LocalDateTime.now())
                    .eq(QuotationDO::getId, q.getId()));
        }
        statusSync.sync(inquiryIds);
        logService.recordOperateLog(SalesConstants.MENU_PI, "重新打开 PI", Map.of("piNo", pi.getPiNo(), "status", "已关闭"),
                Map.of("piNo", pi.getPiNo(), "status", "已发送",
                        "quotationsRestored", quotations.stream().map(QuotationDO::getQuotationNo).toList()));
    }

    /** 当前有效版本型号行的来源报价单，按 ID 升序加锁后返回 */
    private List<QuotationDO> lockSources(ProformaInvoiceDO pi) {
        PiVersionDO v = store.version(pi.getId(), pi.getCurrentVersionNo());
        Set<Long> ids = itemMapper.selectList(new LambdaQueryWrapper<PiItemDO>()
                        .select(PiItemDO::getQuotationId)
                        .eq(PiItemDO::getVersionId, v.getId())
                        .isNull(PiItemDO::getDeletedAt))
                .stream().map(PiItemDO::getQuotationId).filter(Objects::nonNull).collect(Collectors.toCollection(TreeSet::new));
        List<QuotationDO> list = new ArrayList<>(ids.size());
        for (Long id : ids) {
            quotationMapper.lockById(id);
            QuotationDO q = quotationMapper.selectById(id);
            if (q != null && q.getDeletedAt() == null) {
                list.add(q);
            }
        }
        return list;
    }

    /** 引用该报价单的其他有效 PI 编号，没有时为空 */
    private String otherLivePi(Long quotationId, Long piId) {
        Set<Long> piIds = itemMapper.selectList(new LambdaQueryWrapper<PiItemDO>()
                        .select(PiItemDO::getPiId)
                        .eq(PiItemDO::getQuotationId, quotationId)
                        .ne(PiItemDO::getPiId, piId)
                        .isNull(PiItemDO::getDeletedAt))
                .stream().map(PiItemDO::getPiId).collect(Collectors.toSet());
        if (piIds.isEmpty()) {
            return null;
        }
        return piMapper.selectList(new LambdaQueryWrapper<ProformaInvoiceDO>()
                        .select(ProformaInvoiceDO::getPiNo)
                        .in(ProformaInvoiceDO::getId, piIds)
                        .in(ProformaInvoiceDO::getStatus, LIVE)
                        .isNull(ProformaInvoiceDO::getDeletedAt)
                        .orderByAsc(ProformaInvoiceDO::getId))
                .stream().map(ProformaInvoiceDO::getPiNo).findFirst().orElse(null);
    }
}
