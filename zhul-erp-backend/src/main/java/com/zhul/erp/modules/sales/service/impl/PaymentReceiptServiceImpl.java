package com.zhul.erp.modules.sales.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.framework.security.DataScopeResolver;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.framework.storage.PrivateFileStorage;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.ClaimReceiptRequest;
import com.zhul.erp.modules.sales.dto.ConfirmReceiptRequest;
import com.zhul.erp.modules.sales.dto.PlatformReceiptRequest;
import com.zhul.erp.modules.sales.dto.ReceiptRowVO;
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
import com.zhul.erp.modules.sales.service.SalesOrderService;
import com.zhul.erp.modules.sales.dto.SalesOrderVO;
import com.zhul.erp.modules.sales.support.PaymentMethods;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.sales.support.ReceiptMoney;
import com.zhul.erp.modules.sales.support.ReceiptRows;
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
    private final PaymentMethods paymentMethods;
    private final ReceiptRows receiptRows;
    private final PermissionChecker perm;
    private final SalesOrderService orderService;

    // ---------------------------------------------------------------- 水单

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO uploadSlip(Long piId, List<MultipartFile> files, BigDecimal amount, LocalDate paidDate, String paymentMethod, String note) {
        doUploadSlip(Owner.of(requireReceivable(piId)), files, amount, paidDate, paymentMethod, note);
        return piService.detail(piId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO uploadOrderSlip(Long soId, List<MultipartFile> files, BigDecimal amount, LocalDate paidDate, String paymentMethod, String note) {
        doUploadSlip(Owner.of(requireOrder(soId, true)), files, amount, paidDate, paymentMethod, note);
        return orderService.detail(soId);
    }

    private void doUploadSlip(Owner o, List<MultipartFile> files, BigDecimal amount, LocalDate paidDate, String paymentMethod, String note) {
        PaymentMethods.Method method = paymentMethods.offline(paymentMethod);
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
            PrivateFileStorage.StoredFile s = fileStorage.store(SalesConstants.SLIP_MODULE, o.tenantId(), f, SLIP_TYPES,
                    SalesConstants.MAX_SLIP_BYTES, "水单只支持 JPG、PNG、PDF");
            ReceiptVO.SlipFile sf = new ReceiptVO.SlipFile();
            sf.setFileKey(s.fileKey());
            sf.setFileName(s.fileName());
            stored.add(sf);
        }
        PaymentReceiptDO r = new PaymentReceiptDO();
        r.setTenantId(o.tenantId());
        o.bind(r);
        r.setKind(SalesConstants.KIND_SLIP);
        r.setCurrencyCode(o.currency());
        setMethod(r, method);
        r.setPlatformFee(BigDecimal.ZERO);
        r.setNetAmount(BigDecimal.ZERO);
        r.setNetAmountCny(BigDecimal.ZERO);
        r.setRateSource(SalesConstants.RATE_SYSTEM);
        r.setPayer("");
        r.setAmount(amount.setScale(2, RoundingMode.HALF_UP));
        r.setExchangeRate(o.rate());
        r.setAmountCny(r.getAmount().multiply(o.rate()).setScale(2, RoundingMode.HALF_UP));
        r.setFeeDiff(BigDecimal.ZERO);
        r.setReceiptDate(paidDate);
        r.setFileKeys(toJson(stored));
        r.setNote(trim(note, 300));
        r.setStatus(SalesConstants.RECORD_VALID);
        r.setVoidReason("");
        r.setOperatorId(currentUserId());
        receiptMapper.insert(r);
        refresh(o);
        logService.recordOperateLog(o.menu(), "上传付款水单", null, Map.of(o.noKey(), o.no(),
                "amount", o.currency() + " " + r.getAmount(), "date", paidDate.toString(), "files", stored.size()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO deleteSlip(Long piId, Long slipId) {
        doDeleteSlip(Owner.of(store.lockVisible(piId)), slipId);
        return piService.detail(piId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO deleteOrderSlip(Long soId, Long slipId) {
        doDeleteSlip(Owner.of(requireOrder(soId, false)), slipId);
        return orderService.detail(soId);
    }

    private void doDeleteSlip(Owner o, Long slipId) {
        PaymentReceiptDO slip = record(o, slipId, SalesConstants.KIND_SLIP);
        boolean matched = receiptMapper.selectCount(new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getSlipId, slipId)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt)) > 0;
        if (matched) {
            throw new BizException("这张水单已有对应的到账记录，不能删除");
        }
        slip.setDeletedAt(LocalDateTime.now());
        receiptMapper.updateById(slip);
        refresh(o);
        logService.recordOperateLog(o.menu(), "删除付款水单",
                Map.of(o.noKey(), o.no(), "amount", o.currency() + " " + slip.getAmount()), null);
    }

    @Override
    public SlipFile slipFile(Long piId, Long slipId, int index) {
        return slipFileOf(Owner.of(store.visible(piId)), slipId, index);
    }

    @Override
    public SlipFile orderSlipFile(Long soId, Long slipId, int index) {
        return slipFileOf(Owner.of(visibleOrder(soId)), slipId, index);
    }

    private SlipFile slipFileOf(Owner o, Long slipId, int index) {
        PaymentReceiptDO slip = record(o, slipId, SalesConstants.KIND_SLIP);
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
        doConfirm(Owner.of(requireReceivable(piId)), req);
        return piService.detail(piId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO confirmOrder(Long soId, ConfirmReceiptRequest req) {
        doConfirm(Owner.of(requireOrder(soId, true)), req);
        return orderService.detail(soId);
    }

    private void doConfirm(Owner o, ConfirmReceiptRequest req) {
        if (req.getAmount().signum() <= 0) {
            throw new BizException("到账金额需要大于 0");
        }
        BigDecimal amount = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        BankAccountDO bank = bankAccountService.requireEnabled(req.getBankAccountId());
        if (!bank.getCurrencyCode().equals(o.currency())) {
            throw new BizException("收款账户币种（" + bank.getCurrencyCode() + "）与" + o.label() + "币种（" + o.currency() + "）不同");
        }
        PaymentReceiptDO slip = req.getSlipId() == null ? null : requireFreeSlip(o, req.getSlipId());
        PaymentMethods.Method method = paymentMethods.offline(StringUtils.hasText(req.getPaymentMethod()) ? req.getPaymentMethod()
                : slip != null ? slip.getPaymentMethod() : null);
        BigDecimal after = o.remaining().subtract(amount);
        BigDecimal feeDiff = BigDecimal.ZERO;
        if (Boolean.TRUE.equals(req.getFeeDiff())) {
            BigDecimal tolerance = feeTolerance();
            if (after.signum() <= 0) {
                throw new BizException("本次到账后没有剩余差额，不需要记为手续费");
            }
            if (after.compareTo(tolerance) > 0) {
                throw new BizException("差额 " + o.currency() + " " + after.toPlainString() + " 超过可记为手续费的上限 "
                        + o.currency() + " " + tolerance.toPlainString());
            }
            feeDiff = after;
        }
        ExchangeRateService.Snapshot rate = exchangeRateService.require(o.currency());
        PaymentReceiptDO r = new PaymentReceiptDO();
        r.setTenantId(o.tenantId());
        o.bind(r);
        r.setKind(SalesConstants.KIND_RECEIPT);
        r.setCurrencyCode(o.currency());
        setMethod(r, method);
        ReceiptMoney.apply(r, amount, BigDecimal.ZERO, rate.rate(), req.getActualAmountCny());
        r.setPayer("");
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
        refresh(o);
        Map<String, Object> after2 = new LinkedHashMap<>();
        after2.put(o.noKey(), o.no());
        after2.put("amount", o.currency() + " " + amount);
        after2.put("amountCny", r.getAmountCny());
        after2.put("rate", r.getExchangeRate());
        after2.put("netAmountCny", r.getNetAmountCny());
        after2.put("paymentMethod", method.name());
        after2.put("feeDiff", feeDiff);
        after2.put("bank", bank.getBankName() + " " + BankAccountService.mask(bank.getAccountNo()));
        after2.put("receiptStatus", SalesConstants.RECEIPT_STATUS_NAMES.get(o.receiptStatus()));
        logService.recordOperateLog(o.menu(), "登记到账", null, after2);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO voidReceipt(Long piId, Long receiptId, String reason) {
        doVoid(Owner.of(store.lockVisible(piId)), receiptId, reason);
        return piService.detail(piId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO voidOrderReceipt(Long soId, Long receiptId, String reason) {
        doVoid(Owner.of(requireOrder(soId, false)), receiptId, reason);
        return orderService.detail(soId);
    }

    private void doVoid(Owner o, Long receiptId, String reason) {
        PaymentReceiptDO r = record(o, receiptId, SalesConstants.KIND_RECEIPT);
        if (r.getStatus() == SalesConstants.RECORD_VOID) {
            throw new BizException("这笔到账已作废");
        }
        // 没有「登记到账」权限时只能作废自己登记的平台收款
        if (!perm.has(SalesConstants.PERM_RECEIPT_CONFIRM)
                && !(StringUtils.hasText(r.getPlatformOrderNo()) && Objects.equals(r.getOperatorId(), currentUserId()))) {
            throw new BizException("只能作废自己登记的平台收款");
        }
        String why = reason == null ? "" : reason.trim();
        if (why.isEmpty()) {
            throw new BizException("请填写作废原因");
        }
        r.setStatus(SalesConstants.RECORD_VOID);
        r.setVoidReason(trim(why, 200));
        receiptMapper.updateById(r);
        refresh(o);
        logService.recordOperateLog(o.menu(), "作废到账",
                Map.of(o.noKey(), o.no(), "amount", o.currency() + " " + r.getAmount(), "status", "有效"),
                Map.of(o.noKey(), o.no(), "status", "已作废", "reason", r.getVoidReason()));
    }

    // ---------------------------------------------------------------- 平台收款与认领

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO platformReceipt(Long piId, PlatformReceiptRequest req) {
        doPlatform(Owner.of(requireReceivable(piId)), req);
        return piService.detail(piId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO orderPlatformReceipt(Long soId, PlatformReceiptRequest req) {
        doPlatform(Owner.of(requireOrder(soId, true)), req);
        return orderService.detail(soId);
    }

    private void doPlatform(Owner o, PlatformReceiptRequest req) {
        PaymentMethods.Method method = paymentMethods.online(req.getPaymentMethod());
        String orderNo = req.getPlatformOrderNo() == null ? "" : req.getPlatformOrderNo().trim();
        if (orderNo.isEmpty()) {
            throw new BizException("请填写平台订单号");
        }
        if (req.getAmount().signum() <= 0) {
            throw new BizException("客户付款金额需要大于 0");
        }
        PaymentReceiptDO dup = receiptMapper.selectOne(new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getTenantId, o.tenantId())
                .eq(PaymentReceiptDO::getPaymentMethod, method.code())
                .eq(PaymentReceiptDO::getPlatformOrderNo, orderNo)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt)
                .last("LIMIT 1"));
        if (dup != null) {
            String other = ownerNo(dup);
            throw new BizException("平台订单号 " + orderNo + " 已登记过" + (other == null ? "" : "（" + other + "）"));
        }
        BigDecimal amount = req.getAmount().setScale(2, RoundingMode.HALF_UP);
        ExchangeRateService.Snapshot rate = exchangeRateService.require(o.currency());
        PaymentReceiptDO r = new PaymentReceiptDO();
        r.setTenantId(o.tenantId());
        o.bind(r);
        r.setKind(SalesConstants.KIND_RECEIPT);
        r.setCurrencyCode(o.currency());
        setMethod(r, method);
        r.setPlatformOrderNo(orderNo);
        ReceiptMoney.apply(r, amount, req.getPlatformFee(), rate.rate(), req.getActualAmountCny());
        r.setFeeDiff(BigDecimal.ZERO);
        r.setPayer("");
        r.setReceiptDate(req.getReceiptDate());
        r.setFileKeys("[]");
        r.setNote(trim(req.getNote(), 300));
        r.setStatus(SalesConstants.RECORD_VALID);
        r.setVoidReason("");
        r.setOperatorId(currentUserId());
        receiptMapper.insert(r);
        refresh(o);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put(o.noKey(), o.no());
        after.put("paymentMethod", method.name());
        after.put("platformOrderNo", orderNo);
        after.put("amount", o.currency() + " " + amount);
        after.put("platformFee", r.getPlatformFee());
        after.put("netAmountCny", r.getNetAmountCny());
        logService.recordOperateLog(o.menu(), "登记平台收款", null, after);
    }

    @Override
    public List<ReceiptRowVO> claimable(Long piId) {
        return claimableFor(Owner.of(store.visible(piId)));
    }

    @Override
    public List<ReceiptRowVO> orderClaimable(Long soId) {
        return claimableFor(Owner.of(visibleOrder(soId)));
    }

    private List<ReceiptRowVO> claimableFor(Owner o) {
        return receiptRows.of(receiptMapper.selectList(new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getTenantId, o.tenantId())
                .isNull(PaymentReceiptDO::getPiId)
                .isNull(PaymentReceiptDO::getSoId)
                .eq(PaymentReceiptDO::getKind, SalesConstants.KIND_RECEIPT)
                .eq(PaymentReceiptDO::getCurrencyCode, o.currency())
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt)
                .orderByDesc(PaymentReceiptDO::getReceiptDate)
                .orderByDesc(PaymentReceiptDO::getId)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PiVO claim(Long piId, ClaimReceiptRequest req) {
        doClaim(Owner.of(requireReceivable(piId)), req);
        return piService.detail(piId, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalesOrderVO claimToOrder(Long soId, ClaimReceiptRequest req) {
        doClaim(Owner.of(requireOrder(soId, true)), req);
        return orderService.detail(soId);
    }

    private void doClaim(Owner o, ClaimReceiptRequest req) {
        PaymentReceiptDO r = receiptMapper.selectOne(new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getId, req.getReceiptId())
                .last("FOR UPDATE"));
        if (r == null || r.getDeletedAt() != null || !Objects.equals(r.getTenantId(), o.tenantId())
                || r.getKind() != SalesConstants.KIND_RECEIPT || r.getStatus() != SalesConstants.RECORD_VALID) {
            throw new BizException("到账记录不存在");
        }
        if (r.getPiId() != null || r.getSoId() != null) {
            String other = ownerNo(r);
            throw new BizException("这笔到账已被 " + (other == null ? "其他单据" : other) + " 认领");
        }
        if (!r.getCurrencyCode().equals(o.currency())) {
            throw new BizException("币种不同（到账 " + r.getCurrencyCode() + "，" + o.label().trim() + " " + o.currency() + "），不能认领");
        }
        if (req.getSlipId() != null) {
            requireFreeSlip(o, req.getSlipId());
        }
        o.bind(r);
        r.setSlipId(req.getSlipId());
        r.setClaimedBy(currentUserId());
        r.setClaimedAt(LocalDateTime.now());
        receiptMapper.updateById(r);
        refresh(o);
        logService.recordOperateLog(o.menu(), "认领到账", null, Map.of(o.noKey(), o.no(),
                "amount", r.getCurrencyCode() + " " + r.getAmount(), "payer", r.getPayer(), "receiptId", r.getId()));
    }

    /** 收款记录所属单据的编号（PI 编号或订单编号），未认领时为空 */
    private String ownerNo(PaymentReceiptDO r) {
        if (r.getPiId() != null) {
            ProformaInvoiceDO pi = piMapper.selectById(r.getPiId());
            return pi == null ? null : pi.getPiNo();
        }
        if (r.getSoId() != null) {
            SalesOrderDO so = orderMapper.selectById(r.getSoId());
            return so == null ? null : so.getSoNo();
        }
        return null;
    }

    private PaymentReceiptDO requireFreeSlip(Owner o, Long slipId) {
        PaymentReceiptDO slip = record(o, slipId, SalesConstants.KIND_SLIP);
        boolean used = receiptMapper.selectCount(new LambdaQueryWrapper<PaymentReceiptDO>()
                .eq(PaymentReceiptDO::getSlipId, slipId)
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt)) > 0;
        if (used) {
            throw new BizException("这张水单已登记过到账");
        }
        return slip;
    }

    static void setMethod(PaymentReceiptDO r, PaymentMethods.Method m) {
        r.setPaymentMethod(m.code());
        r.setPaymentMethodName(m.name());
        r.setChannel(m.channel());
        if (r.getPlatformOrderNo() == null) {
            r.setPlatformOrderNo("");
        }
    }

    // ---------------------------------------------------------------- 到账登记工作列表

    @Override
    public PageResult<ReceiptDeskRowVO> desk(ReceiptDeskQuery q) {
        int tenant = PiStore.tenantId();
        List<Long> customerIds = StringUtils.hasText(q.getKeyword()) ? customerMapper.selectList(new LambdaQueryWrapper<CustomerDO>()
                        .select(CustomerDO::getId)
                        .eq(CustomerDO::getTenantId, tenant)
                        .and(x -> x.like(CustomerDO::getName, q.getKeyword().trim()).or().like(CustomerDO::getShortName, q.getKeyword().trim()))
                        .last("LIMIT 500"))
                .stream().map(CustomerDO::getId).toList() : List.of();
        LambdaQueryWrapper<ProformaInvoiceDO> w = new LambdaQueryWrapper<ProformaInvoiceDO>()
                .eq(ProformaInvoiceDO::getTenantId, tenant)
                .in(ProformaInvoiceDO::getStatus, RECEIVABLE)
                .isNull(ProformaInvoiceDO::getDeletedAt);
        dataScopeResolver.current().apply(w, ProformaInvoiceDO::getOwnerId);
        // 手动创建的有效订单：收款记在订单上，同样出现在待确认
        LambdaQueryWrapper<SalesOrderDO> ow = new LambdaQueryWrapper<SalesOrderDO>()
                .eq(SalesOrderDO::getTenantId, tenant)
                .eq(SalesOrderDO::getSource, SalesConstants.SO_MANUAL)
                .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                .isNull(SalesOrderDO::getDeletedAt);
        dataScopeResolver.current().apply(ow, SalesOrderDO::getOwnerId);
        if (q.getReceiptStatus() != null) {
            w.eq(ProformaInvoiceDO::getReceiptStatus, q.getReceiptStatus());
            ow.eq(SalesOrderDO::getReceiptStatus, q.getReceiptStatus());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            // 编号按包含匹配：客户水单上写的不带前缀的编号也能找到
            w.and(x -> {
                x.like(ProformaInvoiceDO::getPiNo, kw);
                if (!customerIds.isEmpty()) {
                    x.or().in(ProformaInvoiceDO::getCustomerId, customerIds);
                }
            });
            ow.and(x -> {
                x.like(SalesOrderDO::getSoNo, kw);
                if (!customerIds.isEmpty()) {
                    x.or().in(SalesOrderDO::getCustomerId, customerIds);
                }
            });
        }
        List<DeskRow> all = new ArrayList<>();
        piMapper.selectList(w).forEach(p -> all.add(new DeskRow(p, null)));
        orderMapper.selectList(ow).forEach(o -> all.add(new DeskRow(null, o)));
        if (all.isEmpty()) {
            return PageResult.of(0L, List.of());
        }
        List<Long> piIds = all.stream().filter(r -> r.pi() != null).map(r -> r.pi().getId()).toList();
        List<Long> soIds = all.stream().filter(r -> r.so() != null).map(r -> r.so().getId()).toList();
        List<PaymentReceiptDO> records = receiptMapper.selectList(new LambdaQueryWrapper<PaymentReceiptDO>()
                .and(x -> {
                    if (!piIds.isEmpty()) {
                        x.in(PaymentReceiptDO::getPiId, piIds);
                    }
                    if (!soIds.isEmpty()) {
                        x.or().in(PaymentReceiptDO::getSoId, soIds);
                    }
                })
                .eq(PaymentReceiptDO::getStatus, SalesConstants.RECORD_VALID)
                .isNull(PaymentReceiptDO::getDeletedAt));
        Set<Long> matched = new HashSet<>();
        records.stream().filter(r -> r.getKind() == SalesConstants.KIND_RECEIPT && r.getSlipId() != null)
                .forEach(r -> matched.add(r.getSlipId()));
        Map<String, List<PaymentReceiptDO>> pending = new HashMap<>();
        records.stream().filter(r -> r.getKind() == SalesConstants.KIND_SLIP && !matched.contains(r.getId()))
                .sorted(Comparator.comparing(PaymentReceiptDO::getReceiptDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .forEach(r -> pending.computeIfAbsent(DeskRow.key(r.getPiId(), r.getSoId()), k -> new ArrayList<>()).add(r));

        boolean showAll = Boolean.TRUE.equals(q.getAll());
        List<DeskRow> rows = all.stream()
                .filter(r -> showAll || pending.containsKey(r.key()) || (r.received().signum() > 0
                        && r.received().add(r.feeDiff()).compareTo(r.total()) < 0))
                // 最早一张待确认水单在前，没有待确认水单的排后面
                .sorted(Comparator.<DeskRow, LocalDate>comparing(r -> earliest(pending.get(r.key())),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(DeskRow::createTime, Comparator.reverseOrder()))
                .toList();
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        List<DeskRow> slice = rows.stream().skip((long) (page - 1) * size).limit(size).toList();
        if (slice.isEmpty()) {
            return PageResult.of((long) rows.size(), List.of());
        }
        Map<Long, CustomerDO> customers = lookups.customers(slice.stream().map(DeskRow::customerId).toList());
        Map<Long, String> users = lookups.userNames(slice.stream().map(DeskRow::ownerId).toList());
        List<Long> slicePis = slice.stream().filter(r -> r.pi() != null).map(r -> r.pi().getId()).toList();
        Map<Long, SalesOrderDO> orders = slicePis.isEmpty() ? Map.of() : orderMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                        .in(SalesOrderDO::getPiId, slicePis)
                        .eq(SalesOrderDO::getStatus, SalesConstants.SO_ACTIVE)
                        .isNull(SalesOrderDO::getDeletedAt))
                .stream().collect(Collectors.toMap(SalesOrderDO::getPiId, o -> o, (a, b) -> a));
        List<ReceiptDeskRowVO> list = new ArrayList<>(slice.size());
        for (DeskRow r : slice) {
            ReceiptDeskRowVO vo = new ReceiptDeskRowVO();
            ProformaInvoiceDO p = r.pi();
            vo.setPiId(p == null ? null : p.getId());
            vo.setPiNo(p == null ? null : p.getPiNo());
            vo.setPiStatus(p == null ? null : p.getStatus());
            vo.setCustomerId(r.customerId());
            vo.setCustomerName(InquiryLookups.customerName(customers.get(r.customerId())));
            vo.setOwnerId(r.ownerId());
            vo.setOwnerName(users.get(r.ownerId()));
            vo.setCurrencyCode(p == null ? r.so().getCurrencyCode() : p.getCurrencyCode());
            vo.setTotalAmount(r.total());
            vo.setReceivedAmount(r.received());
            vo.setFeeDiffAmount(r.feeDiff());
            vo.setRemainingAmount(r.total().subtract(r.received()).subtract(r.feeDiff()));
            int status = p == null ? r.so().getReceiptStatus() : p.getReceiptStatus();
            vo.setReceiptStatus(status);
            vo.setReceiptStatusName(SalesConstants.RECEIPT_STATUS_NAMES.get(status));
            SalesOrderDO o = p == null ? r.so() : orders.get(p.getId());
            vo.setOrderId(o == null ? null : o.getId());
            vo.setSoNo(o == null ? null : o.getSoNo());
            List<PaymentReceiptDO> slips = pending.getOrDefault(r.key(), List.of());
            vo.setPendingSlips(slips.stream().map(x -> {
                ReceiptVO sv = new ReceiptVO();
                sv.setId(x.getId());
                sv.setKind(x.getKind());
                sv.setAmount(x.getAmount());
                sv.setAmountCny(x.getAmountCny());
                sv.setPaymentMethod(x.getPaymentMethod());
                sv.setPaymentMethodName(x.getPaymentMethodName());
                sv.setReceiptDate(x.getReceiptDate());
                sv.setFiles(fromJson(x.getFileKeys()));
                sv.setNote(x.getNote());
                sv.setStatus(x.getStatus());
                sv.setMatched(false);
                sv.setCreateTime(x.getCreateTime());
                return sv;
            }).toList());
            vo.setEarliestSlipDate(earliest(slips));
            list.add(vo);
        }
        return PageResult.of((long) rows.size(), list);
    }

    /** 待确认列表的一行：PI，或手动创建的订单 */
    private record DeskRow(ProformaInvoiceDO pi, SalesOrderDO so) {
        static String key(Long piId, Long soId) {
            return piId != null ? "P" + piId : "S" + soId;
        }

        String key() {
            return pi != null ? key(pi.getId(), null) : key(null, so.getId());
        }

        BigDecimal total() {
            return pi != null ? pi.getTotalAmount() : so.getTotalAmount();
        }

        BigDecimal received() {
            return pi != null ? pi.getReceivedAmount() : so.getReceivedAmount();
        }

        BigDecimal feeDiff() {
            return pi != null ? pi.getFeeDiffAmount() : so.getFeeDiffAmount();
        }

        Long customerId() {
            return pi != null ? pi.getCustomerId() : so.getCustomerId();
        }

        Long ownerId() {
            return pi != null ? pi.getOwnerId() : so.getOwnerId();
        }

        LocalDateTime createTime() {
            return pi != null ? pi.getCreateTime() : so.getCreateTime();
        }
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
            throw new BizException(pi.getStatus() == SalesConstants.PI_VOID ? "PI 已作废，不能登记收款"
                    : pi.getStatus() == SalesConstants.PI_CLOSED ? SalesConstants.CLOSED_MESSAGE : "PI 还没有发送，不能登记收款");
        }
        return pi;
    }

    /** 手动创建的订单（加锁）：PI 转成的订单收款登记在 PI 上；receivable 时要求订单有效 */
    private SalesOrderDO requireOrder(Long soId, boolean receivable) {
        visibleOrder(soId);
        SalesOrderDO so = orderMapper.selectOne(new LambdaQueryWrapper<SalesOrderDO>().eq(SalesOrderDO::getId, soId).last("FOR UPDATE"));
        if (!Objects.equals(so.getSource(), SalesConstants.SO_MANUAL)) {
            throw new BizException("这张订单由 PI 转成，收款请在 PI 上登记");
        }
        if (receivable && so.getStatus() != SalesConstants.SO_ACTIVE) {
            throw new BizException("订单已取消，不能登记收款");
        }
        return so;
    }

    private SalesOrderDO visibleOrder(Long soId) {
        SalesOrderDO so = soId == null ? null : orderMapper.selectById(soId);
        if (so == null || so.getDeletedAt() != null || !Objects.equals(so.getTenantId(), PiStore.tenantId())
                || !dataScopeResolver.current().canSee(so.getOwnerId())) {
            throw new BizException("销售订单不存在");
        }
        return so;
    }

    private void refresh(Owner o) {
        if (o.pi() != null) {
            summary.refresh(o.pi());
        } else {
            summary.refreshOrder(o.so());
        }
    }

    /** 收款归属：PI，或手动创建的订单（二者取其一） */
    private record Owner(ProformaInvoiceDO pi, SalesOrderDO so) {
        static Owner of(ProformaInvoiceDO pi) {
            return new Owner(pi, null);
        }

        static Owner of(SalesOrderDO so) {
            return new Owner(null, so);
        }

        Integer tenantId() {
            return pi != null ? pi.getTenantId() : so.getTenantId();
        }

        String currency() {
            return pi != null ? pi.getCurrencyCode() : so.getCurrencyCode();
        }

        BigDecimal rate() {
            return pi != null ? pi.getExchangeRate() : so.getExchangeRate();
        }

        BigDecimal remaining() {
            return pi != null ? pi.getTotalAmount().subtract(pi.getReceivedAmount()).subtract(pi.getFeeDiffAmount())
                    : so.getTotalAmount().subtract(so.getReceivedAmount()).subtract(so.getFeeDiffAmount());
        }

        Integer receiptStatus() {
            return pi != null ? pi.getReceiptStatus() : so.getReceiptStatus();
        }

        String no() {
            return pi != null ? pi.getPiNo() : so.getSoNo();
        }

        String noKey() {
            return pi != null ? "piNo" : "soNo";
        }

        String label() {
            return pi != null ? " PI " : "订单";
        }

        String menu() {
            return pi != null ? SalesConstants.MENU_PI : SalesConstants.MENU_SO;
        }

        void bind(PaymentReceiptDO r) {
            r.setPiId(pi == null ? null : pi.getId());
            r.setSoId(so == null ? null : so.getId());
        }

        boolean owns(PaymentReceiptDO r) {
            return Objects.equals(r.getPiId(), pi == null ? null : pi.getId()) && Objects.equals(r.getSoId(), so == null ? null : so.getId());
        }
    }

    private PaymentReceiptDO record(Owner o, Long id, int kind) {
        PaymentReceiptDO r = id == null ? null : receiptMapper.selectById(id);
        if (r == null || r.getDeletedAt() != null || !o.owns(r) || r.getKind() != kind) {
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
