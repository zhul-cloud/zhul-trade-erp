package com.zhul.erp.modules.logistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.document.constants.DocTypes;
import com.zhul.erp.modules.document.dto.TemplateFile;
import com.zhul.erp.modules.document.service.DocumentTemplateService;
import com.zhul.erp.modules.document.support.DocumentConverter;
import com.zhul.erp.modules.document.support.RenderModel;
import com.zhul.erp.modules.document.support.XlsxRenderer;
import com.zhul.erp.modules.logistics.constants.LogisticsConstants;
import com.zhul.erp.modules.logistics.dto.GenerateDocsRequest;
import com.zhul.erp.modules.logistics.dto.LogisticsVO;
import com.zhul.erp.modules.logistics.entity.LogisticsShipmentDO;
import com.zhul.erp.modules.logistics.entity.OutboundBoxDO;
import com.zhul.erp.modules.logistics.entity.OutboundBoxItemDO;
import com.zhul.erp.modules.logistics.entity.OutboundOrderDO;
import com.zhul.erp.modules.logistics.entity.OutboundOrderItemDO;
import com.zhul.erp.modules.logistics.entity.ShipmentDocGroupDO;
import com.zhul.erp.modules.logistics.repository.OutboundBoxItemMapper;
import com.zhul.erp.modules.logistics.repository.OutboundBoxMapper;
import com.zhul.erp.modules.logistics.repository.ShipmentDocGroupMapper;
import com.zhul.erp.modules.logistics.support.LogisticsSupport;
import com.zhul.erp.modules.quotation.support.QuotationRenderModels;
import com.zhul.erp.modules.sales.dto.PartyDTO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.entity.SalesOrderFeeDO;
import com.zhul.erp.modules.sales.entity.SalesOrderItemDO;
import com.zhul.erp.modules.sales.repository.ProformaInvoiceMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderFeeMapper;
import com.zhul.erp.modules.sales.repository.SalesOrderItemMapper;
import com.zhul.erp.modules.sales.support.PiRenderModels;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.constants.DocumentType;
import com.zhul.erp.modules.system.service.DocumentNumberService;
import com.zhul.erp.modules.system.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 出运单的单证组：一张 CI + 一张 PL，共用编号主体；可以合成一组或每张订单各一组。
 * CI 取订单的型号、英文描述、HS 编码、原产地、单价，数量为本出运单上的数量；订单的费用行只出现在这张订单第一次出运的单证组里。
 * PL 取装箱记录：每箱一组，箱字段写在箱内第一行并合并单元格。金额两位小数 HALF_UP；体积（立方米）三位小数。
 */
@Service
@RequiredArgsConstructor
public class DocGroupService {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String MERGED = "MERGED";
    private static final String PER_ORDER = "PER_ORDER";
    private static final BigDecimal CM3_PER_M3 = BigDecimal.valueOf(1_000_000);

    private final ShipmentDocGroupMapper groupMapper;
    private final OutboundBoxMapper boxMapper;
    private final OutboundBoxItemMapper boxItemMapper;
    private final SalesOrderItemMapper soItemMapper;
    private final SalesOrderFeeMapper feeMapper;
    private final ProformaInvoiceMapper piMapper;
    private final LogisticsShipmentService shipments;
    private final OutboundService outbounds;
    private final LogisticsSupport support;
    private final DocumentTemplateService templateService;
    private final DocumentConverter converter;
    private final DocumentNumberService documentNumberService;
    private final PiStore piStore;
    private final LogService logService;
    private final com.zhul.erp.modules.product.support.BrandResolver brandResolver;

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public LogisticsVO generate(Long logisticsId, GenerateDocsRequest req) {
        LogisticsShipmentDO sh = shipments.visible(logisticsId);
        if (sh.getStatus() == LogisticsConstants.SH_VOID) {
            throw new BizException("出运单已作废");
        }
        String mode = req.getMode().trim().toUpperCase(Locale.ROOT);
        if (!MERGED.equals(mode) && !PER_ORDER.equals(mode)) {
            throw new BizException("请选择合成一组或每张订单各一组");
        }
        List<OutboundOrderDO> obs = shipments.outboundsOf(logisticsId);
        if (obs.isEmpty()) {
            throw new BizException("出运单里还没有出库单；直发货请先确认实收");
        }
        List<Long> soIds = obs.stream().map(OutboundOrderDO::getSoId).distinct().sorted().toList();
        Map<Long, SalesOrderDO> orders = support.orders(soIds);
        if (MERGED.equals(mode) && orders.values().stream().map(SalesOrderDO::getCurrencyCode).distinct().count() > 1) {
            throw new BizException("几张订单的币种不同，不能合成一组，请选「每张订单各一组」");
        }
        List<List<Long>> wanted = MERGED.equals(mode) ? List.of(soIds) : soIds.stream().map(List::of).toList();
        List<ShipmentDocGroupDO> old = groupMapper.selectList(new LambdaQueryWrapper<ShipmentDocGroupDO>()
                .eq(ShipmentDocGroupDO::getLogisticsId, logisticsId)
                .eq(ShipmentDocGroupDO::getStatus, LogisticsConstants.DOC_VALID)
                .isNull(ShipmentDocGroupDO::getDeletedAt));
        Map<String, ShipmentDocGroupDO> byKey = old.stream().collect(Collectors.toMap(ShipmentDocGroupDO::getSoIds, g -> g, (a, b) -> a));
        String paymentRef = req.getPaymentRef() == null ? "" : req.getPaymentRef().trim();
        List<String> kept = new ArrayList<>();
        for (List<Long> ids : wanted) {
            String key = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
            ShipmentDocGroupDO g = byKey.remove(key);
            if (g == null) {
                g = new ShipmentDocGroupDO();
                g.setTenantId(PiStore.tenantId());
                g.setLogisticsId(logisticsId);
                g.setSoIds(key);
                g.setCiNo(documentNumberService.next(DocumentType.CI));
                g.setPlNo(plNoOf(g.getCiNo()));
                g.setStatus(LogisticsConstants.DOC_VALID);
                g.setPaymentRef(paymentRef);
                groupMapper.insert(g);
            } else {
                g.setPaymentRef(paymentRef);
                groupMapper.updateById(g);
            }
            kept.add(g.getCiNo());
        }
        byKey.values().forEach(g -> {
            g.setStatus(LogisticsConstants.DOC_VOID);
            groupMapper.updateById(g);
        });
        logService.recordOperateLog(LogisticsConstants.MENU_SHIPMENT, "生成 CI/PL", null, Map.of("shNo", sh.getShNo(), "ci", kept));
        return shipments.detail(logisticsId);
    }

    /** PL 编号与 CI 共用「年月日 + 流水」主体，只换类型代码 */
    private String plNoOf(String ciNo) {
        String prefix = documentNumberService.prefix();
        String head = prefix + DocumentType.CI.name();
        return ciNo.startsWith(head) ? prefix + DocumentType.PL.name() + ciNo.substring(head.length())
                : ciNo.replaceFirst(DocumentType.CI.name(), DocumentType.PL.name());
    }

    public TemplateFile export(Long groupId, String kind, String format) {
        ShipmentDocGroupDO g = groupMapper.selectById(groupId);
        if (g == null || g.getDeletedAt() != null || !Objects.equals(g.getTenantId(), PiStore.tenantId())
                || g.getStatus() != LogisticsConstants.DOC_VALID) {
            throw new BizException("单证组不存在");
        }
        LogisticsShipmentDO sh = shipments.visible(g.getLogisticsId());
        String f = format == null ? "xlsx" : format.trim().toLowerCase(Locale.ROOT);
        if (!List.of("xlsx", "pdf").contains(f)) {
            throw new BizException("导出格式只支持 Excel、PDF");
        }
        boolean ci = "ci".equalsIgnoreCase(kind);
        int docType = ci ? DocTypes.CI : DocTypes.PL;
        RenderModel model = ci ? ciModel(sh, g) : plModel(sh, g);
        byte[] xlsx = XlsxRenderer.render(templateService.resolveDefault(docType).file(), docType, model);
        String customer = support.customerNames(List.of(sh.getCustomerId())).getOrDefault(sh.getCustomerId(), "");
        String clean = customer == null ? "" : customer.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", " ").trim();
        String base = (ci ? g.getCiNo() : g.getPlNo()) + (clean.isEmpty() ? "" : "_" + (clean.length() > 60 ? clean.substring(0, 60).trim() : clean));
        return "pdf".equals(f) ? new TemplateFile(base + ".pdf", "application/pdf", converter.toPdf(xlsx))
                : new TemplateFile(base + ".xlsx", XLSX, xlsx);
    }

    // ---------------------------------------------------------------- CI

    private RenderModel ciModel(LogisticsShipmentDO sh, ShipmentDocGroupDO g) {
        List<Long> soIds = LogisticsShipmentService.parseIds(g.getSoIds());
        Map<Long, SalesOrderDO> orders = support.orders(soIds);
        List<OutboundOrderDO> obs = shipments.outboundsOf(sh.getId()).stream().filter(o -> soIds.contains(o.getSoId())).toList();
        Map<Long, Integer> qty = new LinkedHashMap<>();
        outbounds.itemsOf(obs.stream().map(OutboundOrderDO::getId).toList()).values().stream().flatMap(List::stream)
                .forEach(i -> qty.merge(i.getSoItemId(), i.getQuantity(), Integer::sum));
        Map<Long, SalesOrderItemDO> soItems = qty.isEmpty() ? Map.of()
                : soItemMapper.selectBatchIds(qty.keySet()).stream().collect(Collectors.toMap(SalesOrderItemDO::getId, i -> i));
        SalesOrderDO first = orders.get(soIds.get(0));
        Map<String, Object> h = header(sh, orders, soIds, first);
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal itemTotal = BigDecimal.ZERO;
        int no = 1;
        List<Map.Entry<Long, Integer>> lines = qty.entrySet().stream()
                .sorted((a, b) -> compareLine(soItems.get(a.getKey()), soItems.get(b.getKey()))).toList();
        for (Map.Entry<Long, Integer> e : lines) {
            SalesOrderItemDO i = soItems.get(e.getKey());
            BigDecimal price = i.getUnitPrice() == null ? BigDecimal.ZERO : i.getUnitPrice();
            BigDecimal amount = price.multiply(BigDecimal.valueOf(e.getValue())).setScale(2, RoundingMode.HALF_UP);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("item.no", no++);
            m.put("item.model", nz(i.getModel()));
            m.put("item.brand", nz(i.getBrand()));
            m.put("item.description", QuotationRenderModels.customerDescription(i.getDescriptionEn(), i.getDescription()));
            m.put("item.hsCode", nz(i.getHsCode()));
            m.put("item.origin", nz(i.getOriginCountry()));
            m.put("item.qty", e.getValue());
            m.put("item.unitPrice", price);
            m.put("item.amount", amount);
            items.add(m);
            itemTotal = itemTotal.add(amount);
        }
        List<Map<String, Object>> fees = new ArrayList<>();
        BigDecimal feeTotal = BigDecimal.ZERO;
        Set<Long> billedElsewhere = feesBilledOnOtherShipments(sh.getId(), soIds);
        for (Long soId : soIds) {
            if (billedElsewhere.contains(soId)) {
                continue;
            }
            for (SalesOrderFeeDO f : feeMapper.selectList(new LambdaQueryWrapper<SalesOrderFeeDO>()
                    .eq(SalesOrderFeeDO::getSoId, soId).isNull(SalesOrderFeeDO::getDeletedAt).orderByAsc(SalesOrderFeeDO::getSortOrder))) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("fee.name", nz(f.getFeeName()));
                m.put("fee.amount", f.getAmount());
                fees.add(m);
                feeTotal = feeTotal.add(f.getAmount() == null ? BigDecimal.ZERO : f.getAmount());
            }
        }
        h.put("ci.no", g.getCiNo());
        h.put("ci.date", date(sh));
        h.put("ci.currency", first.getCurrencyCode());
        h.put("ci.currencySymbol", QuotationRenderModels.symbol(first.getCurrencyCode()));
        h.put("ci.paymentRef", nz(g.getPaymentRef()));
        h.put("ci.itemTotal", itemTotal);
        h.put("ci.feeTotal", feeTotal);
        h.put("ci.total", itemTotal.add(feeTotal));
        return new RenderModel(h, items, fees).withBrands(brandResolver::displayNames);
    }

    /** 这几张订单里，已经在别的（有效、未作废）出运单的单证组上出过费用行的 */
    private Set<Long> feesBilledOnOtherShipments(Long logisticsId, List<Long> soIds) {
        Set<Long> out = new java.util.HashSet<>();
        groupMapper.selectList(new LambdaQueryWrapper<ShipmentDocGroupDO>()
                        .ne(ShipmentDocGroupDO::getLogisticsId, logisticsId)
                        .eq(ShipmentDocGroupDO::getStatus, LogisticsConstants.DOC_VALID)
                        .eq(ShipmentDocGroupDO::getTenantId, PiStore.tenantId())
                        .isNull(ShipmentDocGroupDO::getDeletedAt)
                        .lt(ShipmentDocGroupDO::getLogisticsId, logisticsId))
                .forEach(g -> LogisticsShipmentService.parseIds(g.getSoIds()).stream().filter(soIds::contains).forEach(out::add));
        return out;
    }

    // ---------------------------------------------------------------- PL

    private RenderModel plModel(LogisticsShipmentDO sh, ShipmentDocGroupDO g) {
        List<Long> soIds = LogisticsShipmentService.parseIds(g.getSoIds());
        Map<Long, SalesOrderDO> orders = support.orders(soIds);
        List<OutboundOrderDO> obs = shipments.outboundsOf(sh.getId()).stream().filter(o -> soIds.contains(o.getSoId())).toList();
        Map<Long, OutboundOrderItemDO> obItems = new HashMap<>();
        outbounds.itemsOf(obs.stream().map(OutboundOrderDO::getId).toList()).values().stream().flatMap(List::stream)
                .forEach(i -> obItems.put(i.getId(), i));
        Map<Long, SalesOrderItemDO> soItems = obItems.isEmpty() ? Map.of()
                : soItemMapper.selectBatchIds(obItems.values().stream().map(OutboundOrderItemDO::getSoItemId).distinct().toList())
                .stream().collect(Collectors.toMap(SalesOrderItemDO::getId, i -> i));
        List<OutboundBoxDO> boxes = obs.isEmpty() ? List.of() : boxMapper.selectList(new LambdaQueryWrapper<OutboundBoxDO>()
                .in(OutboundBoxDO::getOutboundId, obs.stream().map(OutboundOrderDO::getId).toList())
                .isNull(OutboundBoxDO::getDeletedAt)
                .orderByAsc(OutboundBoxDO::getOutboundId, OutboundBoxDO::getBoxNo));
        Map<Long, List<OutboundBoxItemDO>> contents = boxes.isEmpty() ? Map.of()
                : boxItemMapper.selectList(new LambdaQueryWrapper<OutboundBoxItemDO>()
                        .in(OutboundBoxItemDO::getBoxId, boxes.stream().map(OutboundBoxDO::getId).toList())
                        .isNull(OutboundBoxItemDO::getDeletedAt)
                        .orderByAsc(OutboundBoxItemDO::getId))
                .stream().collect(Collectors.groupingBy(OutboundBoxItemDO::getBoxId));
        SalesOrderDO first = orders.get(soIds.get(0));
        Map<String, Object> h = header(sh, orders, soIds, first);
        List<Map<String, Object>> items = new ArrayList<>();
        int row = 1;
        int boxNo = 0;
        int totalQty = 0;
        BigDecimal net = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal volume = BigDecimal.ZERO;
        for (OutboundBoxDO b : boxes) {
            boxNo++;
            BigDecimal nw = b.getNetWeight() == null ? b.getGrossWeight() : b.getNetWeight();
            BigDecimal vol = BigDecimal.valueOf((long) b.getLength() * b.getWidth() * b.getHeight()).divide(CM3_PER_M3, 3, RoundingMode.HALF_UP);
            net = net.add(nw);
            gross = gross.add(b.getGrossWeight());
            volume = volume.add(vol);
            boolean firstRow = true;
            for (OutboundBoxItemDO c : contents.getOrDefault(b.getId(), List.of())) {
                OutboundOrderItemDO oi = obItems.get(c.getOutboundItemId());
                SalesOrderItemDO si = oi == null ? null : soItems.get(oi.getSoItemId());
                Map<String, Object> m = new LinkedHashMap<>();
                m.put(RenderModel.BOX_GROUP, boxNo);
                m.put("item.no", row++);
                m.put("item.model", oi == null ? "" : nz(oi.getModel()));
                m.put("item.brand", oi == null ? "" : nz(oi.getBrand()));
                m.put("item.description", si == null ? "" : QuotationRenderModels.customerDescription(si.getDescriptionEn(), si.getDescription()));
                m.put("item.qty", c.getQuantity());
                m.put("box.no", firstRow ? boxNo : "");
                m.put("box.netWeight", firstRow ? nw : "");
                m.put("box.grossWeight", firstRow ? b.getGrossWeight() : "");
                m.put("box.dimensions", firstRow ? b.getLength() + "x" + b.getWidth() + "x" + b.getHeight() : "");
                m.put("box.volume", firstRow ? vol : "");
                items.add(m);
                totalQty += c.getQuantity();
                firstRow = false;
            }
        }
        h.put("pl.no", g.getPlNo());
        h.put("pl.date", date(sh));
        h.put("pl.priceTerm", join(first.getIncoterm(), first.getIncotermPlace()));
        h.put("pl.totalBoxes", boxNo);
        h.put("pl.totalQty", totalQty);
        h.put("pl.totalNetWeight", net);
        h.put("pl.totalGrossWeight", gross);
        h.put("pl.totalVolume", volume);
        h.put("ci.no", g.getCiNo());
        return new RenderModel(h, items, List.of()).withBrands(brandResolver::displayNames);
    }

    // ---------------------------------------------------------------- 通用

    private Map<String, Object> header(LogisticsShipmentDO sh, Map<Long, SalesOrderDO> orders, List<Long> soIds, SalesOrderDO first) {
        Map<String, Object> h = new LinkedHashMap<>();
        PiRenderModels.party(h, "buyer", piStore.fromJson(first.getBuyerJson(), PartyDTO.class));
        PiRenderModels.party(h, "consignee", piStore.fromJson(first.getConsigneeJson(), PartyDTO.class));
        h.put("customer.name", nz(support.customerNames(List.of(sh.getCustomerId())).get(sh.getCustomerId())));
        h.put("seller.name", "");
        h.put("seller.email", "");
        h.put("seller.phone", "");
        List<Long> piIds = soIds.stream().map(orders::get).filter(Objects::nonNull).map(SalesOrderDO::getPiId)
                .filter(x -> x != null && x > 0).distinct().toList();
        h.put("pi.no", piIds.isEmpty() ? "" : piMapper.selectBatchIds(piIds).stream().map(ProformaInvoiceDO::getPiNo).collect(Collectors.joining(", ")));
        h.put("so.no", soIds.stream().map(orders::get).filter(Objects::nonNull).map(SalesOrderDO::getSoNo).collect(Collectors.joining(", ")));
        h.put("shipment.no", sh.getShNo());
        h.put("shipment.waybill", (nz(sh.getCarrier()) + " " + nz(sh.getWaybillNo())).trim());
        return h;
    }

    private static String date(LogisticsShipmentDO sh) {
        return (sh.getShippedDate() != null ? sh.getShippedDate() : LocalDate.now()).toString();
    }

    private static int compareLine(SalesOrderItemDO a, SalesOrderItemDO b) {
        int c = Long.compare(a.getSoId(), b.getSoId());
        return c != 0 ? c : Integer.compare(a.getLineNo() == null ? 0 : a.getLineNo(), b.getLineNo() == null ? 0 : b.getLineNo());
    }

    private static String join(String a, String b) {
        return (StringUtils.hasText(a) ? a.trim() : "") + (StringUtils.hasText(b) ? " " + b.trim() : "");
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
