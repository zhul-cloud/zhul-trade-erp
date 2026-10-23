package com.zhul.erp.modules.sales.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.finance.entity.FxSettlementAllocationDO;
import com.zhul.erp.modules.finance.entity.FxSettlementDO;
import com.zhul.erp.modules.finance.repository.FxSettlementAllocationMapper;
import com.zhul.erp.modules.finance.repository.FxSettlementMapper;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.sales.dto.ReceiptRowVO;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.system.entity.BankAccountDO;
import com.zhul.erp.modules.system.repository.BankAccountMapper;
import com.zhul.erp.modules.system.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 把收款记录转成收款单的行：补上 PI 编号、客户、收款账户与登记人、认领人名称 */
@Component
@RequiredArgsConstructor
public class ReceiptRows {

    private final ProformaInvoiceMapper piMapper;
    private final com.zhul.erp.modules.sales.repository.SalesOrderMapper orderMapper;
    private final BankAccountMapper bankAccountMapper;
    private final InquiryLookups lookups;
    private final FxSettlementAllocationMapper allocationMapper;
    private final FxSettlementMapper settlementMapper;

    public List<ReceiptRowVO> of(List<PaymentReceiptDO> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Set<Long> piIds = rows.stream().map(PaymentReceiptDO::getPiId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ProformaInvoiceDO> pis = piIds.isEmpty() ? Map.of()
                : piMapper.selectBatchIds(piIds).stream().collect(Collectors.toMap(ProformaInvoiceDO::getId, p -> p));
        Set<Long> soIds = rows.stream().map(PaymentReceiptDO::getSoId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, SalesOrderDO> orders = soIds.isEmpty() ? Map.of()
                : orderMapper.selectBatchIds(soIds).stream().collect(Collectors.toMap(SalesOrderDO::getId, o -> o));
        Map<Long, CustomerDO> customers = lookups.customers(Stream.concat(pis.values().stream().map(ProformaInvoiceDO::getCustomerId),
                orders.values().stream().map(SalesOrderDO::getCustomerId)).toList());
        Set<Integer> bankIds = rows.stream().map(PaymentReceiptDO::getBankAccountId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, BankAccountDO> banks = bankIds.isEmpty() ? Map.of()
                : bankAccountMapper.selectBatchIds(bankIds).stream().collect(Collectors.toMap(BankAccountDO::getId, b -> b));
        Map<Long, String> users = lookups.userNames(rows.stream()
                .flatMap(r -> Stream.of(r.getOperatorId(), r.getClaimedBy())).filter(Objects::nonNull).distinct().toList());
        Map<Long, List<String>> settleNos = settleNos(rows.stream().map(PaymentReceiptDO::getId).toList());
        List<ReceiptRowVO> list = new ArrayList<>(rows.size());
        for (PaymentReceiptDO r : rows) {
            ReceiptRowVO vo = new ReceiptRowVO();
            vo.setId(r.getId());
            vo.setPiId(r.getPiId());
            ProformaInvoiceDO pi = r.getPiId() == null ? null : pis.get(r.getPiId());
            vo.setPiNo(pi == null ? null : pi.getPiNo());
            SalesOrderDO so = r.getSoId() == null ? null : orders.get(r.getSoId());
            vo.setSoId(r.getSoId());
            vo.setSoNo(so == null ? null : so.getSoNo());
            Long customerId = pi != null ? pi.getCustomerId() : so != null ? so.getCustomerId() : null;
            vo.setCustomerName(customerId == null ? null : InquiryLookups.customerName(customers.get(customerId)));
            vo.setCurrencyCode(r.getCurrencyCode());
            vo.setAmount(r.getAmount());
            vo.setPlatformFee(r.getPlatformFee());
            vo.setFeeDiff(r.getFeeDiff());
            vo.setNetAmount(r.getNetAmount());
            vo.setExchangeRate(r.getExchangeRate());
            vo.setRateSource(r.getRateSource());
            vo.setNetAmountCny(r.getNetAmountCny());
            vo.setSettledAmount(r.getSettledAmount());
            vo.setSettleStatus(r.getSettleStatus());
            vo.setSettleNos(settleNos.getOrDefault(r.getId(), List.of()));
            vo.setPaymentMethod(r.getPaymentMethod());
            vo.setPaymentMethodName(r.getPaymentMethodName());
            vo.setChannel(r.getChannel());
            vo.setPlatformOrderNo(r.getPlatformOrderNo());
            vo.setPayer(r.getPayer());
            vo.setReceiptDate(r.getReceiptDate());
            BankAccountDO b = r.getBankAccountId() == null ? null : banks.get(r.getBankAccountId());
            vo.setBankAccountName(b == null ? null : b.getBankName() + " " + BankAccountService.mask(b.getAccountNo()));
            vo.setBusinessUnitId(r.getBusinessUnitId());
            vo.setBuMismatch(Integer.valueOf(1).equals(r.getBuMismatch()));
            vo.setNote(r.getNote());
            vo.setStatus(r.getStatus());
            vo.setVoidReason(r.getVoidReason());
            vo.setOperatorName(users.get(r.getOperatorId()));
            vo.setClaimedByName(r.getClaimedBy() == null ? null : users.get(r.getClaimedBy()));
            vo.setClaimedAt(r.getClaimedAt());
            vo.setCreateTime(r.getCreateTime());
            list.add(vo);
        }
        return list;
    }

    /** 到账 → 有效结汇单编号（按结汇先后） */
    private Map<Long, List<String>> settleNos(List<Long> receiptIds) {
        List<FxSettlementAllocationDO> allocs = allocationMapper.selectList(new LambdaQueryWrapper<FxSettlementAllocationDO>()
                .in(FxSettlementAllocationDO::getReceiptId, receiptIds)
                .isNull(FxSettlementAllocationDO::getDeletedAt)
                .orderByAsc(FxSettlementAllocationDO::getId));
        if (allocs.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> nos = settlementMapper.selectBatchIds(allocs.stream().map(FxSettlementAllocationDO::getSettlementId).distinct().toList())
                .stream().collect(Collectors.toMap(FxSettlementDO::getId, FxSettlementDO::getSettleNo));
        Map<Long, List<String>> out = new java.util.HashMap<>();
        for (FxSettlementAllocationDO a : allocs) {
            out.computeIfAbsent(a.getReceiptId(), k -> new ArrayList<>()).add(nos.get(a.getSettlementId()));
        }
        return out;
    }
}
