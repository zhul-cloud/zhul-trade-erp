package com.zhul.erp.modules.sales.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.framework.security.PermissionChecker;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.dto.ReceiptVO;
import com.zhul.erp.modules.sales.entity.PaymentReceiptDO;
import com.zhul.erp.modules.sales.repository.PaymentReceiptMapper;
import com.zhul.erp.modules.system.entity.BankAccountDO;
import com.zhul.erp.modules.system.repository.BankAccountMapper;
import com.zhul.erp.modules.system.service.BankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 收款记录（水单与到账）的展示：PI 收款面板与手动订单的收款面板共用 */
@Component
@RequiredArgsConstructor
public class ReceiptViews {

    private final PaymentReceiptMapper receiptMapper;
    private final BankAccountMapper bankAccountMapper;
    private final InquiryLookups lookups;
    private final PermissionChecker perm;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;

    /** owner：归属条件（PI 或订单） */
    public List<ReceiptVO> list(LambdaQueryWrapper<PaymentReceiptDO> owner) {
        List<PaymentReceiptDO> rows = receiptMapper.selectList(owner
                .isNull(PaymentReceiptDO::getDeletedAt)
                .orderByAsc(PaymentReceiptDO::getId));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<Long, String> names = lookups.userNames(rows.stream().flatMap(r -> java.util.stream.Stream.of(r.getOperatorId(), r.getClaimedBy()))
                .filter(Objects::nonNull).distinct().toList());
        boolean canConfirm = perm.has(SalesConstants.PERM_RECEIPT_CONFIRM);
        Long me = currentUser.resolve();
        Set<Integer> bankIds = rows.stream().map(PaymentReceiptDO::getBankAccountId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, BankAccountDO> banks = bankIds.isEmpty() ? Map.of()
                : bankAccountMapper.selectBatchIds(bankIds).stream().collect(Collectors.toMap(BankAccountDO::getId, b -> b));
        Set<Long> matchedSlips = rows.stream().filter(r -> r.getKind() == SalesConstants.KIND_RECEIPT && r.getStatus() == SalesConstants.RECORD_VALID
                && r.getSlipId() != null).map(PaymentReceiptDO::getSlipId).collect(Collectors.toSet());
        return rows.stream().map(r -> {
            ReceiptVO vo = new ReceiptVO();
            vo.setId(r.getId());
            vo.setKind(r.getKind());
            vo.setAmount(r.getAmount());
            vo.setAmountCny(r.getAmountCny());
            vo.setFeeDiff(r.getFeeDiff());
            vo.setPaymentMethod(r.getPaymentMethod());
            vo.setPaymentMethodName(r.getPaymentMethodName());
            vo.setChannel(r.getChannel());
            vo.setPlatformOrderNo(r.getPlatformOrderNo());
            vo.setPlatformFee(r.getPlatformFee());
            vo.setNetAmount(r.getNetAmount());
            vo.setNetAmountCny(r.getNetAmountCny());
            vo.setExchangeRate(r.getExchangeRate());
            vo.setRateSource(r.getRateSource());
            vo.setPayer(r.getPayer());
            vo.setClaimedByName(r.getClaimedBy() == null ? null : names.get(r.getClaimedBy()));
            vo.setClaimedAt(r.getClaimedAt());
            vo.setVoidable(r.getKind() == SalesConstants.KIND_RECEIPT && r.getStatus() == SalesConstants.RECORD_VALID
                    && (canConfirm || (StringUtils.hasText(r.getPlatformOrderNo()) && Objects.equals(r.getOperatorId(), me))));
            vo.setReceiptDate(r.getReceiptDate());
            vo.setBankAccountId(r.getBankAccountId());
            BankAccountDO b = r.getBankAccountId() == null ? null : banks.get(r.getBankAccountId());
            vo.setBankAccountName(b == null ? null : b.getBankName() + " " + BankAccountService.mask(b.getAccountNo()));
            vo.setSlipId(r.getSlipId());
            vo.setFiles(slipFiles(r.getFileKeys()));
            vo.setNote(r.getNote());
            vo.setStatus(r.getStatus());
            vo.setVoidReason(r.getVoidReason());
            vo.setOperatorName(names.get(r.getOperatorId()));
            vo.setMatched(r.getKind() == SalesConstants.KIND_SLIP && matchedSlips.contains(r.getId()));
            vo.setCreateTime(r.getCreateTime());
            return vo;
        }).toList();
    }

    private List<ReceiptVO.SlipFile> slipFiles(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<ReceiptVO.SlipFile>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("水单附件解析失败", e);
        }
    }
}
