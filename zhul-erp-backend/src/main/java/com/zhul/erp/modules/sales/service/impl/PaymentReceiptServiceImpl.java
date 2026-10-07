package com.zhul.erp.modules.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.storage.PrivateFileStorage;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.ConfirmReceiptRequest;
import com.zhul.erp.modules.sales.dto.PiVO;
import com.zhul.erp.modules.sales.dto.ReceiptDeskQuery;
import com.zhul.erp.modules.sales.dto.ReceiptDeskRowVO;
import com.zhul.erp.modules.sales.dto.ReceiptVO;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.service.PaymentReceiptService;
import com.zhul.erp.modules.sales.service.PiService;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.sales.support.PiSummary;
import com.zhul.erp.modules.system.entity.BankAccountDO;
import com.zhul.erp.modules.system.entity.SysConfigDO;
import com.zhul.erp.modules.system.repository.SysConfigMapper;
import com.zhul.erp.modules.system.service.BankAccountService;
import com.zhul.erp.modules.system.service.ExchangeRateService;
import com.zhul.erp.modules.system.service.LogService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentReceiptServiceImpl implements PaymentReceiptService {

    private static final Set<String> SLIP_TYPES = Set.of("jpg", "png", "pdf");
    private static final Set<Integer> RECEIVABLE = Set.of(SalesConstants.PI_SENT, SalesConstants.PI_CONVERTED);

    private final PaymentReceiptMapper receiptMapper;
    private final SysConfigMapper sysConfigMapper;
    private final PiStore store;
    private final PiSummary summary;
    private final PiService piService;
    private final BankAccountService bankAccountService;
    private final ExchangeRateService exchangeRateService;
    private final PrivateFileStorage fileStorage;
    private final CurrentUserResolver currentUser;
    private final LogService logService;
    private final ObjectMapper objectMapper;
    private final ProformaInvoiceMapper piMapper;
    private final SalesOrderMapper orderMapper;
    private final CustomerMapper customerMapper;
    private final DataScopeResolver dataScopeResolver;
    private final InquiryLookups lookups;

    // ---------------------------------------------------------------- 水单

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO uploadSlip(Long piId, List<MultipartFile> files, BigDecimal amount, LocalDate paidDate, String note) {
        ProformaInvoiceDO pi = requireReceivable(piId);
        if (amount == null || amount.signum() <= 0) {
            throw new BizException("付款金额需要大于 0");
        }
        if (paidDate == null) {
            throw new BizException("请选择付款日期");
        }
        List<MultipartFile> list = files == null ? List.of() : files.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (list.isEmpty()) {
            throw new BizException("请上传水单（截图或 PDF）");
        }
        if (list.size() > SalesConstants.MAX_SLIP_FILES) {
            throw new BizException("一张水单最多上传 " + SalesConstants.MAX_SLIP_FILES + " 个附件");
        }
        List<ReceiptVO.SlipFile> stored = new ArrayList<>();
        for (MultipartFile f : list) {
            PrivateFileStorage.StoredFile s = fileStorage.store(SalesConstants.SLIP_MODULE, pi.getTenantId(), f, SLIP_TYPES,
                    SalesConstants.MAX_SLIP_BYTES, "水单只支持 JPG、PNG、PDF");
            ReceiptVO.SlipFile sf = new ReceiptVO.SlipFile();
            sf.setFileKey(s.fileKey());
            sf.setFileName(s.fileName());
            stored.add(sf);
        }
        PaymentReceiptDO r = new PaymentReceiptDO();
        r.setTenantId(pi.getTenantId());
        r.setPiId(piId);
        r.setKind(SalesConstants.KIND_SLIP);
        r.setCurrencyCode(pi.getCurrencyCode());
        r.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        r.setExchangeRate(pi.getExchangeRate());
        r.setAmountCny(r.getAmount().multiply(pi.getExchangeRate()).setScale(2, RoundingMode.HALF_UP));
        r.setFeeDiff(BigDecimal.ZERO);
        r.setReceiptDate(paidDate);
        r.setFileKeys(toJson(stored));
        r.setNote(trim(note, 300));
        r.setStatus(SalesConstants.RECORD_VALID);
        r.setVoidReason("");
        r.setOperatorId(currentUserId());
        receiptMapper.insert(r);
        summary.refresh(pi);
        logService.recordOperateLog(SalesConstants.MENU_PI, "上传付款水单", null, Map.of("piNo", pi.getPiNo(),
                "amount", pi.getCurrencyCode() + " " + r.getAmount(), "date", paidDate.toString(), "files", stored.size()));
        return piService.detail(piId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO deleteSlip(Long piId, Long slipId) {
        ProformaInvoiceDO pi = store.lockVisible(piId);
        PaymentReceiptDO slip = record(piId, slipId, SalesConstants.KIND_SLIP);
        boolean matched = receiptMapper.selectCount(new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getSlipId, slipId)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt)) > 0;
        if (matched) {
            throw new BizException("这张水单已有对应的到账记录，不能删除");
        }
        slip.setDeletedAt(LocalDateTime.now());
        receiptMapper.updateById(slip);
        summary.refresh(pi);
        logService.recordOperateLog(SalesConstants.MENU_PI, "删除付款水单",
                Map.of("piNo", pi.getPiNo(), "amount", pi.getCurrencyCode() + " " + slip.getAmount()), null);
        return piService.detail(piId, null);
    }

    @Override
    public SlipFile slipFile(Long piId, Long slipId, int index) {
        store.visible(piId);
        PaymentReceiptDO slip = record(piId, slipId, SalesConstants.KIND_SLIP);
        List<ReceiptVO.SlipFile> files = fromJson(slip.getFileKeys());
        if (index < 0 || index >= files.size()) {
            throw new BizException("附件不存在");
        }
        ReceiptVO.SlipFile f = files.get(index);
        return new SlipFile(fileStorage.resolveOwned(SalesConstants.SLIP_MODULE, f.getFileKey(), slip.getTenantId()), f.getFileName());
    }

    // ---------------------------------------------------------------- 到账

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO confirm(Long piId, ConfirmReceiptRequest req) {
        ProformaInvoiceDO pi = requireReceivable(piId);
        if (req.getAmount().signum() <= 0) {
            throw new BizException("到账金额需要大于 0");
        }
        BigDecimal amount = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        BankAccountDO bank = bankAccountService.requireEnabled(req.getBankAccountId());
        if (!bank.getCurrencyCode().equals(pi.getCurrencyCode())) {
            throw new BizException("收款账户币种（" + bank.getCurrencyCode() + "）与 PI 币种（" + pi.getCurrencyCode() + "）不同");
        }
        if (req.getSlipId() != null) {
            record(piId, req.getSlipId(), SalesConstants.KIND_SLIP);
            boolean used = receiptMapper.selectCount(new LambdaQueryWrapper<PaymentReceiptDO>()
                    .eq(PaymentReceiptDO::getSlipId, req.getSlipId())
                    .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                    .isNull(PaymentReceiptDO::getDeletedAt)) > 0;
            if (used) {
                throw new BizException("这张水单已登记过到账");
            }
        }
        BigDecimal remaining = pi.getTotalAmount().subtract(pi.getReceivedAmount()).subtract(pi.getFeeDiffAmount());
        BigDecimal after = remaining.subtract(amount);
        BigDecimal feeDiff = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(req.getFeeDiff())) {
            BigDecimal tolerance = feeTolerance();
            if (after.signum() <= 0) {
                throw new BizException("本次到账后没有剩余差额，不需要记为手续费");
            }
            if (after.compareTo(tolerance) > 0) {
                throw new BizException("差额 " + pi.getCurrencyCode() + " " + after.toPlainString() + " 超过可记为手续费的上限 "
                        + pi.getCurrencyCode() + " " + tolerance.toPlainString());
            }
            feeDiff = after;
        }
        ExchangeRateService.Snapshot rate = exchangeRateService.require(pi.getCurrencyCode());
        PaymentReceiptDO r = new PaymentReceiptDO();
        r.setTenantId(pi.getTenantId());
        r.setPiId(piId);
        r.setKind(SalesConstants.KIND_RECEIPT);
        r.setCurrencyCode(pi.getCurrencyCode());
        r.setAmount(amount);
        r.setExchangeRate(rate.rate());
        r.setAmountCny(amount.multiply(rate.rate()).setScale(2, RoundingMode.HALF_UP));
        r.setFeeDiff(feeDiff);
        r.setReceiptDate(req.getReceiptDate());
        r.setBankAccountId(bank.getId());
        r.setSlipId(req.getSlipId());
        r.setFileKeys("[]");
        r.setNote(trim(req.getNote(), 300));
        r.setStatus(SalesConstants.RECORD_VALID);
        r.setVoidReason("");
        r.setOperatorId(currentUserId());
        receiptMapper.insert(r);
        summary.refresh(pi);
        Map<String, Object> after2 = new LinkedHashMap<>();
        after2.put("piNo", pi.getPiNo());
        after2.put("amount", pi.getCurrencyCode() + " " + amount);
        after2.put("amountCny", r.getAmountCny());
        after2.put("rate", rate.rate());
        after2.put("feeDiff", feeDiff);
        after2.put("bank", bank.getBankName() + " " + BankAccountService.mask(bank.getAccountNo()));
        after2.put("receiptStatus", SalesConstants.RECEIPT_STATUS_NAMES.get(pi.getReceiptStatus()));
        logService.recordOperateLog(SalesConstants.MENU_PI, "登记到账", null, after2);
        return piService.detail(piId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO voidReceipt(Long piId, Long receiptId, String reason) {
        ProformaInvoiceDO pi = store.lockVisible(piId);
        PaymentReceiptDO r = record(piId, receiptId, SalesConstants.KIND_RECEIPT);
        if (r.getStatus() == SalesConstants.RECORD_VOID) {
            throw new BizException("这笔到账已作废");
        }
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw new BizException("请填写作废原因");
        }
        r.setStatus(SalesConstants.RECORD_VOID);
        r.setVoidReason(trim(why, 200));
        receiptMapper.updateById(r);
        summary.refresh(pi);
        logService.recordOperateLog(SalesConstants.MENU_PI, "作废到账",
                Map.of("piNo", pi.getPiNo(), "amount", pi.getCurrencyCode() + " " + r.getAmount(), "status", "有效"),
                Map.of("piNo", pi.getPiNo(), "status", "已作废", "reason", r.getVoidReason()));
        return piService.detail(piId, null);
    }

    // ---------------------------------------------------------------- 到账登记工作列表

    @Override
    public PageResult<ReceiptDeskRowVO> desk(ReceiptDeskQuery q) {
        int tenant = PiStore.tenantId();
        LambdaQueryWrapper<ProformaInvoiceDO> w = new LambdaQueryWrapper<ProformaInvoiceDO>()
                .eq(ProformaInvoiceDO::getTenantId, tenant)
                .in(ProformaInvoiceDO::getStatus, RECEIVABLE)
                .isNull(ProformaInvoiceDO::getDeletedAt);
        dataScopeResolver.current().apply(w, ProformaInvoiceDO::getOwnerId);
        if (q.getReceiptStatus() != null) {
            w.eq(ProformaInvoiceDO::getReceiptStatus, q.getReceiptStatus());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            List<Long> customerIds = customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                            .select(CustomerDO::getId)
                            .eq(CustomerDO::getTenantId, tenant)
                            .and(x -> x.like(CustomerDO::getName, kw).or().like(CustomerDO::getShortName, kw))
                            .last("LIMIT 500"))
                    .stream().map(CustomerDO::getId).toList();
            // 编号按包含匹配：客户水单上写的不带前缀的编号也能找到
            w.and(x -> {
                x.like(ProformaInvoiceDO::getPiNo, kw);
                if (!customerIds.isEmpty()) {
                    x.or().in(ProformaInvoiceDO::getCustomerId, customerIds);
                }
            });
        }
        List<ProformaInvoiceDO> pis = piMapper.selectList(w);
        if (pis.isEmpty()) {
            return PageResult.of(0L, List.of());
        }
        List<Long> ids = pis.stream().map(ProformaInvoiceDO::getId).toList();
        List<PaymentReceiptDO> records = receiptMapper.selectList(new LambdaQueryWrapper<PaymentReceiptDO>()
                .in(PaymentReceiptDO::getPiId, ids)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt));
        Set<Long> matched = new HashSet<>();
        records.stream().filter(r -> r.getKind() == SalesConstants.KIND_RECEIPT && r.getSlipId() != null)
                .forEach(r -> matched.add(r.getSlipId()));
        Map<Long, List<PaymentReceiptDO>> pending = new HashMap<>();
        records.stream().filter(r -> r.getKind() == SalesConstants.KIND_SLIP && !matched.contains(r.getId()))
                .sorted(Comparator.comparing(PaymentReceiptDO::getReceiptDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(r -> pending.computeIfAbsent(r.getPiId(), k -> new ArrayList<>()).add(r));

        boolean all = Boolean.TRUE.equals(q.getAll());
        List<ProformaInvoiceDO> rows = pis.stream()
                .filter(p -> all || pending.containsKey(p.getId()) || (p.getReceivedAmount().signum() > 0
                        && p.getReceivedAmount().add(p.getFeeDiffAmount()).compareTo(p.getTotalAmount()) < 0))
                // 最早一张待确认水单在前，没有待确认水单的排后面
                .sorted(Comparator.<ProformaInvoiceDO, LocalDate>comparing(p -> earliest(pending.get(p.getId())),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ProformaInvoiceDO::getCreateTime, Comparator.reverseOrder()))
                .toList();
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        List<ProformaInvoiceDO> slice = rows.stream().skip((long) (page - 1) * size).limit(size).toList();
        if (slice.isEmpty()) {
            return PageResult.of((long) rows.size(), List.of());
        }
        Map<Long, CustomerDO> customers = lookups.customers(slice.stream().map(ProformaInvoiceDO::getCustomerId).toList());
        Map<Long, String> users = lookups.userNames(slice.stream().map(ProformaInvoiceDO::getOwnerId).toList());
        Map<Long, SalesOrderDO> orders = orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                        .in(SalesOrderDO::getPiId, slice.stream().map(ProformaInvoiceDO::getId).toList())
                        .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                        .isNull(SalesOrderDO::getDeletedAt))
                .stream().collect(Collectors.toMap(SalesOrderDO::getPiId, o -> o, (a, b) -> a));
        List<ReceiptDeskRowVO> list = new ArrayList<>(slice.size());
        for (ProformaInvoiceDO p : slice) {
            ReceiptDeskRowVO vo = new ReceiptDeskRowVO();
            vo.setPiId(p.getId());
            vo.setPiNo(p.getPiNo());
            vo.setPiStatus(p.getStatus());
            vo.setCustomerId(p.getCustomerId());
            vo.setCustomerName(InquiryLookups.customerName(customers.get(p.getCustomerId())));
            vo.setOwnerId(p.getOwnerId());
            vo.setOwnerName(users.get(p.getOwnerId()));
            vo.setCurrencyCode(p.getCurrencyCode());
            vo.setTotalAmount(p.getTotalAmount());
            vo.setReceivedAmount(p.getReceivedAmount());
            vo.setFeeDiffAmount(p.getFeeDiffAmount());
            vo.setRemainingAmount(p.getTotalAmount().subtract(p.getReceivedAmount()).subtract(p.getFeeDiffAmount()));
            vo.setReceiptStatus(p.getReceiptStatus());
            vo.setReceiptStatusName(SalesConstants.RECEIPT_STATUS_NAMES.get(p.getReceiptStatus()));
            SalesOrderDO o = orders.get(p.getId());
            vo.setOrderId(o == null ? null : o.getId());
            vo.setSoNo(o == null ? null : o.getSoNo());
            List<PaymentReceiptDO> slips = pending.getOrDefault(p.getId(), List.of());
            vo.setPendingSlips(slips.stream().map(r -> {
                ReceiptVO s = new ReceiptVO();
                s.setId(r.getId());
                s.setKind(r.getKind());
                s.setAmount(r.getAmount());
                s.setAmountCny(r.getAmountCny());
                s.setReceiptDate(r.getReceiptDate());
                s.setFiles(fromJson(r.getFileKeys()));
                s.setNote(r.getNote());
                s.setStatus(r.getStatus());
                s.setMatched(false);
                s.setCreateTime(r.getCreateTime());
                return s;
            }).toList());
            vo.setEarliestSlipDate(earliest(slips));
            list.add(vo);
        }
        return PageResult.of((long) rows.size(), list);
    }

    private static LocalDate earliest(List<PaymentReceiptDO> slips) {
        return slips == null ? null : slips.stream().map(PaymentReceiptDO::getReceiptDate).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(null);
    }

    @Override
    public BigDecimal feeTolerance() {
        SysConfigDO row = config(PiStore.tenantId());
        if (row == null && PiStore.tenantId() != 0) {
            row = config(0);
        }
        try {
            return row == null ? BigDecimal.valueOf(SalesConstants.DEFAULT_FEE_TOLERANCE) : new BigDecimal(row.getConfigValue().trim());
        } catch (NumberFormatException e) {
            return BigDecimal.valueOf(SalesConstants.DEFAULT_FEE_TOLERANCE);
        }
    }

    // ---------------------------------------------------------------- 内部

    /** 锁住 PI：只有已发送或已转订单的 PI 可以登记收款 */
    private ProformaInvoiceDO requireReceivable(Long piId) {
        ProformaInvoiceDO pi = store.lockVisible(piId);
        if (!RECEIVABLE.contains(pi.getStatus())) {
            throw new BizException(pi.getStatus() == SalesConstants.PI_VOID ? "PI 已作废，不能登记收款" : "PI 还没有发送，不能登记收款");
        }
        return pi;
    }

    private PaymentReceiptDO record(Long piId, Long id, int kind) {
        PaymentReceiptDO r = id == null ? null : receiptMapper.selectById(id);
        if (r == null || r.getDeletedAt() != null || !Objects.equals(r.getPiId(), piId) || r.getKind() != kind) {
            throw new BizException(kind == SalesConstants.KIND_SLIP ? "水单不存在" : "到账记录不存在");
        }
        return r;
    }

    private SysConfigDO config(int tenant) {
        return sysConfigMapper.selectOne(new LambdaQueryWrapper<SysConfigDO>()
                .eq(SysConfigDO::getConfigKey, SalesConstants.CONFIG_FEE_TOLERANCE)
                .eq(SysConfigDO::getTenantId, tenant)
                .isNull(SysConfigDO::getDeletedAt)
                .last("LIMIT 1"));
    }

    private String toJson(List<ReceiptVO.SlipFile> files) {
        try {
            return objectMapper.writeValueAsString(files);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("水单附件序列化失败", e);
        }
    }

    private List<ReceiptVO.SlipFile> fromJson(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "[]" : json, new TypeReference<List<ReceiptVO.SlipFile>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("水单附件解析失败", e);
        }
    }

    private Long currentUserId() {
        Long id = currentUser.resolve();
        return id == null ? 0L : id;
    }

    private static String trim(String s, int max) {
        String t = s == null ? "" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
