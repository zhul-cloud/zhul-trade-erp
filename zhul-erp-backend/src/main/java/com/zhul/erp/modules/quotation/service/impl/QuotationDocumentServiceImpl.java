package com.zhul.erp.modules.quotation.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.document.constants.DocTypes;
import com.zhul.erp.modules.document.dto.TemplateFile;
import com.zhul.erp.modules.document.service.DocumentTemplateService;
import com.zhul.erp.modules.document.support.DocumentConverter;
import com.zhul.erp.modules.document.support.RenderModel;
import com.zhul.erp.modules.document.support.TextTemplateEngine;
import com.zhul.erp.modules.document.support.XlsxRenderer;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.masterdata.entity.CustomerDO;
import com.zhul.erp.modules.masterdata.repository.CustomerMapper;
import com.zhul.erp.modules.quotation.dto.PreviewRequest;
import com.zhul.erp.modules.quotation.dto.PreviewVO;
import com.zhul.erp.modules.quotation.dto.QuoteTextVO;
import com.zhul.erp.modules.quotation.entity.QuotationDO;
import com.zhul.erp.modules.quotation.entity.QuotationFeeDO;
import com.zhul.erp.modules.quotation.entity.QuotationItemDO;
import com.zhul.erp.modules.quotation.service.QuotationDocumentService;
import com.zhul.erp.modules.quotation.support.QuotationRenderModels;
import com.zhul.erp.modules.quotation.support.QuotationStore;
import com.zhul.erp.modules.system.entity.UserBasicDO;
import com.zhul.erp.modules.system.repository.UserBasicMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuotationDocumentServiceImpl implements QuotationDocumentService {

    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int IMAGE_MAX_WIDTH = 1600;
    private static final String PREVIEW_UNAVAILABLE = "预览暂时不可用，不影响编辑与导出 Excel";

    private final QuotationStore store;
    private final CustomerMapper customerMapper;
    private final UserBasicMapper userBasicMapper;
    private final DocumentTemplateService templateService;
    private final DocumentConverter converter;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;

    /** 每个用户最新一次预览请求的序号：排队中的旧请求在拿到转换机会时直接放弃 */
    private final Map<Long, AtomicLong> previewSeq = new ConcurrentHashMap<>();
    private final AtomicLong seqSource = new AtomicLong();

    @Override
    public QuoteTextVO text(Long id) {
        QuotationDO q = store.visible(id);
        DocumentTemplateService.Resolved tpl = templateService.resolveDefault(DocTypes.TEXT_QUOTE);
        QuoteTextVO vo = new QuoteTextVO();
        vo.setText(TextTemplateEngine.render(tpl.text(), model(q, store.items(id), store.fees(id))));
        vo.setTemplateVersionNo(tpl.versionNo());
        return vo;
    }

    @Override
    public TemplateFile export(Long id, String format) {
        QuotationDO q = store.visible(id);
        String f = format == null ? "xlsx" : format.trim().toLowerCase(Locale.ROOT);
        if (!List.of("xlsx", "pdf", "jpg").contains(f)) {
            throw new BizException("导出格式只支持 Excel、PDF、图片");
        }
        List<QuotationItemDO> items = store.items(id);
        if (items.isEmpty()) {
            throw new BizException("报价单没有型号，不能导出");
        }
        DocumentTemplateService.Resolved tpl = templateService.resolveDefault(DocTypes.QUOTATION);
        byte[] xlsx = XlsxRenderer.render(tpl.file(), model(q, items, store.fees(id)));
        String base = fileBase(q);
        return switch (f) {
            case "pdf" -> new TemplateFile(base + ".pdf", "application/pdf", converter.toPdf(xlsx));
            case "jpg" -> new TemplateFile(base + ".jpg", "image/jpeg", converter.toJpeg(converter.toPdf(xlsx), IMAGE_MAX_WIDTH));
            default -> new TemplateFile(base + ".xlsx", XLSX, xlsx);
        };
    }

    @Override
    public PreviewVO preview(PreviewRequest req) {
        QuotationDO saved = store.visible(req.getQuotationId());
        PreviewVO vo = new PreviewVO();
        if (!converter.available()) {
            vo.setUnavailable(true);
            vo.setMessage(PREVIEW_UNAVAILABLE);
            return vo;
        }
        // 在副本上套用编辑中的内容，不落库
        QuotationDO q = copyHeader(saved);
        List<QuotationItemDO> items = store.items(saved.getId());
        QuotationStore.Applied applied = store.apply(q, items, req.getContent());
        RenderModel model = model(q, applied.items(), applied.fees());
        DocumentTemplateService.Resolved tpl = templateService.resolveDefault(DocTypes.QUOTATION);
        Long user = currentUser.resolve();
        long seq = seqSource.incrementAndGet();
        AtomicLong latest = previewSeq.computeIfAbsent(user == null ? 0L : user, k -> new AtomicLong());
        latest.set(seq);
        try {
            byte[] xlsx = XlsxRenderer.render(tpl.file(), model);
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

    @Override
    public boolean converterAvailable() {
        return converter.available();
    }

    private RenderModel model(QuotationDO q, List<QuotationItemDO> items, List<QuotationFeeDO> fees) {
        CustomerDO customer = customerMapper.selectById(q.getCustomerId());
        UserBasicDO owner = q.getOwnerId() == null || q.getOwnerId() == 0 ? null : userBasicMapper.selectById(q.getOwnerId().intValue());
        return QuotationRenderModels.build(q, items, fees, customer, owner, store.labels(),
                q.getCreateTime() == null ? null : q.getCreateTime().toLocalDate());
    }

    /** 文件名：报价单编号_客户简称或名称 */
    private String fileBase(QuotationDO q) {
        CustomerDO c = customerMapper.selectById(q.getCustomerId());
        String name = c == null ? "" : (c.getShortName() != null && !c.getShortName().isBlank() ? c.getShortName() : c.getName());
        String clean = name == null ? "" : name.replaceAll("[\\\\/:*?\"<>|\\r\\n]+", " ").trim();
        if (clean.length() > 60) {
            clean = clean.substring(0, 60).trim();
        }
        return clean.isEmpty() ? q.getQuotationNo() : q.getQuotationNo() + "_" + clean;
    }

    private String cacheKey(Long versionId, RenderModel model) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(model);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(String.valueOf(versionId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest(json));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("预览缓存键生成失败", e);
        }
    }

    private static QuotationDO copyHeader(QuotationDO s) {
        QuotationDO q = new QuotationDO();
        q.setId(s.getId());
        q.setTenantId(s.getTenantId());
        q.setQuotationNo(s.getQuotationNo());
        q.setCustomerId(s.getCustomerId());
        q.setOwnerId(s.getOwnerId());
        q.setCurrencyCode(s.getCurrencyCode());
        q.setExchangeRate(s.getExchangeRate());
        q.setRateTime(s.getRateTime());
        q.setStatus(s.getStatus());
        q.setCreateTime(s.getCreateTime());
        return q;
    }
}
