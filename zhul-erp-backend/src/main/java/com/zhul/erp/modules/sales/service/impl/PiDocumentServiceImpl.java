package com.zhul.erp.modules.sales.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.document.constants.DocTypes;
import com.zhul.erp.modules.document.dto.TemplateFile;
import com.zhul.erp.modules.document.service.DocumentTemplateService;
import com.zhul.erp.modules.document.support.DocumentConverter;
import com.zhul.erp.modules.document.support.RenderModel;
import com.zhul.erp.modules.document.support.XlsxRenderer;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.quotation.dto.PreviewVO;
import com.zhul.erp.modules.quotation.support.QuotationStore;
import com.zhul.erp.modules.sales.dto.BankSnapshotDTO;
import com.zhul.erp.modules.sales.dto.PartyDTO;
import com.zhul.erp.modules.sales.dto.SavePiRequest;
import com.zhul.erp.modules.sales.entity.PiFeeDO;
import com.zhul.erp.modules.sales.entity.PiItemDO;
import com.zhul.erp.modules.sales.entity.PiVersionDO;
import com.zhul.erp.modules.sales.entity.ProformaInvoiceDO;
import com.zhul.erp.modules.sales.service.PiDocumentService;
import com.zhul.erp.modules.sales.support.PiEditor;
import com.zhul.erp.modules.sales.support.PiRenderModels;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import com.zhul.erp.modules.sales.support.PiBargainSheet;
import com.zhul.erp.modules.sales.support.PiCalculator;
import java.math.BigDecimal;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class PiDocumentServiceImpl implements PiDocumentService {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int IMAGE_MAX_WIDTH = 1600;
    private static final String PREVIEW_UNAVAILABLE = "预览暂时不可用，不影响编辑与导出 Excel";

    private final PiStore store;
    private final PiEditor editor;
    private final QuotationStore quotationStore;
    private final CustomerMapper customerMapper;
    private final UserBasicMapper userBasicMapper;
    private final DocumentTemplateService templateService;
    private final DocumentConverter converter;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;
    private final com.zhul.erp.modules.product.support.BrandResolver brandResolver;

    /** 每个用户最新一次预览请求的序号：排队中的旧请求在拿到转换机会时直接放弃 */
    private final Map<Long, AtomicLong> previewSeq = new ConcurrentHashMap<>();
    private final AtomicLong seqSource = new AtomicLong();

    @Override
    public TemplateFile export(Long id, Integer versionNo, String format) {
        ProformaInvoiceDO pi = store.visible(id);
        String f = format == null ? "xlsx" : format.trim().toLowerCase(Locale.ROOT);
        if (!List.of("xlsx", "pdf", "jpg").contains(f)) {
            throw new BizException("导出格式只支持 Excel、PDF、图片");
        }
        Integer target = versionNo != null ? versionNo
                : pi.getEditingVersionNo() != null ? pi.getEditingVersionNo() : pi.getCurrentVersionNo();
        PiVersionDO v = store.version(id, target);
        if (v == null) {
            throw new BizException("PI 版本不存在");
        }
        List<PiItemDO> items = store.items(v.getId());
        if (items.isEmpty()) {
            throw new BizException("PI 没有型号，不能导出");
        }
        DocumentTemplateService.Resolved tpl = templateService.resolveDefault(DocTypes.PI);
        byte[] xlsx = XlsxRenderer.render(tpl.file(), DocTypes.PI, model(pi, v, items, store.fees(v.getId())));
        String base = fileBase(pi);
        return switch (f) {
            case "pdf" -> new TemplateFile(base + ".pdf", "application/pdf", converter.toPdf(xlsx));
            case "jpg" -> new TemplateFile(base + ".jpg", "image/jpeg", converter.toJpeg(converter.toPdf(xlsx), IMAGE_MAX_WIDTH));
            default -> new TemplateFile(base + ".xlsx", XLSX, xlsx);
        };
    }

    @Override
    public TemplateFile bargainExport(Long id, Integer versionNo, BigDecimal rate, Integer discountType, BigDecimal discountValue) {
        ProformaInvoiceDO pi = store.visible(id);
        Integer target = versionNo != null ? versionNo
                : pi.getEditingVersionNo() != null ? pi.getEditingVersionNo() : pi.getCurrentVersionNo();
        PiVersionDO v = store.version(id, target);
        if (v == null) {
            throw new BizException("PI 版本不存在");
        }
        BigDecimal r = rate == null ? pi.getExchangeRate() : rate;
        if (r == null || r.signum() <= 0) {
            throw new BizException("测算汇率需要大于 0");
        }
        List<PiItemDO> items = store.items(v.getId());
        BigDecimal itemAmount = items.stream().map(PiItemDO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal discount = discountType == null ? v.getDiscountAmount() : PiCalculator.discount(discountType, discountValue, itemAmount);
        byte[] xlsx = PiBargainSheet.build(items, pi.getCurrencyCode(), r, discount == null ? BigDecimal.ZERO : discount);
        CustomerDO c = customerMapper.selectById(pi.getCustomerId());
        String display = InquiryLookups.customerName(c);
        String name = display == null ? "" : display.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", " ").trim();
        String base = (name.isEmpty() ? "" : name + "_") + pi.getPiNo() + "_" + LocalDate.now();
        return new TemplateFile(base + ".xlsx", XLSX, xlsx);
    }

    @Override
    public PreviewVO preview(Long id, SavePiRequest content) {
        ProformaInvoiceDO pi = store.visible(id);
        PreviewVO vo = new PreviewVO();
        if (!converter.available()) {
            vo.setUnavailable(true);
            vo.setMessage(PREVIEW_UNAVAILABLE);
            return vo;
        }
        PiVersionDO v = store.requireEditing(pi);
        // 实体是刚读出的副本：套用编辑内容后只用于渲染，不落库
        PiEditor.Applied applied = editor.apply(pi, v, store.items(v.getId()), content, false);
        RenderModel model = model(pi, v, applied.items(), applied.fees());
        DocumentTemplateService.Resolved tpl = templateService.resolveDefault(DocTypes.PI);
        Long user = currentUser.resolve();
        long seq = seqSource.incrementAndGet();
        AtomicLong latest = previewSeq.computeIfAbsent(user == null ? 0L : user, k -> new AtomicLong());
        latest.set(seq);
        try {
            byte[] xlsx = XlsxRenderer.render(tpl.file(), DocTypes.PI, model);
            List<byte[]> pages = converter.previewPages(cacheKey(tpl.versionId(), model), xlsx, () -> latest.get() == seq);
            if (pages == null) {
                vo.setSuperseded(true);
                return vo;
            }
            List<String> urls = new ArrayList<>(pages.size());
            for (byte[] p : pages) {
                urls.add("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(p));
            }
            vo.setPages(urls);
            vo.setUpdatedAt(LocalDateTime.now());
            return vo;
        } catch (BizException e) {
            if ("CONVERTER_UNAVAILABLE".equals(e.getErrorCode())) {
                vo.setUnavailable(true);
                vo.setMessage(PREVIEW_UNAVAILABLE);
                return vo;
            }
            throw e;
        }
    }

    private RenderModel model(ProformaInvoiceDO pi, PiVersionDO v, List<PiItemDO> items, List<PiFeeDO> fees) {
        CustomerDO customer = customerMapper.selectById(pi.getCustomerId());
        UserBasicDO owner = pi.getOwnerId() == null || pi.getOwnerId() == 0 ? null : userBasicMapper.selectById(pi.getOwnerId().intValue());
        LocalDate date = v.getSentAt() != null ? v.getSentAt().toLocalDate() : LocalDate.now();
        return PiRenderModels.build(pi, v, items, fees, store.fromJson(v.getBuyerJson(), PartyDTO.class),
                store.fromJson(v.getConsigneeJson(), PartyDTO.class), store.fromJson(v.getBankAccountJson(), BankSnapshotDTO.class),
                customer, owner, quotationStore.labels(), date)
                .withBrands(brandResolver::displayNames);
    }

    /** 文件名：PI 编号_客户简称或名称 */
    private String fileBase(ProformaInvoiceDO pi) {
        CustomerDO c = customerMapper.selectById(pi.getCustomerId());
        String name = c == null ? "" : (c.getShortName() != null && !c.getShortName().isBlank() ? c.getShortName() : c.getName());
        String clean = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", " ").trim();
        if (clean.length() > 60) {
            clean = clean.substring(0, 60).trim();
        }
        return clean.isEmpty() ? pi.getPiNo() : pi.getPiNo() + "_" + clean;
    }

    private String cacheKey(Long versionId, RenderModel model) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(model);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(("pi:" + versionId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("预览缓存键生成失败", e);
        }
    }
}
