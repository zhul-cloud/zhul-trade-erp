package com.zhul.erp.modules.quotation.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.modules.sales.constants.SalesConstants;
import com.zhul.erp.modules.sales.entity.PiItemDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.repository.PiItemMapper;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/** 由报价单开出的 PI：报价单已开出未作废的 PI 时不能出新版本（在 PI 上修改） */
@Component
@RequiredArgsConstructor
public class QuotationPis {

    private final PiItemMapper piItemMapper;
    private final ProformaInvoiceMapper piMapper;

    public record Brief(Long id, String piNo) {
    }

    /** 引用该报价单、未作废也未关闭的 PI（取编号最早的一张），没有时为空 */
    public Brief activeOf(Long quotationId) {
        Set<Long> piIds = piItemMapper.selectList(new LambdaQueryWrapper<PiItemDO>()
                        .select(PiItemDO::getPiId)
                        .eq(PiItemDO::getQuotationId, quotationId)
                        .isNull(PiItemDO::getDeletedAt))
                .stream().map(PiItemDO::getPiId).collect(Collectors.toSet());
        if (piIds.isEmpty()) {
            return null;
        }
        ProformaInvoiceDO pi = piMapper.selectOne(new LambdaQueryWrapper<ProformaInvoiceDO>()
                .select(ProformaInvoiceDO::getId, ProformaInvoiceDO::getPiNo)
                .in(ProformaInvoiceDO::getId, piIds)
                .notIn(ProformaInvoiceDO::getStatus, SalesConstants.PI_VOID, SalesConstants.PI_CLOSED)
                .isNull(ProformaInvoiceDO::getDeletedAt)
                .orderByAsc(ProformaInvoiceDO::getId)
                .last("LIMIT 1"));
        return pi == null ? null : new Brief(pi.getId(), pi.getPiNo());
    }
}
