package com.zhul.erp.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.finance.constants.FinanceConstants;
import com.zhul.erp.modules.finance.dto.AdjustCreditRequest;
import com.zhul.erp.modules.finance.dto.CreditAccountVO;
import com.zhul.erp.modules.finance.dto.CreditEntryVO;
import com.zhul.erp.modules.finance.dto.CreditQuery;
import com.zhul.erp.modules.finance.dto.CreditReconVO;
import com.zhul.erp.modules.finance.dto.CreditReconVO.Pair;
import com.zhul.erp.modules.finance.dto.CreditSummaryVO;
import com.zhul.erp.modules.finance.dto.OverpaidVO;
import com.zhul.erp.modules.finance.dto.ProcessOverpaidRequest;
import com.zhul.erp.modules.finance.dto.RefundVO;
import com.zhul.erp.modules.finance.dto.WithdrawCreditRequest;
import com.zhul.erp.modules.finance.entity.CustomerCreditConsumptionDO;
import com.zhul.erp.modules.finance.entity.CustomerCreditEntryDO;
import com.zhul.erp.modules.finance.entity.CustomerRefundDO;
import com.zhul.erp.modules.finance.entity.OtherIncomeDO;
import com.zhul.erp.modules.finance.repository.CustomerCreditConsumptionMapper;
import com.zhul.erp.modules.finance.repository.CustomerCreditEntryMapper;
import com.zhul.erp.modules.finance.repository.CustomerRefundMapper;
import com.zhul.erp.modules.finance.repository.OtherIncomeMapper;
import com.zhul.erp.modules.finance.service.CustomerCreditLedger.Account;
import com.zhul.erp.modules.finance.service.CustomerCreditLedger.Source;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.sales.support.ReceiptPosition;
import com.zhul.erp.modules.sales.support.ReceiptPosition.Position;
import com.zhul.erp.modules.system.service.BusinessUnitService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 余额管理（spec finance/customer-credit）：余额列表、汇总、流水、月度核对、手工调整、转给客户，
 * 以及收款管理的待处理多收（spec sales/payment-receipt「多收的处理」）。
 * 金额 HALF_UP 两位，汇率 6 位；事务边界：调整、转给客户、处理多收各自在一个事务内完成流水、退款单与汇兑损益。
 */
@Service
@RequiredArgsConstructor
public class CustomerCreditService {

    private static final String MENU = "余额管理";
    private static final MathContext MC = MathContext.DECIMAL128;
    private static final String KEY_SEP = "|";

    private final CustomerCreditLedger ledger;
    private final CustomerCreditEntryMapper entryMapper;
    private final CustomerCreditConsumptionMapper consumptionMapper;
    private final CustomerRefundService refundService;
    private final CustomerRefundMapper refundMapper;
    private final OtherIncomeService otherIncomeService;
    private final OtherIncomeMapper otherIncomeMapper;
    private final CreditReceiptSync receiptSync;
    private final ReceiptPosition receiptPosition;
    private final ProformaInvoiceMapper piMapper;
    private final SalesOrderMapper orderMapper;
    private final InquiryLookups lookups;
    private final BusinessUnitService businessUnitService;
    private final AttachmentService attachments;
    private final LogService logService;
    private final com.zhul.erp.framework.security.PermissionChecker perm;
    private final com.zhul.erp.framework.security.DataScopeResolver dataScopeResolver;

    // ---------------------------------------------------------------- 余额列表与汇总

    public PageResult<CreditAccountVO> page(CreditQuery q) {
        List<CreditAccountVO> all = accounts(q.getCustomerId());
        Map<Long, CustomerDO> customers = lookups.customers(all.stream().map(CreditAccountVO::getCustomerId).distinct().toList());
        String keyword = StringUtils.hasText(q.getKeyword()) ? q.getKeyword().trim().toLowerCase() : null;
        List<CreditAccountVO> rows = all.stream()
                .filter(a -> Boolean.TRUE.equals(q.getShowAll()) || a.getAvailable().signum() != 0 || a.getOccupied().signum() != 0)
                .filter(a -> !StringUtils.hasText(q.getCurrencyCode()) || q.getCurrencyCode().equals(a.getCurrencyCode()))
                .filter(a -> q.getBusinessUnitId() == null
                        || (q.getBusinessUnitId() == 0 ? a.getBusinessUnitId() == null : q.getBusinessUnitId().equals(a.getBusinessUnitId())))
                .filter(a -> keyword == null || InquiryLookups.customerName(customers.get(a.getCustomerId())).toLowerCase().contains(keyword))
                .sorted(Comparator.comparing(CreditAccountVO::getLastTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        int from = Math.min((page - 1) * size, rows.size());
        List<CreditAccountVO> slice = rows.subList(from, Math.min(from + size, rows.size()));
        slice.forEach(a -> {
            CustomerDO c = customers.get(a.getCustomerId());
            a.setCustomerName(InquiryLookups.customerName(c));
            a.setCustomerCountry(c == null ? null : c.getCountry());
        });
        return PageResult.of((long) rows.size(), slice);
    }

    /** 某客户各币种、事业部的余额（客户详情、PI 与订单的可用余额）；没有余额管理菜单的只能看数据范围内的客户 */
    public List<CreditAccountVO> ofCustomer(Long customerId) {
        requireCustomerVisible(customerId);
        return accounts(customerId);
    }

    public List<CreditEntryVO> visibleEntries(Long customerId, String currencyCode, Integer businessUnitId) {
        requireCustomerVisible(customerId);
        return entries(customerId, currencyCode, businessUnitId);
    }

    private void requireCustomerVisible(Long customerId) {
        if (perm.canAccessMenu(FinanceConstants.MENU_CREDIT)) {
            return;
        }
        CustomerDO c = lookups.customers(List.of(customerId)).get(customerId);
        if (c == null || !dataScopeResolver.current().canSee(c.getOwnerId())) {
            throw new BizException("客户不存在");
        }
    }

    public CreditSummaryVO summary() {
        List<CreditAccountVO> all = accounts(null);
        CreditSummaryVO s = new CreditSummaryVO();
        Map<String, CreditSummaryVO.CurrencyTotal> cur = new LinkedHashMap<>();
        Map<String, CreditSummaryVO.CurrencyTotal> occ = new LinkedHashMap<>();
        for (CreditAccountVO a : all) {
            if (a.getAvailable().signum() != 0) {
                CreditSummaryVO.CurrencyTotal t = cur.computeIfAbsent(a.getCurrencyCode(), CustomerCreditService::total);
                t.setAmount(t.getAmount().add(a.getAvailable()));
                t.setAmountCny(t.getAmountCny().add(a.getAvailableCny()));
                t.setCount(t.getCount() + 1);
            }
            if (a.getOccupied().signum() != 0) {
                CreditSummaryVO.CurrencyTotal t = occ.computeIfAbsent(a.getCurrencyCode(), CustomerCreditService::total);
                t.setAmount(t.getAmount().add(a.getOccupied()));
                t.setCount(t.getCount() + a.getOccupiers().size());
            }
        }
        s.setCurrencies(new ArrayList<>(cur.values()));
        s.setOccupied(new ArrayList<>(occ.values()));
        LocalDate first = LocalDate.now().withDayOfMonth(1);
        List<CustomerRefundDO> withdraws = refundMapper.selectList(new LambdaQueryWrapper<CustomerRefundDO>()
                .eq(CustomerRefundDO::getTenantId, PiStore.tenantId())
                .eq(CustomerRefundDO::getRefundType, FinanceConstants.REFUND_CREDIT_WITHDRAW)
                .eq(CustomerRefundDO::getStatus, FinanceConstants.RECORD_VALID)
                .ge(CustomerRefundDO::getRefundDate, first)
                .isNull(CustomerRefundDO::getDeletedAt));
        s.setMonthWithdrawCount(withdraws.size());
        s.setMonthWithdrawCny(withdraws.stream().map(CustomerRefundDO::getAmountCny).reduce(BigDecimal.ZERO, BigDecimal::add));
        s.setMonthFxGain(otherIncomeMapper.selectList(new LambdaQueryWrapper<OtherIncomeDO>()
                        .eq(OtherIncomeDO::getTenantId, PiStore.tenantId())
                        .eq(OtherIncomeDO::getCategory, FinanceConstants.INCOME_FX_GAIN)
                        .eq(OtherIncomeDO::getStatus, FinanceConstants.RECORD_VALID)
                        .ge(OtherIncomeDO::getIncomeDate, first)
                        .isNull(OtherIncomeDO::getDeletedAt))
                .stream().map(OtherIncomeDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        return s;
    }

    public List<CreditEntryVO> entries(Long customerId, String currencyCode, Integer businessUnitId) {
        LambdaQueryWrapper<CustomerCreditEntryDO> w = new LambdaQueryWrapper<CustomerCreditEntryDO>()
                .eq(CustomerCreditEntryDO::getTenantId, PiStore.tenantId())
                .eq(CustomerCreditEntryDO::getCustomerId, customerId)
                .eq(CustomerCreditEntryDO::getCurrencyCode, currencyCode)
                .isNull(CustomerCreditEntryDO::getDeletedAt)
                .orderByDesc(CustomerCreditEntryDO::getId);
        if (businessUnitId == null) {
            w.isNull(CustomerCreditEntryDO::getBusinessUnitId);
        } else {
            w.eq(CustomerCreditEntryDO::getBusinessUnitId, businessUnitId);
        }
        return entryMapper.selectList(w).stream().map(e -> {
            CreditEntryVO vo = new CreditEntryVO();
            vo.setId(e.getId());
            vo.setEntryType(e.getEntryType());
            vo.setTypeName(FinanceConstants.CREDIT_TYPE_NAMES.get(e.getEntryType()));
            vo.setAmount(e.getAmount());
            vo.setAmountCny(e.getAmountCny());
            vo.setSourceType(e.getSourceType());
            vo.setSourceId(e.getSourceId());
            vo.setSourceNo(e.getSourceNo());
            vo.setRemark(e.getRemark());
            vo.setCreateTime(e.getCreateTime());
            vo.setCreateBy(e.getCreateBy());
            return vo;
        }).toList();
    }

    // ---------------------------------------------------------------- 月度核对

    public CreditReconVO reconciliation(String month, String currencyCode, Integer businessUnitId) {
        YearMonth ym = StringUtils.hasText(month) ? YearMonth.parse(month) : YearMonth.now().minusMonths(1);
        String currency = StringUtils.hasText(currencyCode) ? currencyCode : "USD";
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.plusMonths(1).atDay(1).atStartOfDay();
        LambdaQueryWrapper<CustomerCreditEntryDO> w = new LambdaQueryWrapper<CustomerCreditEntryDO>()
                .eq(CustomerCreditEntryDO::getTenantId, PiStore.tenantId())
                .eq(CustomerCreditEntryDO::getCurrencyCode, currency)
                .lt(CustomerCreditEntryDO::getCreateTime, end)
                .isNull(CustomerCreditEntryDO::getDeletedAt);
        if (businessUnitId != null) {
            if (businessUnitId == 0) {
                w.isNull(CustomerCreditEntryDO::getBusinessUnitId);
            } else {
                w.eq(CustomerCreditEntryDO::getBusinessUnitId, businessUnitId);
            }
        }
        List<CustomerCreditEntryDO> rows = entryMapper.selectList(w);
        Map<Long, CustomerCreditEntryDO> byId = rows.stream().collect(Collectors.toMap(CustomerCreditEntryDO::getId, e -> e));
        Map<String, Map<String, Pair>> acc = new LinkedHashMap<>();
        for (CustomerCreditEntryDO e : rows.stream().sorted(Comparator.comparing(CustomerCreditEntryDO::getId)).toList()) {
            Map<String, Pair> cols = acc.computeIfAbsent(key(e), k -> new HashMap<>());
            String col = e.getCreateTime().isBefore(start) ? "opening" : column(e, byId);
            add(cols, col, e.getAmount(), e.getAmountCny());
        }
        Map<Integer, String> buNames = businessUnitService.names();
        Map<Long, CustomerDO> customers = lookups.customers(acc.keySet().stream().map(k -> Long.valueOf(k.split("\\|")[0])).distinct().toList());
        CreditReconVO vo = new CreditReconVO();
        vo.setMonth(ym.toString());
        vo.setCurrencyCode(currency);
        List<CreditReconVO.Row> out = new ArrayList<>();
        Map<String, Pair> sum = new HashMap<>();
        for (Map.Entry<String, Map<String, Pair>> en : acc.entrySet()) {
            String[] k = en.getKey().split("\\|", -1);
            Long customerId = Long.valueOf(k[0]);
            Integer bu = k[1].isEmpty() ? null : Integer.valueOf(k[1]);
            CreditReconVO.Row r = row(en.getValue());
            if (isZero(r)) {
                continue;
            }
            r.setCustomerId(customerId);
            r.setCustomerName(InquiryLookups.customerName(customers.get(customerId)));
            r.setBusinessUnitId(bu);
            r.setBusinessUnitName(bu == null ? null : BusinessUnitService.label(buNames, bu));
            out.add(r);
            en.getValue().forEach((c, p) -> add(sum, c, p.getAmount(), p.getAmountCny()));
        }
        out.sort(Comparator.comparing(CreditReconVO.Row::getCustomerName, Comparator.nullsLast(Comparator.naturalOrder())));
        vo.setRows(out);
        CreditReconVO.Row total = row(sum);
        total.setCustomerName("合计");
        vo.setTotal(total);
        BigDecimal check = total.getOpening().getAmount().add(total.getRefundIn().getAmount()).add(total.getCollectionIn().getAmount())
                .add(total.getOverpaidIn().getAmount()).add(total.getAdjust().getAmount())
                .subtract(total.getDeduct().getAmount()).subtract(total.getWithdraw().getAmount());
        vo.setBalanced(check.compareTo(total.getClosing().getAmount()) == 0);
        return vo;
    }

    public byte[] exportReconciliation(String month, String currencyCode, Integer businessUnitId) {
        CreditReconVO r = reconciliation(month, currencyCode, businessUnitId);
        String[] heads = {"客户", "事业部", "期初", "售后退款转入", "代收转入", "多收转入", "调整", "抵扣", "提现", "期末"};
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(r.getMonth() + " " + r.getCurrencyCode());
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            Row h1 = sheet.createRow(0);
            Row h2 = sheet.createRow(1);
            h1.createCell(0).setCellValue(heads[0]);
            h1.createCell(1).setCellValue(heads[1]);
            for (int i = 2; i < heads.length; i++) {
                h1.createCell(2 * i - 2).setCellValue(heads[i]);
                h2.createCell(2 * i - 2).setCellValue(r.getCurrencyCode());
                h2.createCell(2 * i - 1).setCellValue("人民币");
            }
            List<CreditReconVO.Row> rows = new ArrayList<>(r.getRows());
            rows.add(r.getTotal());
            int n = 2;
            for (CreditReconVO.Row x : rows) {
                Row row = sheet.createRow(n++);
                row.createCell(0).setCellValue(x.getCustomerName());
                row.createCell(1).setCellValue(x.getBusinessUnitName() == null ? "" : x.getBusinessUnitName());
                Pair[] ps = {x.getOpening(), x.getRefundIn(), x.getCollectionIn(), x.getOverpaidIn(), x.getAdjust(), x.getDeduct(),
                        x.getWithdraw(), x.getClosing()};
                for (int i = 0; i < ps.length; i++) {
                    org.apache.poi.ss.usermodel.Cell a = row.createCell(2 + 2 * i);
                    a.setCellValue(ps[i].getAmount().doubleValue());
                    a.setCellStyle(money);
                    org.apache.poi.ss.usermodel.Cell c = row.createCell(3 + 2 * i);
                    c.setCellValue(ps[i].getAmountCny().doubleValue());
                    c.setCellStyle(money);
                }
            }
            for (int i = 0; i < 18; i++) {
                sheet.setColumnWidth(i, (i < 2 ? 22 : 13) * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BizException("导出失败", e);
        }
    }

    // ---------------------------------------------------------------- 手工调整与转给客户

    @Transactional(rollbackFor = Exception.class)
    public List<CreditEntryVO> adjust(AdjustCreditRequest req) {
        BigDecimal amount = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() == 0) {
            throw new BizException("调整金额不能为 0");
        }
        Account a = new Account(req.getCustomerId(), req.getCurrencyCode(), businessUnitService.forOptional(req.getBusinessUnitId()));
        Source s = new Source(FinanceConstants.SRC_MANUAL, null, "");
        CustomerCreditEntryDO e;
        if (amount.signum() > 0) {
            if (req.getAmountCny() == null || req.getAmountCny().signum() <= 0) {
                throw new BizException("请填写人民币价值");
            }
            e = ledger.credit(a, FinanceConstants.CREDIT_ADJUST, amount, req.getAmountCny(), s, req.getReason());
        } else {
            BigDecimal available = ledger.balance(a).amount();
            if (available.compareTo(amount.negate()) < 0) {
                throw new BizException("最多能减少 " + a.currencyCode() + " " + CustomerCreditLedger.fmt(available));
            }
            e = ledger.consume(a, FinanceConstants.CREDIT_ADJUST, amount.negate(), s, req.getReason());
        }
        attachments.attach(AttachmentService.CREDIT, e.getId(), req.getAttachmentIds());
        logService.recordOperateLog(MENU, "手工调整余额", null, Map.of("customerId", a.customerId(), "currency", a.currencyCode(),
                "amount", amount.toPlainString(), "reason", req.getReason()));
        return entries(a.customerId(), a.currencyCode(), a.businessUnitId());
    }

    /** 转给客户：生成余额提现退款单与提现流水；锁定价值 − 实际转出人民币记为汇兑损益 */
    @Transactional(rollbackFor = Exception.class)
    public RefundVO withdraw(WithdrawCreditRequest req) {
        Account a = new Account(req.getCustomerId(), req.getCurrencyCode(), req.getBusinessUnitId());
        return withdraw(a, req.getAmount(), req.getWithdrawDate(), req.getMethod(), req.getMethodName(), req.getPayeeAccount(),
                req.getActualCny(), req.getAttachmentIds(), req.getRemark(), null);
    }

    // ---------------------------------------------------------------- 多收

    /** 待处理多收：已到账 + 手续费差额超过合计、还没处理完的 PI 与手动订单 */
    public List<OverpaidVO> overpaid() {
        List<ProformaInvoiceDO> pis = piMapper.selectList(new LambdaQueryWrapper<ProformaInvoiceDO>()
                .eq(ProformaInvoiceDO::getTenantId, PiStore.tenantId())
                .ne(ProformaInvoiceDO::getStatus, SalesConstants.PI_VOID)
                .isNull(ProformaInvoiceDO::getDeletedAt)
                .apply("received_amount + fee_diff_amount > total_amount"));
        List<SalesOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getTenantId, PiStore.tenantId())
                .eq(SalesOrderDO::getSource, SalesConstants.SO_MANUAL)
                .isNull(SalesOrderDO::getDeletedAt)
                .apply("received_amount + fee_diff_amount > total_amount"));
        Map<Long, SalesOrderDO> piOrders = pis.isEmpty() ? Map.of() : orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                        .in(SalesOrderDO::getPiId, pis.stream().map(ProformaInvoiceDO::getId).toList())
                        .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                        .isNull(SalesOrderDO::getDeletedAt))
                .stream().collect(Collectors.toMap(SalesOrderDO::getPiId, Function.identity(), (x, y) -> x));
        List<OverpaidVO> out = new ArrayList<>();
        for (ProformaInvoiceDO pi : pis) {
            SalesOrderDO so = piOrders.get(pi.getId());
            out.add(overpaidVo(receiptPosition.ofPi(pi), pi.getOwnerId(), so));
        }
        for (SalesOrderDO o : orders) {
            out.add(overpaidVo(receiptPosition.ofOrder(o), o.getOwnerId(), o));
        }
        List<OverpaidVO> pending = out.stream().filter(v -> v.getPending().signum() > 0).toList();
        Map<Long, CustomerDO> customers = lookups.customers(pending.stream().map(OverpaidVO::getCustomerId).distinct().toList());
        Map<Long, String> users = lookups.userNames(pending.stream().map(OverpaidVO::getSalesId).filter(Objects::nonNull).distinct().toList());
        pending.forEach(v -> {
            v.setCustomerName(InquiryLookups.customerName(customers.get(v.getCustomerId())));
            v.setSalesName(users.get(v.getSalesId()));
        });
        return pending;
    }

    @Transactional(rollbackFor = Exception.class)
    public List<OverpaidVO> processOverpaid(ProcessOverpaidRequest req) {
        Position p = position(req.getOwnerType(), req.getOwnerId());
        ledger.lock(p.customerId());
        BigDecimal amount = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal pending = receiptSync.pendingOverpaid(p);
        if (amount.compareTo(pending) > 0) {
            throw new BizException("待处理多收只有 " + p.currencyCode() + " " + CustomerCreditLedger.fmt(pending));
        }
        boolean refund = "REFUND".equals(req.getMode());
        if (!refund && !"CREDIT".equals(req.getMode())) {
            throw new BizException("处理方式不正确");
        }
        Account a = new Account(p.customerId(), p.currencyCode(), p.businessUnitId());
        CustomerCreditEntryDO in = ledger.credit(a, FinanceConstants.CREDIT_OVERPAID_IN, amount,
                amount.multiply(CreditReceiptSync.rate(p), MC), new Source(p.ownerType(), p.ownerId(), p.ownerNo()),
                StringUtils.hasText(req.getRemark()) ? req.getRemark() : "客户多付");
        if (refund) {
            if (req.getWithdrawDate() == null || req.getMethod() == null || req.getActualCny() == null) {
                throw new BizException("请填写转出日期、转出方式与实际转出人民币");
            }
            withdraw(a, amount, req.getWithdrawDate(), req.getMethod(), req.getMethodName(), req.getPayeeAccount(),
                    req.getActualCny(), req.getAttachmentIds(), "退回多收 " + p.ownerNo(), in.getId());
        }
        logService.recordOperateLog("收款管理", refund ? "多收退回客户" : "多收转客户余额", null,
                Map.of("no", p.ownerNo(), "amount", p.currencyCode() + " " + amount));
        return overpaid();
    }

    // ---------------------------------------------------------------- 内部

    private RefundVO withdraw(Account a, BigDecimal amount, LocalDate date, Integer method, String methodName, String payeeAccount,
                              BigDecimal actualCny, List<Long> attachmentIds, String remark, Long preferredLotId) {
        if (date.isAfter(LocalDate.now())) {
            throw new BizException("转出日期不能晚于今天");
        }
        if (method == null || (method != FinanceConstants.REFUND_BANK && method != FinanceConstants.REFUND_OTHER)) {
            throw new BizException("转出方式不正确");
        }
        if (method == FinanceConstants.REFUND_OTHER && !StringUtils.hasText(methodName)) {
            throw new BizException("请填写转出方式，如支付宝");
        }
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            throw new BizException("请上传转账凭证");
        }
        BigDecimal amt = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal actual = actualCny.setScale(2, RoundingMode.HALF_UP);
        ledger.lock(a.customerId());
        CustomerRefundDO f = refundService.newWithdraw(a, date, amt, actual, method, methodName, payeeAccount, remark);
        CustomerCreditEntryDO out = ledger.consume(a, FinanceConstants.CREDIT_WITHDRAW, amt,
                new Source(FinanceConstants.SRC_REFUND, f.getId(), f.getRefundNo()),
                "转给客户" + (StringUtils.hasText(payeeAccount) ? "：" + payeeAccount.trim() : ""), preferredLotId);
        refundService.bindCreditEntry(f, out.getId());
        BigDecimal value = out.getAmountCny().negate();
        otherIncomeService.createFxGain(date, value.subtract(actual), a.businessUnitId(), f.getId(),
                InquiryLookups.customerName(lookups.customers(List.of(a.customerId())).get(a.customerId())) + " " + a.currencyCode()
                        + " 余额提现 " + f.getRefundNo());
        attachments.attach(AttachmentService.REFUND, f.getId(), attachmentIds);
        logService.recordOperateLog(MENU, "转给客户", null, Map.of("refundNo", f.getRefundNo(),
                "amount", a.currencyCode() + " " + amt, "actualCny", actual.toPlainString(), "value", value.toPlainString()));
        return refundService.detail(f.getId());
    }

    private Position position(String ownerType, Long ownerId) {
        if ("PI".equals(ownerType)) {
            ProformaInvoiceDO pi = piMapper.selectById(ownerId);
            if (pi == null || pi.getDeletedAt() != null || !Objects.equals(pi.getTenantId(), PiStore.tenantId())) {
                throw new BizException("PI 不存在");
            }
            return receiptPosition.ofPi(pi);
        }
        SalesOrderDO o = orderMapper.selectById(ownerId);
        if (o == null || o.getDeletedAt() != null || !Objects.equals(o.getTenantId(), PiStore.tenantId()) || !Objects.equals(o.getSource(), SalesConstants.SO_MANUAL)) {
            throw new BizException("销售订单不存在");
        }
        return receiptPosition.ofOrder(o);
    }

    private OverpaidVO overpaidVo(Position p, Long salesId, SalesOrderDO so) {
        OverpaidVO v = new OverpaidVO();
        v.setOwnerType(p.ownerType());
        v.setOwnerId(p.ownerId());
        v.setOwnerNo(p.ownerNo());
        v.setSoId(so == null ? null : so.getId());
        v.setSoNo(so == null ? null : so.getSoNo());
        v.setCustomerId(p.customerId());
        v.setCurrencyCode(p.currencyCode());
        v.setBusinessUnitId(p.businessUnitId());
        v.setTotal(p.total());
        v.setReceived(p.received().add(p.feeDiff()));
        v.setOverpaid(p.overpaid());
        BigDecimal pending = receiptSync.pendingOverpaid(p);
        BigDecimal rate = CreditReceiptSync.rate(p);
        v.setPending(pending);
        v.setRate(rate);
        v.setPendingCny(pending.multiply(rate).setScale(2, RoundingMode.HALF_UP));
        v.setSalesId(salesId);
        return v;
    }

    /** 全部余额账户（或某客户的），含 PI 占用中与最近变动 */
    private List<CreditAccountVO> accounts(Long customerId) {
        LambdaQueryWrapper<CustomerCreditEntryDO> w = new LambdaQueryWrapper<CustomerCreditEntryDO>()
                .eq(CustomerCreditEntryDO::getTenantId, PiStore.tenantId())
                .isNull(CustomerCreditEntryDO::getDeletedAt);
        if (customerId != null) {
            w.eq(CustomerCreditEntryDO::getCustomerId, customerId);
        }
        List<CustomerCreditEntryDO> rows = entryMapper.selectList(w);
        Map<String, List<CustomerCreditEntryDO>> grouped = rows.stream().collect(Collectors.groupingBy(CustomerCreditService::key,
                LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<CreditAccountVO.Occupier>> occupiedByOut = occupiedByOut(rows);
        Map<Integer, String> buNames = businessUnitService.names();
        List<CreditAccountVO> out = new ArrayList<>(grouped.size());
        for (List<CustomerCreditEntryDO> list : grouped.values()) {
            CustomerCreditEntryDO first = list.get(0);
            CustomerCreditEntryDO last = list.stream().max(Comparator.comparing(CustomerCreditEntryDO::getId)).orElseThrow();
            CreditAccountVO a = new CreditAccountVO();
            a.setCustomerId(first.getCustomerId());
            a.setCurrencyCode(first.getCurrencyCode());
            a.setBusinessUnitId(first.getBusinessUnitId());
            a.setBusinessUnitName(first.getBusinessUnitId() == null ? null : BusinessUnitService.label(buNames, first.getBusinessUnitId()));
            a.setAvailable(list.stream().map(CustomerCreditEntryDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            a.setAvailableCny(list.stream().map(CustomerCreditEntryDO::getAmountCny).reduce(BigDecimal.ZERO, BigDecimal::add));
            List<CreditAccountVO.Occupier> occ = list.stream().map(e -> occupiedByOut.get(e.getId())).filter(Objects::nonNull)
                    .flatMap(List::stream).toList();
            a.setOccupiers(occ);
            a.setOccupied(occ.stream().map(CreditAccountVO.Occupier::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            a.setCollection(list.stream().anyMatch(e -> e.getEntryType() == FinanceConstants.CREDIT_COLLECTION_IN));
            a.setLastTime(last.getCreateTime());
            a.setLastTypeName(FinanceConstants.CREDIT_TYPE_NAMES.get(last.getEntryType()));
            a.setLastSourceNo(last.getSourceNo());
            out.add(a);
        }
        return out;
    }

    /** 还没转订单的 PI（草稿、已发送）上还没释放的抵扣占用：转出流水 ID → 占用 */
    private Map<Long, List<CreditAccountVO.Occupier>> occupiedByOut(List<CustomerCreditEntryDO> rows) {
        List<CustomerCreditEntryDO> occupies = rows.stream()
                .filter(e -> e.getEntryType() == FinanceConstants.CREDIT_OCCUPY && FinanceConstants.SRC_PI.equals(e.getSourceType()))
                .toList();
        if (occupies.isEmpty()) {
            return Map.of();
        }
        Map<Long, BigDecimal> unreleased = new HashMap<>();
        consumptionMapper.selectList(new LambdaQueryWrapper<CustomerCreditConsumptionDO>()
                        .in(CustomerCreditConsumptionDO::getOutEntryId, occupies.stream().map(CustomerCreditEntryDO::getId).toList())
                        .eq(CustomerCreditConsumptionDO::getReleased, 0)
                        .isNull(CustomerCreditConsumptionDO::getDeletedAt))
                .forEach(c -> unreleased.merge(c.getOutEntryId(), c.getAmount(), BigDecimal::add));
        Set<Long> piIds = occupies.stream().map(CustomerCreditEntryDO::getSourceId).collect(Collectors.toSet());
        Set<Long> open = piMapper.selectBatchIds(piIds).stream()
                .filter(pi -> pi.getStatus() == SalesConstants.PI_DRAFT || pi.getStatus() == SalesConstants.PI_SENT)
                .map(ProformaInvoiceDO::getId).collect(Collectors.toSet());
        Map<Long, List<CreditAccountVO.Occupier>> out = new HashMap<>();
        for (CustomerCreditEntryDO e : occupies) {
            BigDecimal amt = unreleased.get(e.getId());
            if (amt == null || amt.signum() <= 0 || !open.contains(e.getSourceId())) {
                continue;
            }
            CreditAccountVO.Occupier o = new CreditAccountVO.Occupier();
            o.setSourceType(e.getSourceType());
            o.setSourceId(e.getSourceId());
            o.setSourceNo(e.getSourceNo());
            o.setAmount(amt);
            out.put(e.getId(), List.of(o));
        }
        return out;
    }

    /** 核对表的列：转入按类型；释放、冲销归到它对应的流水所在的列 */
    private static String column(CustomerCreditEntryDO e, Map<Long, CustomerCreditEntryDO> byId) {
        int type = e.getEntryType();
        if ((type == FinanceConstants.CREDIT_RELEASE || type == FinanceConstants.CREDIT_REVERSE) && e.getRelatedEntryId() != null) {
            CustomerCreditEntryDO related = byId.get(e.getRelatedEntryId());
            if (related != null) {
                type = related.getEntryType();
            }
        }
        return switch (type) {
            case FinanceConstants.CREDIT_REFUND_IN -> "refundIn";
            case FinanceConstants.CREDIT_COLLECTION_IN -> "collectionIn";
            case FinanceConstants.CREDIT_OVERPAID_IN -> "overpaidIn";
            case FinanceConstants.CREDIT_OCCUPY, FinanceConstants.CREDIT_RELEASE -> "deduct";
            case FinanceConstants.CREDIT_WITHDRAW -> "withdraw";
            default -> "adjust";
        };
    }

    private static CreditReconVO.Row row(Map<String, Pair> cols) {
        CreditReconVO.Row r = new CreditReconVO.Row();
        r.setOpening(get(cols, "opening", false));
        r.setRefundIn(get(cols, "refundIn", false));
        r.setCollectionIn(get(cols, "collectionIn", false));
        r.setOverpaidIn(get(cols, "overpaidIn", false));
        r.setAdjust(get(cols, "adjust", false));
        r.setDeduct(get(cols, "deduct", true));
        r.setWithdraw(get(cols, "withdraw", true));
        BigDecimal closing = BigDecimal.ZERO;
        BigDecimal closingCny = BigDecimal.ZERO;
        for (Pair p : cols.values()) {
            closing = closing.add(p.getAmount());
            closingCny = closingCny.add(p.getAmountCny());
        }
        r.setClosing(Pair.of(closing, closingCny));
        return r;
    }

    private static boolean isZero(CreditReconVO.Row r) {
        return r.getOpening().getAmount().signum() == 0 && r.getClosing().getAmount().signum() == 0
                && r.getRefundIn().getAmount().signum() == 0 && r.getCollectionIn().getAmount().signum() == 0
                && r.getOverpaidIn().getAmount().signum() == 0 && r.getAdjust().getAmount().signum() == 0
                && r.getDeduct().getAmount().signum() == 0 && r.getWithdraw().getAmount().signum() == 0;
    }

    /** negate：抵扣、提现以正数显示用掉的金额 */
    private static Pair get(Map<String, Pair> cols, String col, boolean negate) {
        Pair p = cols.get(col);
        BigDecimal a = p == null ? BigDecimal.ZERO : p.getAmount();
        BigDecimal c = p == null ? BigDecimal.ZERO : p.getAmountCny();
        return negate ? Pair.of(a.negate(), c.negate()) : Pair.of(a, c);
    }

    private static void add(Map<String, Pair> cols, String col, BigDecimal amount, BigDecimal cny) {
        cols.merge(col, Pair.of(amount, cny), (x, y) -> Pair.of(x.getAmount().add(y.getAmount()), x.getAmountCny().add(y.getAmountCny())));
    }

    private static String key(CustomerCreditEntryDO e) {
        return e.getCustomerId() + KEY_SEP + (e.getBusinessUnitId() == null ? "" : e.getBusinessUnitId()) + KEY_SEP + e.getCurrencyCode();
    }

    private static CreditSummaryVO.CurrencyTotal total(String currency) {
        CreditSummaryVO.CurrencyTotal t = new CreditSummaryVO.CurrencyTotal();
        t.setCurrencyCode(currency);
        t.setAmount(BigDecimal.ZERO);
        t.setAmountCny(BigDecimal.ZERO);
        t.setCount(0);
        return t;
    }
}
