package com.zhul.erp.modules.logistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.document.dto.TemplateFile;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.dto.SaveStatementRequest;
import com.zhul.erp.modules.logistics.dto.StatementVO;
import com.zhul.erp.modules.logistics.entity.ForwarderStatementDO;
import com.zhul.erp.modules.logistics.entity.ForwarderStatementLineDO;
import com.zhul.erp.modules.logistics.entity.LogisticsShipmentDO;
import com.zhul.erp.modules.logistics.repository.ForwarderStatementLineMapper;
import com.zhul.erp.modules.logistics.repository.ForwarderStatementMapper;
import com.zhul.erp.modules.logistics.repository.LogisticsShipmentMapper;
import com.zhul.erp.modules.logistics.support.LogisticsSupport;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 货代月结对账：按货代、月份列出已出运、还没对账的出运单，逐票填对账金额；确认后差额更新出运单运费并重新分摊，
 * 出运单标记已对账。事务边界：确认 = 对账单、出运单运费与对账标记、国际运费重新分摊。金额两位小数 HALF_UP。
 */
@Service
@RequiredArgsConstructor
public class StatementService {

    private final ForwarderStatementMapper statementMapper;
    private final ForwarderStatementLineMapper lineMapper;
    private final LogisticsShipmentMapper shipmentMapper;
    private final LogisticsShipmentService shipments;
    private final LogisticsSupport support;
    private final CurrentUserResolver currentUser;
    private final LogService logService;

    public PageResult<StatementVO> page(Integer page, Integer pageSize, Long forwarderId, Integer status) {
        LambdaQueryWrapper<ForwarderStatementDO> w = new LambdaQueryWrapper<ForwarderStatementDO>()
                .eq(ForwarderStatementDO::getTenantId, PiStore.tenantId())
                .isNull(ForwarderStatementDO::getDeletedAt);
        if (forwarderId != null) {
            w.eq(ForwarderStatementDO::getForwarderId, forwarderId);
        }
        if (status != null) {
            w.eq(ForwarderStatementDO::getStatus, status);
        }
        int size = pageSize == null || pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        int p = Math.max(1, page == null ? 1 : page);
        long total = statementMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        w.last("ORDER BY update_time DESC, id DESC LIMIT " + (long) (p - 1) * size + ", " + size);
        return PageResult.of(total, toVos(statementMapper.selectList(w), false));
    }

    public StatementVO detail(Long id) {
        return toVos(List.of(visible(id)), true).get(0);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public StatementVO create(SaveStatementRequest req) {
        if (req.getForwarderId() == null || req.getPeriod() == null) {
            throw new BizException("请选择货代与对账月份");
        }
        support.requireForwarder(req.getForwarderId());
        YearMonth ym = YearMonth.parse(req.getPeriod());
        Set<Long> taken = lineMapper.selectList(new LambdaQueryWrapper<ForwarderStatementLineDO>()
                        .select(ForwarderStatementLineDO::getLogisticsId)
                        .eq(ForwarderStatementLineDO::getTenantId, PiStore.tenantId())
                        .isNull(ForwarderStatementLineDO::getDeletedAt))
                .stream().map(ForwarderStatementLineDO::getLogisticsId).collect(Collectors.toSet());
        List<LogisticsShipmentDO> candidates = shipmentMapper.selectList(new LambdaQueryWrapper<LogisticsShipmentDO>()
                        .eq(LogisticsShipmentDO::getTenantId, PiStore.tenantId())
                        .eq(LogisticsShipmentDO::getForwarderId, req.getForwarderId())
                        .eq(LogisticsShipmentDO::getStatus, LogisticsConstants.SH_SHIPPED)
                        .eq(LogisticsShipmentDO::getReconciled, 0)
                        .ge(LogisticsShipmentDO::getShippedDate, ym.atDay(1))
                        .le(LogisticsShipmentDO::getShippedDate, ym.atEndOfMonth())
                        .isNull(LogisticsShipmentDO::getDeletedAt)
                        .orderByAsc(LogisticsShipmentDO::getShippedDate, LogisticsShipmentDO::getId))
                .stream().filter(s -> !taken.contains(s.getId())).toList();
        if (candidates.isEmpty()) {
            throw new BizException("这个月没有要对账的出运单");
        }
        ForwarderStatementDO st = new ForwarderStatementDO();
        st.setTenantId(PiStore.tenantId());
        st.setForwarderId(req.getForwarderId());
        st.setPeriod(req.getPeriod());
        st.setStatus(LogisticsConstants.STATEMENT_DRAFT);
        st.setOurTotal(BigDecimal.ZERO);
        st.setStatementTotal(BigDecimal.ZERO);
        statementMapper.insert(st);
        for (LogisticsShipmentDO s : candidates) {
            ForwarderStatementLineDO l = new ForwarderStatementLineDO();
            l.setTenantId(st.getTenantId());
            l.setStatementId(st.getId());
            l.setLogisticsId(s.getId());
            BigDecimal f = s.getFreight() == null ? BigDecimal.ZERO : s.getFreight();
            l.setOurFreight(f);
            l.setStatementAmount(f);
            l.setNote("");
            lineMapper.insert(l);
        }
        totals(st);
        logService.recordOperateLog(LogisticsConstants.MENU_STATEMENT, "新建对账单", null,
                Map.of("forwarder", req.getForwarderId(), "period", req.getPeriod(), "count", candidates.size()));
        return detail(st.getId());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public StatementVO save(Long id, SaveStatementRequest req) {
        ForwarderStatementDO st = lockDraft(id);
        Map<Long, ForwarderStatementLineDO> lines = linesOf(id).stream().collect(Collectors.toMap(ForwarderStatementLineDO::getId, l -> l));
        for (SaveStatementRequest.Line l : req.getLines() == null ? List.<SaveStatementRequest.Line>of() : req.getLines()) {
            ForwarderStatementLineDO x = lines.get(l.getId());
            if (x == null) {
                throw new BizException("出运单不在这张对账单上");
            }
            x.setStatementAmount(l.getStatementAmount().setScale(2, RoundingMode.HALF_UP));
            x.setNote(l.getNote() == null ? "" : l.getNote().trim());
            lineMapper.updateById(x);
        }
        totals(st);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public StatementVO confirm(Long id, SaveStatementRequest req) {
        if (req != null && req.getLines() != null) {
            save(id, req);
        }
        ForwarderStatementDO st = lockDraft(id);
        List<ForwarderStatementLineDO> lines = linesOf(id);
        for (ForwarderStatementLineDO l : lines) {
            if (l.getStatementAmount().compareTo(l.getOurFreight()) != 0 && (l.getNote() == null || l.getNote().isBlank())) {
                LogisticsShipmentDO s = shipmentMapper.selectById(l.getLogisticsId());
                throw new BizException((s == null ? "" : s.getShNo() + " ") + "有差额，请填写说明");
            }
        }
        for (ForwarderStatementLineDO l : lines) {
            shipments.reconcile(l.getLogisticsId(), l.getStatementAmount());
        }
        st.setStatus(LogisticsConstants.STATEMENT_CONFIRMED);
        st.setConfirmedBy(currentUser.resolve());
        st.setConfirmedAt(LocalDateTime.now());
        totals(st);
        logService.recordOperateLog(LogisticsConstants.MENU_STATEMENT, "确认对账", null, Map.of("period", st.getPeriod(),
                "our", "CNY " + st.getOurTotal(), "statement", "CNY " + st.getStatementTotal()));
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void delete(Long id) {
        ForwarderStatementDO st = lockDraft(id);
        LocalDateTime now = LocalDateTime.now();
        linesOf(id).forEach(l -> {
            l.setDeletedAt(now);
            lineMapper.updateById(l);
        });
        st.setDeletedAt(now);
        statementMapper.updateById(st);
    }

    /** 对账明细 Excel：给货代核对 */
    public TemplateFile export(Long id) {
        StatementVO vo = detail(id);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(vo.getPeriod());
            String[] head = {"出运单", "出运日期", "承运商", "运单号", "客户", "我们登记的运费（CNY）", "对账金额（CNY）", "差额", "说明"};
            Row r0 = sheet.createRow(0);
            for (int i = 0; i < head.length; i++) {
                r0.createCell(i).setCellValue(head[i]);
            }
            int n = 1;
            for (StatementVO.Line l : vo.getLines()) {
                Row r = sheet.createRow(n++);
                r.createCell(0).setCellValue(l.getShNo());
                r.createCell(1).setCellValue(l.getShippedDate() == null ? "" : l.getShippedDate().toString());
                r.createCell(2).setCellValue(l.getCarrier());
                r.createCell(3).setCellValue(l.getWaybillNo());
                r.createCell(4).setCellValue(l.getCustomerName() == null ? "" : l.getCustomerName());
                r.createCell(5).setCellValue(l.getOurFreight().doubleValue());
                r.createCell(6).setCellValue(l.getStatementAmount().doubleValue());
                r.createCell(7).setCellValue(l.getDiff().doubleValue());
                r.createCell(8).setCellValue(l.getNote());
            }
            Row t = sheet.createRow(n);
            t.createCell(0).setCellValue("合计");
            t.createCell(5).setCellValue(vo.getOurTotal().doubleValue());
            t.createCell(6).setCellValue(vo.getStatementTotal().doubleValue());
            t.createCell(7).setCellValue(vo.getDiffTotal().doubleValue());
            for (int i = 0; i < head.length; i++) {
                sheet.setColumnWidth(i, 18 * 256);
            }
            wb.write(out);
            return new TemplateFile("货代对账_" + vo.getForwarderName() + "_" + vo.getPeriod() + ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("对账明细导出失败", e);
        }
    }

    private void totals(ForwarderStatementDO st) {
        List<ForwarderStatementLineDO> lines = linesOf(st.getId());
        st.setOurTotal(lines.stream().map(ForwarderStatementLineDO::getOurFreight).reduce(BigDecimal.ZERO, BigDecimal::add));
        st.setStatementTotal(lines.stream().map(ForwarderStatementLineDO::getStatementAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        statementMapper.updateById(st);
    }

    private List<StatementVO> toVos(List<ForwarderStatementDO> rows, boolean full) {
        Map<Long, String> forwarders = support.supplierNames(rows.stream().map(ForwarderStatementDO::getForwarderId).toList());
        Map<Long, String> users = support.userNames(rows.stream().map(ForwarderStatementDO::getConfirmedBy).toList());
        List<StatementVO> out = new ArrayList<>(rows.size());
        for (ForwarderStatementDO st : rows) {
            List<ForwarderStatementLineDO> lines = linesOf(st.getId());
            StatementVO vo = new StatementVO();
            vo.setId(st.getId());
            vo.setForwarderId(st.getForwarderId());
            vo.setForwarderName(forwarders.get(st.getForwarderId()));
            vo.setPeriod(st.getPeriod());
            vo.setStatus(st.getStatus());
            vo.setStatusName(LogisticsConstants.STATEMENT_STATUS_NAMES.get(st.getStatus()));
            vo.setShipmentCount(lines.size());
            vo.setOurTotal(st.getOurTotal());
            vo.setStatementTotal(st.getStatementTotal());
            vo.setDiffTotal(st.getStatementTotal().subtract(st.getOurTotal()));
            vo.setDiffCount((int) lines.stream().filter(l -> l.getStatementAmount().compareTo(l.getOurFreight()) != 0).count());
            vo.setConfirmedByName(users.get(st.getConfirmedBy()));
            vo.setConfirmedAt(st.getConfirmedAt());
            if (full) {
                Map<Long, LogisticsShipmentDO> shs = new HashMap<>();
                if (!lines.isEmpty()) {
                    shipmentMapper.selectBatchIds(lines.stream().map(ForwarderStatementLineDO::getLogisticsId).toList())
                            .forEach(s -> shs.put(s.getId(), s));
                }
                Map<Long, String> customers = support.customerNames(shs.values().stream().map(LogisticsShipmentDO::getCustomerId).toList());
                vo.setLines(lines.stream().map(l -> {
                    LogisticsShipmentDO s = shs.get(l.getLogisticsId());
                    StatementVO.Line x = new StatementVO.Line();
                    x.setId(l.getId());
                    x.setLogisticsId(l.getLogisticsId());
                    x.setShNo(s == null ? null : s.getShNo());
                    x.setShippedDate(s == null ? null : s.getShippedDate());
                    x.setCarrier(s == null ? "" : s.getCarrier());
                    x.setWaybillNo(s == null ? "" : s.getWaybillNo());
                    x.setCustomerName(s == null ? null : customers.get(s.getCustomerId()));
                    x.setOurFreight(l.getOurFreight());
                    x.setStatementAmount(l.getStatementAmount());
                    x.setDiff(l.getStatementAmount().subtract(l.getOurFreight()));
                    x.setNote(l.getNote());
                    return x;
                }).toList());
            }
            vo.setCreateTime(st.getCreateTime());
            vo.setCreateBy(st.getCreateBy());
            vo.setUpdateTime(st.getUpdateTime());
            vo.setUpdateBy(st.getUpdateBy());
            out.add(vo);
        }
        return out;
    }

    private List<ForwarderStatementLineDO> linesOf(Long id) {
        return lineMapper.selectList(new LambdaQueryWrapper<ForwarderStatementLineDO>()
                .eq(ForwarderStatementLineDO::getStatementId, id)
                .isNull(ForwarderStatementLineDO::getDeletedAt)
                .orderByAsc(ForwarderStatementLineDO::getId));
    }

    private ForwarderStatementDO visible(Long id) {
        ForwarderStatementDO st = id == null ? null : statementMapper.selectById(id);
        if (st == null || st.getDeletedAt() != null || !Objects.equals(st.getTenantId(), PiStore.tenantId())) {
            throw new BizException("对账单不存在");
        }
        return st;
    }

    private ForwarderStatementDO lockDraft(Long id) {
        visible(id);
        statementMapper.lockById(id);
        ForwarderStatementDO st = visible(id);
        if (st.getStatus() != LogisticsConstants.STATEMENT_DRAFT) {
            throw new BizException("对账单已确认，不能修改");
        }
        return st;
    }
}
