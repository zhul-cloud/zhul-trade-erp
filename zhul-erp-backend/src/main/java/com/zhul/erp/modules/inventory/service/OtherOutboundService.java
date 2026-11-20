package com.zhul.erp.modules.inventory.service;

import com.zhul.erp.modules.inventory.constants.InvConstants;
import com.zhul.erp.modules.inventory.entity.InvMoveDO;
import com.zhul.erp.modules.inventory.entity.InvOtherOutboundDO;
import com.zhul.erp.modules.inventory.entity.InvOtherOutboundItemDO;
import com.zhul.erp.modules.inventory.repository.InvOtherOutboundItemMapper;
import com.zhul.erp.modules.inventory.repository.InvOtherOutboundMapper;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Doc;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Line;
import com.zhul.erp.modules.inventory.service.InventoryPosting.Loc;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.doclog.DocLogs;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 其他出库单（spec warehouse/other-outbound）：报废、转样品、货代丢失、货损。保存即出库，作废反向。
 */
@Service
@RequiredArgsConstructor
public class OtherOutboundService {

    private final InvOtherOutboundMapper mapper;
    private final InvOtherOutboundItemMapper itemMapper;
    private final InventoryPosting posting;
    private final DocumentNumberService numbers;
    private final DocLogs docLogs;

    /** 出库的一行：某仓库某批次某状态的数量 */
    public record Pick(Long productId, Long batchId, int status, int qty) {
    }

    /** 来源单据（退货去向报废时为退货入库单与去向记录；手工新建为 null） */
    public record Source(String docType, Long docId, String docNo, Long refId) {
    }

    /** 生成一张已出库的其他出库单并过账（调用方事务内）；返回出库单 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public InvOtherOutboundDO create(int type, Long warehouseId, LocalDate date, String reason, List<Pick> picks, Source source) {
        InvOtherOutboundDO o = new InvOtherOutboundDO();
        o.setTenantId(PiStore.tenantId());
        o.setOutboundNo(numbers.next(DocumentType.OO));
        o.setOutboundType(type);
        o.setWarehouseId(warehouseId);
        o.setOutboundDate(date == null ? LocalDate.now() : date);
        o.setReason(reason == null ? "" : reason);
        o.setStatus(InvConstants.OO_DONE);
        o.setCurrencyCode("CNY");
        o.setAmountCny(BigDecimal.ZERO.setScale(2));
        o.setSourceDocType(source == null ? "" : source.docType());
        o.setSourceDocId(source == null ? null : source.docId());
        o.setSourceDocNo(source == null ? "" : source.docNo());
        o.setSourceRefId(source == null ? null : source.refId());
        o.setRemark("");
        o.setVoidReason("");
        mapper.insert(o);
        Doc doc = Doc.of(InvConstants.BIZ_OTHER_OUT, "OO", o.getId(), o.getOutboundNo())
                .label("其他出库 · " + InvConstants.OO_TYPE_NAMES.get(type));
        BigDecimal total = BigDecimal.ZERO;
        for (Pick p : picks) {
            InvOtherOutboundItemDO i = new InvOtherOutboundItemDO();
            i.setTenantId(o.getTenantId());
            i.setOtherOutboundId(o.getId());
            i.setProductId(p.productId());
            i.setBatchId(p.batchId());
            i.setStockStatus(p.status());
            i.setQuantity(p.qty());
            i.setCurrencyCode("CNY");
            itemMapper.insert(i);
            InvMoveDO m = posting.post(List.of(Line.of(new Loc(warehouseId, p.productId(), p.batchId(), p.status()), InvConstants.OP_OUT,
                    p.qty(), null, doc.item(i.getId())))).get(0);
            i.setUnitPriceCny(m.getUnitPriceCny());
            i.setAmountCny(m.getAmountCny().negate());
            itemMapper.updateById(i);
            total = total.add(i.getAmountCny());
        }
        o.setAmountCny(total);
        mapper.updateById(o);
        String text = InvConstants.OO_TYPE_NAMES.get(type) + "：" + picks.stream().mapToInt(Pick::qty).sum() + " 个，¥" + total
                + (o.getReason().isEmpty() ? "" : "，" + o.getReason())
                + (source == null ? "" : "（来源 " + source.docNo() + "）");
        if (source == null) {
            docLogs.add(DocumentType.OO, o.getId(), "新建", text);
        } else {
            docLogs.addBySystem(DocumentType.OO, o.getId(), "生成", text);
        }
        return o;
    }

    /** 某来源单据生成的其他出库单（未删除） */
    public List<InvOtherOutboundDO> bySource(String docType, Long docId) {
        return mapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<InvOtherOutboundDO>()
                .eq(InvOtherOutboundDO::getTenantId, PiStore.tenantId())
                .eq(InvOtherOutboundDO::getSourceDocType, docType)
                .eq(InvOtherOutboundDO::getSourceDocId, docId)
                .isNull(InvOtherOutboundDO::getDeletedAt)
                .orderByAsc(InvOtherOutboundDO::getId))
                .stream().collect(Collectors.toList());
    }
}
