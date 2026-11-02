package com.zhul.erp.modules.aftersales.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.DataScope;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.modules.aftersales.constants.AfterSalesConstants;
import com.zhul.erp.modules.aftersales.dto.AfterSalesCloseRequest;
import com.zhul.erp.modules.aftersales.dto.AfterSalesNoteRequest;
import com.zhul.erp.modules.aftersales.dto.AfterSalesPlanRequest;
import com.zhul.erp.modules.aftersales.dto.AfterSalesQuery;
import com.zhul.erp.modules.aftersales.dto.AfterSalesVO;
import com.zhul.erp.modules.aftersales.dto.SaveAfterSalesRequest;
import com.zhul.erp.modules.aftersales.entity.AfterSalesDO;
import com.zhul.erp.modules.aftersales.entity.AfterSalesItemDO;
import com.zhul.erp.modules.aftersales.entity.AfterSalesLogDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnDispositionDO;
import com.zhul.erp.modules.aftersales.entity.SalesReturnItemDO;
import com.zhul.erp.modules.aftersales.repository.AfterSalesItemMapper;
import com.zhul.erp.modules.aftersales.repository.AfterSalesLogMapper;
import com.zhul.erp.modules.aftersales.repository.AfterSalesMapper;
import com.zhul.erp.modules.aftersales.repository.SalesReturnDispositionMapper;
import com.zhul.erp.modules.aftersales.repository.SalesReturnItemMapper;
import com.zhul.erp.modules.aftersales.repository.SalesReturnMapper;
import com.zhul.erp.modules.aftersales.support.AfterSalesLogs;
import com.zhul.erp.modules.aftersales.support.AfterSalesSla;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.entity.CustomerRefundDO;
import com.zhul.erp.modules.finance.entity.ExpenseDO;
import com.zhul.erp.modules.finance.entity.RefundRequestDO;
import com.zhul.erp.modules.finance.repository.CustomerRefundMapper;
import com.zhul.erp.modules.finance.repository.ExpenseMapper;
import com.zhul.erp.modules.finance.repository.RefundRequestMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.logistics.support.LogisticsQty;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import com.zhul.erp.modules.system.service.DictItemService;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 售后单（spec sales/after-sales）：登记、跟进、处理信息、结案与撤销，详情汇总退货、退款申请、退款与费用。
 * 可见范围：负责人或订单业务员在当前用户的数据范围内；能看到就能跟进（每个操作一个本地事务，先锁售后单）。
 */
@Service
@RequiredArgsConstructor
public class AfterSalesService {

    private final AfterSalesMapper mapper;
    private final AfterSalesItemMapper itemMapper;
    private final AfterSalesLogMapper logMapper;
    private final SalesReturnMapper returnMapper;
    private final SalesReturnItemMapper returnItemMapper;
    private final SalesReturnDispositionMapper dispositionMapper;
    private final RefundRequestMapper requestMapper;
    private final CustomerRefundMapper refundMapper;
    private final ExpenseMapper expenseMapper;
    private final CustomerMapper customerMapper;
    private final SalesOrderMapper orderMapper;
    private final SalesOrderItemMapper orderItemMapper;
    private final UserBasicMapper userMapper;
    private final DocumentNumberService documentNumberService;
    private final DataScopeResolver dataScopeResolver;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final DictItemService dictItemService;
    private final AttachmentService attachments;
    private final LogService logService;
    private final LogisticsQty logisticsQty;
    private final AfterSalesLogs logs;
    private final com.zhul.erp.modules.aftersales.support.ReplacementOrders replacementOrders;

    // ---------------------------------------------------------------- 登记

    @Transactional(rollbackFor = Exception.class)
    public AfterSalesVO create(SaveAfterSalesRequest req) {
        int tenant = PiStore.tenantId();
        CustomerDO customer = customerMapper.selectById(req.getCustomerId());
        if (customer == null || customer.getDeletedAt() != null || !Objects.equals(customer.getTenantId(), tenant)) {
            throw new BizException("客户不存在");
        }
        SalesOrderDO order = null;
        Map<Long, SalesOrderItemDO> orderItems = Map.of();
        if (req.getSoId() != null) {
            order = orderMapper.selectById(req.getSoId());
            if (order == null || order.getDeletedAt() != null || !Objects.equals(order.getTenantId(), tenant)
                    || !Objects.equals(order.getCustomerId(), customer.getId())) {
                throw new BizException("订单不属于这个客户");
            }
            if (order.getStatus() != SalesConstants.SO_ACTIVE) {
                throw new BizException("订单已取消，不能登记售后");
            }
            orderItems = orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                            .eq(SalesOrderItemDO::getSoId, order.getId())
                            .isNull(SalesOrderItemDO::getDeletedAt))
                    .stream().collect(Collectors.toMap(SalesOrderItemDO::getId, i -> i));
        }
        List<AfterSalesItemDO> rows = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (SaveAfterSalesRequest.Line l : req.getItems() == null ? List.<SaveAfterSalesRequest.Line>of() : req.getItems()) {
            if (l == null || l.getQuantity() == null || l.getQuantity() <= 0) {
                continue;
            }
            AfterSalesItemDO x = new AfterSalesItemDO();
            x.setTenantId(tenant);
            x.setQuantity(l.getQuantity());
            if (l.getSoItemId() != null) {
                SalesOrderItemDO oi = orderItems.get(l.getSoItemId());
                if (oi == null) {
                    throw new BizException("型号不属于这张订单");
                }
                if (!seen.add(oi.getId())) {
                    throw new BizException("同一个型号不能重复填写");
                }
                int max = oi.getQuantity();
                if (l.getQuantity() > max) {
                    throw new BizException(oi.getModel() + " 最多 " + max + " 个");
                }
                x.setSoItemId(oi.getId());
                x.setModel(oi.getModel());
                x.setBrand(Objects.requireNonNullElse(oi.getBrand(), ""));
            } else {
                if (!StringUtils.hasText(l.getModel())) {
                    throw new BizException("请填写型号");
                }
                x.setModel(l.getModel().trim());
                x.setBrand(l.getBrand() == null ? "" : l.getBrand().trim());
            }
            rows.add(x);
        }
        Long me = currentUserId();
        AfterSalesDO a = new AfterSalesDO();
        a.setTenantId(tenant);
        a.setAsNo(documentNumberService.next(DocumentType.AS));
        a.setCustomerId(customer.getId());
        a.setSoId(order == null ? null : order.getId());
        a.setLevelCode(requireDict(AfterSalesConstants.DICT_LEVEL, StringUtils.hasText(req.getLevelCode()) ? req.getLevelCode()
                : AfterSalesConstants.LEVEL_PENDING, "问题等级"));
        a.setSymptom(req.getSymptom().trim());
        a.setCustomerRequest(trim(req.getCustomerRequest()));
        a.setReceivedAt(req.getReceivedAt() == null ? LocalDateTime.now() : req.getReceivedAt());
        a.setReasonCode(AfterSalesConstants.REASON_INVESTIGATING);
        a.setSolutions("");
        Long owner = req.getOwnerId() != null ? requireUser(req.getOwnerId()) : order != null ? order.getOwnerId() : me;
        a.setOwnerId(owner);
        a.setNextAction(trim(req.getNextAction()));
        a.setNextUpdateAt(req.getNextUpdateAt());
        a.setStatus(AfterSalesConstants.AS_OPEN);
        a.setResult("");
        a.setCancelReason("");
        AfterSalesSla.refresh(a);
        mapper.insert(a);
        for (AfterSalesItemDO x : rows) {
            x.setAfterSalesId(a.getId());
            itemMapper.insert(x);
        }
        attachments.attach(AttachmentService.AFTER_SALES, a.getId(), req.getAttachmentIds());
        logs.add(a.getId(), AfterSalesConstants.LOG_CREATE, "登记售后单" + (order == null ? "" : "（订单 " + order.getSoNo() + "）"), me);
        logService.recordOperateLog(AfterSalesConstants.MENU, "登记售后单", null, Map.of("asNo", a.getAsNo(),
                "customer", InquiryLookups.customerName(customer), "soNo", order == null ? "" : order.getSoNo(), "symptom", a.getSymptom()));
        return detail(a.getId());
    }

    // ---------------------------------------------------------------- 跟进与处理

    @Transactional(rollbackFor = Exception.class)
    public AfterSalesVO addNote(Long id, AfterSalesNoteRequest req) {
        AfterSalesDO a = lock(id);
        Long me = currentUserId();
        AfterSalesLogDO x = new AfterSalesLogDO();
        x.setTenantId(a.getTenantId());
        x.setAfterSalesId(a.getId());
        x.setLogType(AfterSalesConstants.LOG_NOTE);
        x.setContent(req.getContent().trim());
        boolean notified = Boolean.TRUE.equals(req.getCustomerNotified());
        x.setCustomerNotified(notified);
        x.setOperatorId(me);
        logMapper.insert(x);
        attachments.attach(AttachmentService.AFTER_SALES_LOG, x.getId(), req.getAttachmentIds());
        if (notified) {
            LocalDateTime now = LocalDateTime.now();
            if (a.getRespondedAt() == null) {
                a.setRespondedAt(now);
            }
            a.setLastContactAt(now);
            a.setSlaStartAt(now);
        }
        touch(a);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public AfterSalesVO updatePlan(Long id, AfterSalesPlanRequest req) {
        AfterSalesDO a = lockEditable(id);
        Long me = currentUserId();
        List<String> changes = new ArrayList<>();
        if (StringUtils.hasText(req.getLevelCode()) && !req.getLevelCode().equals(a.getLevelCode())) {
            String code = requireDict(AfterSalesConstants.DICT_LEVEL, req.getLevelCode(), "问题等级");
            String note = trim(req.getLevelNote());
            if (note.isEmpty()) {
                throw new BizException("请填写等级调整的原因");
            }
            Map<String, String> names = dictNames(AfterSalesConstants.DICT_LEVEL);
            logs.add(a.getId(), AfterSalesConstants.LOG_LEVEL, "等级 " + shortLevel(names, a.getLevelCode()) + " → "
                    + shortLevel(names, code) + "：" + note, me);
            a.setLevelCode(code);
        }
        if (StringUtils.hasText(req.getReasonCode()) && !req.getReasonCode().equals(a.getReasonCode())) {
            String code = requireDict(AfterSalesConstants.DICT_REASON, req.getReasonCode(), "原因与责任");
            Map<String, String> names = dictNames(AfterSalesConstants.DICT_REASON);
            changes.add("原因 " + names.getOrDefault(a.getReasonCode(), a.getReasonCode()) + " → " + names.get(code));
            a.setReasonCode(code);
        }
        if (req.getSolutions() != null) {
            Map<String, String> names = dictNames(AfterSalesConstants.DICT_SOLUTION);
            List<String> codes = req.getSolutions().stream().filter(StringUtils::hasText).distinct().toList();
            for (String c : codes) {
                if (!names.containsKey(c)) {
                    throw new BizException("处理方案不正确");
                }
            }
            String joined = String.join(",", codes);
            if (!joined.equals(a.getSolutions())) {
                changes.add("方案 " + (codes.isEmpty() ? "清空" : codes.stream().map(names::get).collect(Collectors.joining("、"))));
                a.setSolutions(joined);
            }
        }
        if (req.getOwnerId() != null && !req.getOwnerId().equals(a.getOwnerId())) {
            Long owner = requireUser(req.getOwnerId());
            Map<Long, String> users = lookups.userNames(List.of(a.getOwnerId(), owner));
            changes.add("负责人 " + users.getOrDefault(a.getOwnerId(), "—") + " → " + users.get(owner));
            a.setOwnerId(owner);
        }
        String next = trim(req.getNextAction());
        if (!next.equals(a.getNextAction()) || !Objects.equals(req.getNextUpdateAt(), a.getNextUpdateAt())) {
            changes.add("下一步「" + (next.isEmpty() ? "—" : next) + "」" + (req.getNextUpdateAt() == null ? ""
                    : "，下次更新 " + req.getNextUpdateAt().toString().replace('T', ' ').substring(0, 16)));
            a.setNextAction(next);
            a.setNextUpdateAt(req.getNextUpdateAt());
        }
        if (!changes.isEmpty()) {
            logs.add(a.getId(), AfterSalesConstants.LOG_PLAN, String.join("；", changes), me);
        }
        save(a);
        return detail(id);
    }

    /** 处理中 ↔ 待客户确认 */
    @Transactional(rollbackFor = Exception.class)
    public AfterSalesVO setStatus(Long id, int status) {
        if (status != AfterSalesConstants.AS_OPEN && status != AfterSalesConstants.AS_WAITING) {
            throw new BizException("状态不正确");
        }
        AfterSalesDO a = lockEditable(id);
        if (a.getStatus() != status) {
            logs.add(a.getId(), AfterSalesConstants.LOG_STATUS, AfterSalesConstants.AS_STATUS_NAMES.get(a.getStatus()) + " → "
                    + AfterSalesConstants.AS_STATUS_NAMES.get(status), currentUserId());
            if (status == AfterSalesConstants.AS_OPEN && a.getRespondedAt() != null) {
                a.setSlaStartAt(LocalDateTime.now());
            }
            a.setStatus(status);
            save(a);
        }
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public AfterSalesVO close(Long id, AfterSalesCloseRequest req) {
        AfterSalesDO a = lockEditable(id);
        String reason = requireDict(AfterSalesConstants.DICT_REASON, req.getReasonCode(), "原因与责任");
        if (AfterSalesConstants.REASON_INVESTIGATING.equals(reason)) {
            throw new BizException("结案时请确认最终原因与责任，不能是「调查中」");
        }
        for (SalesReturnDO r : returnsOf(id)) {
            if (r.getStatus() == AfterSalesConstants.RT_PENDING || r.getStatus() == AfterSalesConstants.RT_RECEIVED) {
                throw new BizException("退货单 " + r.getReturnNo() + " 还没处理完");
            }
        }
        for (RefundRequestDO q : requestsOf(id)) {
            if (q.getStatus() == FinanceConstants.RQ_PENDING_APPROVAL || q.getStatus() == FinanceConstants.RQ_PENDING_FINANCE) {
                throw new BizException("退款申请 " + q.getRequestNo() + " 还没处理完");
            }
        }
        List<String> unshipped = replacementOrders.unshipped(id);
        if (!unshipped.isEmpty()) {
            throw new BizException("补发订单 " + String.join("、", unshipped) + " 还没出运");
        }
        a.setReasonCode(reason);
        a.setResult(req.getResult().trim());
        a.setStatus(AfterSalesConstants.AS_CLOSED);
        a.setClosedAt(LocalDateTime.now());
        save(a);
        logs.add(a.getId(), AfterSalesConstants.LOG_STATUS, "结案：" + dictNames(AfterSalesConstants.DICT_REASON).get(reason) + "；" + a.getResult(),
                currentUserId());
        logService.recordOperateLog(AfterSalesConstants.MENU, "售后单结案", null, Map.of("asNo", a.getAsNo(), "result", a.getResult()));
        return detail(id);
    }

    /** 发起补发（spec sales/after-sales「发起补发」）：锁售后单 → 建 0 元补发订单与采购需求 → 写跟进记录，同一事务 */
    @Transactional(rollbackFor = Exception.class)
    public AfterSalesVO replace(Long id, com.zhul.erp.modules.aftersales.dto.ReplacementRequest req) {
        AfterSalesDO a = lockEditable(id);
        SalesOrderDO o = replacementOrders.create(a, req);
        String summary = orderItemMapper.selectList(new LambdaQueryWrapper<SalesOrderItemDO>()
                        .eq(SalesOrderItemDO::getSoId, o.getId())
                        .orderByAsc(SalesOrderItemDO::getLineNo))
                .stream().map(i -> i.getModel() + " × " + i.getQuantity()).collect(Collectors.joining("、"));
        logs.add(a.getId(), AfterSalesConstants.LOG_REPLACEMENT, "补发订单 " + o.getSoNo() + "：" + summary, currentUserId());
        touch(a);
        logService.recordOperateLog(AfterSalesConstants.MENU, "售后补发", null, Map.of("asNo", a.getAsNo(), "soNo", o.getSoNo()));
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public AfterSalesVO cancel(Long id, String reason) {
        String why = trim(reason);
        if (why.isEmpty()) {
            throw new BizException("请填写撤销原因");
        }
        AfterSalesDO a = lockEditable(id);
        if (returnsOf(id).stream().anyMatch(r -> r.getStatus() != AfterSalesConstants.RT_VOID)) {
            throw new BizException("已有退货单，不能撤销");
        }
        if (requestsOf(id).stream().anyMatch(q -> q.getStatus() != FinanceConstants.RQ_WITHDRAWN)) {
            throw new BizException("已有退款申请，不能撤销");
        }
        if (!expensesOf(List.of(id)).isEmpty() || !refundsOf(id).isEmpty()) {
            throw new BizException("已有关联的退款或费用，不能撤销");
        }
        a.setStatus(AfterSalesConstants.AS_CANCELLED);
        a.setCancelReason(why.length() > 200 ? why.substring(0, 200) : why);
        save(a);
        logs.add(a.getId(), AfterSalesConstants.LOG_STATUS, "撤销：" + a.getCancelReason(), currentUserId());
        logService.recordOperateLog(AfterSalesConstants.MENU, "撤销售后单", null, Map.of("asNo", a.getAsNo(), "reason", a.getCancelReason()));
        return detail(id);
    }

    // ---------------------------------------------------------------- 查看

    public PageResult<AfterSalesVO> page(AfterSalesQuery q) {
        LambdaQueryWrapper<AfterSalesDO> w = scoped();
        if (q.getStatus() != null) {
            w.eq(AfterSalesDO::getStatus, q.getStatus());
        }
        if (q.getCustomerId() != null) {
            w.eq(AfterSalesDO::getCustomerId, q.getCustomerId());
        }
        if (StringUtils.hasText(q.getLevelCode())) {
            w.eq(AfterSalesDO::getLevelCode, q.getLevelCode());
        }
        if (q.getOwnerId() != null) {
            w.eq(AfterSalesDO::getOwnerId, q.getOwnerId());
        }
        if (Boolean.TRUE.equals(q.getOverdue())) {
            dueBefore(w, LocalDateTime.now());
        } else if (Boolean.TRUE.equals(q.getDueSoon())) {
            dueBefore(w, LocalDateTime.now().plusMinutes(AfterSalesConstants.DUE_SOON_MINUTES));
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            Set<Long> soIds = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>().select(SalesOrderDO::getId)
                            .eq(SalesOrderDO::getTenantId, PiStore.tenantId()).like(SalesOrderDO::getSoNo, kw).last("LIMIT 200"))
                    .stream().map(SalesOrderDO::getId).collect(Collectors.toSet());
            Set<Long> byModel = itemMapper.selectList(new LambdaQueryWrapper<AfterSalesItemDO>().select(AfterSalesItemDO::getAfterSalesId)
                            .eq(AfterSalesItemDO::getTenantId, PiStore.tenantId()).like(AfterSalesItemDO::getModel, kw)
                            .isNull(AfterSalesItemDO::getDeletedAt).last("LIMIT 500"))
                    .stream().map(AfterSalesItemDO::getAfterSalesId).collect(Collectors.toSet());
            w.and(x -> {
                x.like(AfterSalesDO::getAsNo, kw);
                if (!soIds.isEmpty()) {
                    x.or().in(AfterSalesDO::getSoId, soIds);
                }
                if (!byModel.isEmpty()) {
                    x.or().in(AfterSalesDO::getId, byModel);
                }
            });
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = mapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        w.last("ORDER BY update_time DESC, id DESC LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toVos(mapper.selectList(w)));
    }

    /** 顶部卡片：处理中、待客户确认、需要更新客户（已过截止时间）与快到期、本月结案 */
    public Map<String, Long> counts() {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("open", mapper.selectCount(scoped().eq(AfterSalesDO::getStatus, AfterSalesConstants.AS_OPEN)));
        m.put("waiting", mapper.selectCount(scoped().eq(AfterSalesDO::getStatus, AfterSalesConstants.AS_WAITING)));
        m.put("overdue", mapper.selectCount(dueBefore(scoped(), now)));
        m.put("dueSoon", mapper.selectCount(dueBefore(scoped(), now.plusMinutes(AfterSalesConstants.DUE_SOON_MINUTES))
                .ge(AfterSalesDO::getDueAt, now)));
        m.put("closedThisMonth", mapper.selectCount(scoped().eq(AfterSalesDO::getStatus, AfterSalesConstants.AS_CLOSED)
                .ge(AfterSalesDO::getClosedAt, now.toLocalDate().withDayOfMonth(1).atStartOfDay())));
        return m;
    }

    /** 工作台「我的待办」：本人负责的已逾期或快到期售后单数 */
    public long myDueCount() {
        Long me = currentUserId();
        if (me == null) {
            return 0;
        }
        return mapper.selectCount(dueBefore(new LambdaQueryWrapper<AfterSalesDO>()
                .eq(AfterSalesDO::getTenantId, PiStore.tenantId())
                .isNull(AfterSalesDO::getDeletedAt)
                .eq(AfterSalesDO::getOwnerId, me), LocalDateTime.now().plusMinutes(AfterSalesConstants.DUE_SOON_MINUTES)));
    }

    /** 工作台「待跟进」：数据范围内已逾期的售后单，最久的在前 */
    public List<AfterSalesVO> overdueInScope() {
        return toVos(mapper.selectList(dueBefore(scoped(), LocalDateTime.now()).orderByAsc(AfterSalesDO::getDueAt)));
    }

    /** 处理中、待客户确认且截止时间早于 t */
    private static LambdaQueryWrapper<AfterSalesDO> dueBefore(LambdaQueryWrapper<AfterSalesDO> w, LocalDateTime t) {
        return w.in(AfterSalesDO::getStatus, AfterSalesConstants.AS_OPEN, AfterSalesConstants.AS_WAITING)
                .isNotNull(AfterSalesDO::getDueAt)
                .lt(AfterSalesDO::getDueAt, t);
    }

    public AfterSalesVO detail(Long id) {
        AfterSalesDO a = visible(id);
        AfterSalesVO vo = toVos(List.of(a)).get(0);
        List<AfterSalesItemDO> items = itemsOf(List.of(id)).getOrDefault(id, List.of());
        Map<Long, LogisticsQty.Qty> out = logisticsQty.bySoItem(items.stream().map(AfterSalesItemDO::getSoItemId).filter(Objects::nonNull).toList());
        vo.setItems(items.stream().map(i -> {
            AfterSalesVO.Item x = new AfterSalesVO.Item();
            x.setId(i.getId());
            x.setSoItemId(i.getSoItemId());
            x.setModel(i.getModel());
            x.setBrand(i.getBrand());
            x.setQuantity(i.getQuantity());
            x.setShippedQty(i.getSoItemId() == null ? null : out.getOrDefault(i.getSoItemId(), LogisticsQty.Qty.ZERO).shipped());
            return x;
        }).toList());
        vo.setAttachments(attachments.listOf(AttachmentService.AFTER_SALES, id));
        List<AfterSalesLogDO> logRows = logMapper.selectList(new LambdaQueryWrapper<AfterSalesLogDO>()
                .eq(AfterSalesLogDO::getAfterSalesId, id)
                .isNull(AfterSalesLogDO::getDeletedAt)
                .orderByDesc(AfterSalesLogDO::getId));
        Map<Long, String> users = lookups.userNames(logRows.stream().map(AfterSalesLogDO::getOperatorId).toList());
        Map<Long, List<AttachmentVO>> files = attachments.list(AttachmentService.AFTER_SALES_LOG,
                logRows.stream().filter(l -> AfterSalesConstants.LOG_NOTE.equals(l.getLogType())).map(AfterSalesLogDO::getId).toList());
        vo.setLogs(logRows.stream().map(l -> {
            AfterSalesVO.Log x = new AfterSalesVO.Log();
            x.setId(l.getId());
            x.setLogType(l.getLogType());
            x.setLogTypeName(AfterSalesConstants.LOG_TYPE_NAMES.getOrDefault(l.getLogType(), l.getLogType()));
            x.setContent(l.getContent());
            x.setCustomerNotified(Boolean.TRUE.equals(l.getCustomerNotified()));
            x.setOperatorName(users.get(l.getOperatorId()));
            x.setCreateTime(l.getCreateTime());
            x.setAttachments(files.getOrDefault(l.getId(), List.of()));
            return x;
        }).toList());
        List<SalesReturnDO> returns = returnsOf(id);
        Map<Long, List<SalesReturnItemDO>> returnItems = returns.isEmpty() ? Map.of() : returnItemMapper.selectList(
                        new LambdaQueryWrapper<SalesReturnItemDO>()
                                .in(SalesReturnItemDO::getReturnId, returns.stream().map(SalesReturnDO::getId).toList())
                                .isNull(SalesReturnItemDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(SalesReturnItemDO::getReturnId));
        vo.setReturns(returns.stream().map(r -> {
            AfterSalesVO.ReturnBrief x = new AfterSalesVO.ReturnBrief();
            x.setId(r.getId());
            x.setReturnNo(r.getReturnNo());
            x.setStatus(r.getStatus());
            x.setStatusName(AfterSalesConstants.RT_STATUS_NAMES.get(r.getStatus()));
            x.setSummary(returnItems.getOrDefault(r.getId(), List.of()).stream().map(i -> i.getModel() + " × " + i.getQuantity())
                    .collect(Collectors.joining("、")));
            return x;
        }).toList());
        List<SalesReturnDispositionDO> disps = returns.isEmpty() ? List.of() : dispositionMapper.selectList(
                new LambdaQueryWrapper<SalesReturnDispositionDO>()
                        .in(SalesReturnDispositionDO::getReturnId, returns.stream().map(SalesReturnDO::getId).toList())
                        .isNull(SalesReturnDispositionDO::getDeletedAt));
        vo.setReturnCreditCny(disps.stream().map(SalesReturnDispositionDO::getCostCny).reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setReturnFreightCny(disps.stream().map(SalesReturnDispositionDO::getFreightCny).reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setRequests(requestsOf(id).stream().map(q -> {
            AfterSalesVO.RequestBrief x = new AfterSalesVO.RequestBrief();
            x.setId(q.getId());
            x.setRequestNo(q.getRequestNo());
            x.setStatus(q.getStatus());
            x.setStatusName(FinanceConstants.RQ_STATUS_NAMES.get(q.getStatus()));
            x.setCurrencyCode(q.getCurrencyCode());
            x.setAmount(q.getAmount());
            return x;
        }).toList());
        List<CustomerRefundDO> refunds = refundsOf(id);
        vo.setRefunds(refunds.stream().map(f -> {
            AfterSalesVO.RefundBrief x = new AfterSalesVO.RefundBrief();
            x.setId(f.getId());
            x.setRefundNo(f.getRefundNo());
            x.setRefundDate(f.getRefundDate());
            x.setCurrencyCode(f.getCurrencyCode());
            x.setAmount(f.getAmount());
            x.setAmountCny(f.getAmountCny());
            x.setMethodName(FinanceConstants.REFUND_METHOD_NAMES.get(f.getMethod()));
            return x;
        }).toList());
        vo.setRefundCny(refunds.stream().map(CustomerRefundDO::getAmountCny).reduce(BigDecimal.ZERO, BigDecimal::add));
        List<ExpenseDO> expenses = expensesOf(List.of(id));
        vo.setExpenses(expenses.stream().map(e -> {
            AfterSalesVO.ExpenseBrief x = new AfterSalesVO.ExpenseBrief();
            x.setId(e.getId());
            x.setExpenseNo(e.getExpenseNo());
            x.setName(e.getName());
            x.setAmountCny(e.getAmountCny());
            return x;
        }).toList());
        vo.setExpenseCny(expenses.stream().map(ExpenseDO::getAmountCny).reduce(BigDecimal.ZERO, BigDecimal::add));
        vo.setReplacements(replacementOrders.briefs(id));
        return vo;
    }

    /** 可选的负责人：本租户启用的用户（售后可以交给业务、采购或技术） */
    public List<Map<String, Object>> owners() {
        return userMapper.selectList(new LambdaQueryWrapper<UserBasicDO>()
                        .eq(UserBasicDO::getTenantId, PiStore.tenantId())
                        .eq(UserBasicDO::getStatus, 1)
                        .orderByAsc(UserBasicDO::getId))
                .stream().map(u -> Map.<String, Object>of("id", u.getId().longValue(), "name", u.getName())).toList();
    }

    /** 选售后单（费用单、退款单关联用）：订单的处理中、待客户确认、已结案的售后单 */
    public List<AfterSalesVO> optionsOfOrder(Long soId) {
        return toVos(mapper.selectList(new LambdaQueryWrapper<AfterSalesDO>()
                .eq(AfterSalesDO::getTenantId, PiStore.tenantId())
                .eq(AfterSalesDO::getSoId, soId)
                .ne(AfterSalesDO::getStatus, AfterSalesConstants.AS_CANCELLED)
                .isNull(AfterSalesDO::getDeletedAt)
                .orderByDesc(AfterSalesDO::getId)));
    }

    /** 关联单据校验：售后单属于这张订单且没有撤销（退款单、费用单、退款申请用） */
    public AfterSalesDO requireOfOrder(Long afterSalesId, Long soId) {
        AfterSalesDO a = afterSalesId == null ? null : mapper.selectById(afterSalesId);
        if (a == null || a.getDeletedAt() != null || !Objects.equals(a.getTenantId(), PiStore.tenantId())
                || !Objects.equals(a.getSoId(), soId)) {
            throw new BizException("售后单不属于这张订单");
        }
        if (a.getStatus() == AfterSalesConstants.AS_CANCELLED) {
            throw new BizException("售后单已撤销");
        }
        return a;
    }

    // ---------------------------------------------------------------- 内部

    private List<AfterSalesVO> toVos(List<AfterSalesDO> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, CustomerDO> customers = lookups.customers(rows.stream().map(AfterSalesDO::getCustomerId).distinct().toList());
        Set<Long> soIds = rows.stream().map(AfterSalesDO::getSoId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SalesOrderDO> orders = soIds.isEmpty() ? Map.of()
                : orderMapper.selectBatchIds(soIds).stream().collect(Collectors.toMap(SalesOrderDO::getId, o -> o));
        Map<Long, String> users = lookups.userNames(rows.stream().map(AfterSalesDO::getOwnerId).distinct().toList());
        Map<String, String> levels = dictNames(AfterSalesConstants.DICT_LEVEL);
        Map<String, String> reasons = dictNames(AfterSalesConstants.DICT_REASON);
        Map<String, String> solutions = dictNames(AfterSalesConstants.DICT_SOLUTION);
        Map<Long, List<AfterSalesItemDO>> items = itemsOf(rows.stream().map(AfterSalesDO::getId).toList());
        LocalDateTime now = LocalDateTime.now();
        List<AfterSalesVO> out = new ArrayList<>(rows.size());
        for (AfterSalesDO a : rows) {
            AfterSalesVO v = new AfterSalesVO();
            SalesOrderDO o = a.getSoId() == null ? null : orders.get(a.getSoId());
            v.setId(a.getId());
            v.setAsNo(a.getAsNo());
            v.setCustomerId(a.getCustomerId());
            v.setCustomerName(InquiryLookups.customerName(customers.get(a.getCustomerId())));
            v.setSoId(a.getSoId());
            v.setSoNo(o == null ? null : o.getSoNo());
            v.setCurrencyCode(o == null ? null : o.getCurrencyCode());
            v.setLevelCode(a.getLevelCode());
            v.setLevelName(levels.getOrDefault(a.getLevelCode(), a.getLevelCode()));
            v.setSymptom(a.getSymptom());
            v.setCustomerRequest(a.getCustomerRequest());
            v.setReceivedAt(a.getReceivedAt());
            v.setReasonCode(a.getReasonCode());
            v.setReasonName(reasons.getOrDefault(a.getReasonCode(), a.getReasonCode()));
            List<String> codes = StringUtils.hasText(a.getSolutions()) ? Arrays.asList(a.getSolutions().split(",")) : List.of();
            v.setSolutions(codes);
            v.setSolutionNames(codes.stream().map(c -> solutions.getOrDefault(c, c)).toList());
            v.setOwnerId(a.getOwnerId());
            v.setOwnerName(users.get(a.getOwnerId()));
            v.setNextAction(a.getNextAction());
            v.setNextUpdateAt(a.getNextUpdateAt());
            boolean active = a.getStatus() == AfterSalesConstants.AS_OPEN || a.getStatus() == AfterSalesConstants.AS_WAITING;
            v.setRespondedAt(a.getRespondedAt());
            v.setLastContactAt(a.getLastContactAt());
            v.setLevelDueAt(AfterSalesSla.levelDue(a));
            v.setDueAt(active ? a.getDueAt() : null);
            v.setDueType(v.getDueAt() != null ? a.getDueType() : AfterSalesConstants.DUE_NONE);
            v.setDueTypeName(AfterSalesConstants.DUE_TYPE_NAMES.get(v.getDueType()));
            v.setDueState(AfterSalesSla.state(v.getDueAt(), now));
            v.setOverdue("OVERDUE".equals(v.getDueState()));
            v.setEditable(active);
            v.setStatus(a.getStatus());
            v.setStatusName(AfterSalesConstants.AS_STATUS_NAMES.get(a.getStatus()));
            v.setResult(a.getResult());
            v.setClosedAt(a.getClosedAt());
            v.setCancelReason(a.getCancelReason());
            v.setItemSummary(items.getOrDefault(a.getId(), List.of()).stream().map(i -> i.getModel() + " × " + i.getQuantity())
                    .collect(Collectors.joining("、")));
            v.setCreateTime(a.getCreateTime());
            v.setCreateBy(a.getCreateBy());
            v.setUpdateTime(a.getUpdateTime());
            v.setUpdateBy(a.getUpdateBy());
            out.add(v);
        }
        return out;
    }

    /** 调用方保证 ids 非空 */
    private Map<Long, List<AfterSalesItemDO>> itemsOf(Collection<Long> ids) {
        return itemMapper.selectList(new LambdaQueryWrapper<AfterSalesItemDO>()
                        .in(AfterSalesItemDO::getAfterSalesId, ids)
                        .isNull(AfterSalesItemDO::getDeletedAt)
                        .orderByAsc(AfterSalesItemDO::getId))
                .stream().collect(Collectors.groupingBy(AfterSalesItemDO::getAfterSalesId, LinkedHashMap::new, Collectors.toList()));
    }

    private List<SalesReturnDO> returnsOf(Long id) {
        return returnMapper.selectList(new LambdaQueryWrapper<SalesReturnDO>()
                .eq(SalesReturnDO::getAfterSalesId, id)
                .isNull(SalesReturnDO::getDeletedAt)
                .orderByDesc(SalesReturnDO::getId));
    }

    private List<RefundRequestDO> requestsOf(Long id) {
        return requestMapper.selectList(new LambdaQueryWrapper<RefundRequestDO>()
                .eq(RefundRequestDO::getAfterSalesId, id)
                .isNull(RefundRequestDO::getDeletedAt)
                .orderByDesc(RefundRequestDO::getId));
    }

    private List<CustomerRefundDO> refundsOf(Long id) {
        return refundMapper.selectList(new LambdaQueryWrapper<CustomerRefundDO>()
                .eq(CustomerRefundDO::getAfterSalesId, id)
                .eq(CustomerRefundDO::getStatus, FinanceConstants.RECORD_VALID)
                .isNull(CustomerRefundDO::getDeletedAt)
                .orderByDesc(CustomerRefundDO::getId));
    }

    private List<ExpenseDO> expensesOf(Collection<Long> ids) {
        return expenseMapper.selectList(new LambdaQueryWrapper<ExpenseDO>()
                .in(ExpenseDO::getAfterSalesId, ids)
                .eq(ExpenseDO::getStatus, FinanceConstants.RECORD_VALID)
                .isNull(ExpenseDO::getDeletedAt)
                .orderByDesc(ExpenseDO::getId));
    }

    /** 数据范围：负责人或订单业务员在可见范围内 */
    private LambdaQueryWrapper<AfterSalesDO> scoped() {
        LambdaQueryWrapper<AfterSalesDO> w = new LambdaQueryWrapper<AfterSalesDO>()
                .eq(AfterSalesDO::getTenantId, PiStore.tenantId())
                .isNull(AfterSalesDO::getDeletedAt);
        DataScope scope = dataScopeResolver.current();
        if (scope.isAll()) {
            return w;
        }
        if (scope.ownerIds().isEmpty()) {
            return w.apply("1 = 0");
        }
        Set<Long> owners = new LinkedHashSet<>(scope.ownerIds());
        String ids = owners.stream().map(String::valueOf).collect(Collectors.joining(","));
        return w.and(x -> x.in(AfterSalesDO::getOwnerId, owners)
                .or().inSql(AfterSalesDO::getSoId, "SELECT id FROM sales_order WHERE owner_id IN (" + ids + ")"));
    }

    private AfterSalesDO visible(Long id) {
        AfterSalesDO a = id == null ? null : mapper.selectById(id);
        if (a == null || a.getDeletedAt() != null || !Objects.equals(a.getTenantId(), PiStore.tenantId())) {
            throw new BizException("售后单不存在");
        }
        DataScope scope = dataScopeResolver.current();
        if (!scope.canSee(a.getOwnerId())) {
            SalesOrderDO o = a.getSoId() == null ? null : orderMapper.selectById(a.getSoId());
            if (o == null || !scope.canSee(o.getOwnerId())) {
                throw new BizException("售后单不存在");
            }
        }
        return a;
    }

    private AfterSalesDO lock(Long id) {
        visible(id);
        mapper.lockById(id);
        return mapper.selectById(id);
    }

    private AfterSalesDO lockEditable(Long id) {
        AfterSalesDO a = lock(id);
        if (a.getStatus() == AfterSalesConstants.AS_CLOSED) {
            throw new BizException("售后单已结案，只能添加跟进记录");
        }
        if (a.getStatus() == AfterSalesConstants.AS_CANCELLED) {
            throw new BizException("售后单已撤销");
        }
        return a;
    }

    /** 关联单据的动作让售后单出现在列表前面 */
    private void touch(AfterSalesDO a) {
        save(a);
    }

    /** 保存前按等级、状态与手填时间重算截止时间（spec sales/after-sales「处理时限」） */
    private void save(AfterSalesDO a) {
        AfterSalesSla.refresh(a);
        mapper.updateById(a);
    }

    private Map<String, String> dictNames(String type) {
        Map<String, String> m = new LinkedHashMap<>();
        dictItemService.listByDictType(type).stream().filter(d -> Objects.equals(d.getStatus(), 1))
                .forEach(d -> m.put(d.getItemCode(), d.getItemName()));
        return m;
    }

    private String requireDict(String type, String code, String label) {
        String c = code == null ? "" : code.trim();
        if (!dictNames(type).containsKey(c)) {
            throw new BizException(label + "不正确");
        }
        return c;
    }

    private static String shortLevel(Map<String, String> names, String code) {
        return AfterSalesConstants.LEVEL_PENDING.equals(code) ? names.getOrDefault(code, code) : code;
    }

    private Long requireUser(Long id) {
        long n = userMapper.selectCount(new LambdaQueryWrapper<UserBasicDO>()
                .eq(UserBasicDO::getId, id.intValue())
                .eq(UserBasicDO::getTenantId, PiStore.tenantId()));
        if (n == 0) {
            throw new BizException("负责人不存在");
        }
        return id;
    }

    private Long currentUserId() {
        Long id = currentUser.resolve();
        return id == null ? 0L : id;
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
