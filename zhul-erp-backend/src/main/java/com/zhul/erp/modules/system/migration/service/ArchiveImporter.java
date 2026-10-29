package com.zhul.erp.modules.system.migration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.StorageArea;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.inquiry.customerinquiry.entity.CustomerInquiryAttachmentDO;
import com.zhul.erp.modules.inquiry.customerinquiry.repository.CustomerInquiryAttachmentMapper;
import com.zhul.erp.modules.inquiry.item.entity.InquiryItemDO;
import com.zhul.erp.modules.inquiry.item.repository.InquiryItemMapper;
import com.zhul.erp.modules.media.service.MediaIndexer;
import com.zhul.erp.modules.product.candidate.service.ProductArchiver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.zhul.erp.modules.system.migration.service.MigrationRun.text;

/**
 * 候选商品与素材阶段：型号行按日常规则关联商品或进入候选池（询盘出现、采购问到有货、成交），
 * 图片按品牌 + 型号归档到素材库（客户来图、供方实拍、供方发货）。干跑不写文件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArchiveImporter {

    static final String STAGE = "候选商品与素材";
    private static final int SYNC_BATCH = 200;

    private final ProductArchiver archiver;
    private final InquiryItemMapper itemMapper;
    private final AttachmentService attachments;
    private final FileStorageService files;
    private final CustomerInquiryAttachmentMapper inquiryAttachmentMapper;
    private final MediaIndexer mediaIndexer;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    void run(MigrationRun r, Collection<Long> itemIds) {
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(itemIds));
        for (int i = 0; i < ids.size(); i += SYNC_BATCH) {
            archiver.sync(ids.subList(i, Math.min(ids.size(), i + SYNC_BATCH)));
        }
        r.created(STAGE, "型号建档或入池（询盘型号行）", ids.size());
        media(r);
    }

    private record Group(int category, String sourceKey, Set<String> tokens, Map<String, MediaIndexer.ModelRef> models,
                         Map<String, String> files, Map<String, String> urls) {
    }

    /** 取到的图片；temp 为下载到临时目录的，用完删除 */
    private record Fetched(LocalMultipartFile file, Path temp) {
    }

    private void media(MigrationRun r) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (JsonNode m : r.list("media")) {
            int category = m.path("category").asInt();
            String sourceKey = text(m, "sourceKey");
            Group g = groups.computeIfAbsent(category + "|" + sourceKey,
                    k -> new Group(category, sourceKey, new LinkedHashSet<>(), new LinkedHashMap<>(), new LinkedHashMap<>(),
                            new LinkedHashMap<>()));
            String token = text(m, "token");
            g.tokens().add(token);
            g.files().put(token, text(m, "file"));
            g.urls().put(token, text(m, "url"));
            MediaIndexer.ModelRef ref = new MediaIndexer.ModelRef(text(m, "brand"), text(m, "model"));
            g.models().putIfAbsent(ref.assetKey(), ref);
        }
        for (Group g : groups.values()) {
            if (r.dryRun) {
                r.created(STAGE, "素材图片（干跑不上传）");
                continue;
            }
            try {
                switch (g.category()) {
                    case 3 -> inquiryImages(r, g);
                    case 4 -> quoteImages(r, g);
                    default -> shipmentImages(r, g);
                }
            } catch (RuntimeException e) {
                log.warn("素材归档失败，category={}，sourceKey={}", g.category(), g.sourceKey(), e);
                r.failed(STAGE, "素材图片");
            }
        }
    }

    /** 有下载链接的先下载到临时文件（生产环境：图片放在开发 OSS，链接有效期 7 天），否则读附件目录 */
    private Fetched fetch(MigrationRun r, Group g, String token) {
        String file = g.files().get(token);
        String url = g.urls().get(token);
        Path p;
        Path temp = null;
        if (url != null && !url.isBlank()) {
            temp = download(url, file);
            if (temp == null) {
                return null;
            }
            p = temp;
        } else {
            p = Path.of(r.attachmentDir, file);
            if (!Files.isRegularFile(p)) {
                return null;
            }
        }
        String name = file.toLowerCase().replace(".jpeg", ".jpg");
        String type = name.endsWith(".png") ? "image/png" : name.endsWith(".webp") ? "image/webp" : "image/jpeg";
        return new Fetched(new LocalMultipartFile(p, name, type), temp);
    }

    private Path download(String url, String file) {
        Path temp = null;
        try {
            temp = Files.createTempFile("hm-", file.contains(".") ? file.substring(file.lastIndexOf('.')) : "");
            HttpResponse<Path> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(120)).GET().build(),
                    HttpResponse.BodyHandlers.ofFile(temp));
            if (res.statusCode() == 200) {
                return temp;
            }
            log.warn("下载迁移图片失败，status={}，file={}", res.statusCode(), file);
        } catch (IOException | IllegalArgumentException e) {
            log.warn("下载迁移图片失败，file={}", file, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("下载迁移图片被中断，file={}", file);
        }
        FileStorageService.deleteTemp(temp);
        return null;
    }

    /** 客户来图：存为询盘单附件，按询盘单的型号建索引 */
    private void inquiryImages(MigrationRun r, Group g) {
        Long inquiryId = r.mapped("inquiries", g.sourceKey(), "customer_inquiry");
        if (inquiryId == null) {
            return;
        }
        int sort = 0;
        for (String token : g.tokens()) {
            if (r.mapped("media", token + "|" + g.sourceKey(), "customer_inquiry_attachment") != null) {
                continue;
            }
            Fetched got = fetch(r, g, token);
            if (got == null) {
                r.failed(STAGE, "素材图片（取不到文件）");
                continue;
            }
            LocalMultipartFile f = got.file();
            String ext = f.getOriginalFilename().substring(f.getOriginalFilename().lastIndexOf('.') + 1);
            FileStorageService.StoredFile s;
            try {
                s = files.storeValidated(StorageArea.INQUIRY, r.tenantId, f, ext);
            } finally {
                FileStorageService.deleteTemp(got.temp());
            }
            CustomerInquiryAttachmentDO a = new CustomerInquiryAttachmentDO();
            a.setTenantId(r.tenantId);
            a.setCustomerInquiryId(inquiryId);
            a.setFileName(s.fileName());
            a.setFileKey(s.fileKey());
            a.setStorage(s.storage());
            a.setContentType(s.contentType());
            a.setFileSize(s.fileSize());
            a.setSort(++sort);
            a.setCreateBy("migration");
            inquiryAttachmentMapper.insert(a);
            r.record("media", token + "|" + g.sourceKey(), "customer_inquiry_attachment", a.getId());
            r.created(STAGE, "客户来图");
        }
        List<MediaIndexer.ModelRef> models = itemMapper.selectList(new LambdaQueryWrapper<InquiryItemDO>()
                        .eq(InquiryItemDO::getCustomerInquiryId, inquiryId).isNull(InquiryItemDO::getDeletedAt)).stream()
                .filter(i -> i.getConfidence() == null || i.getConfidence() != 4)
                .map(i -> new MediaIndexer.ModelRef(i.getBrand() == null ? "" : i.getBrand(), i.getConfirmedModel())).toList();
        mediaIndexer.indexInquiry(inquiryId, r.tenantId, models);
    }

    /** 供方实拍：存为询价记录附件，归该询价记录的型号 */
    private void quoteImages(MigrationRun r, Group g) {
        Long quoteId = r.mapped("quotes", g.sourceKey(), "sourcing_quote");
        if (quoteId == null) {
            return;
        }
        List<Long> ids = upload(r, g, AttachmentService.SOURCING_QUOTE, "供方实拍");
        if (!ids.isEmpty()) {
            attachments.attach(AttachmentService.SOURCING_QUOTE, quoteId, ids);
        }
        MediaIndexer.ModelRef m = g.models().values().iterator().next();
        mediaIndexer.indexSourcingQuote(quoteId, r.tenantId, m.brand(), m.model());
    }

    /** 发货、到货图片：存为供应商发货单附件，标注订单的型号 */
    private void shipmentImages(MigrationRun r, Group g) {
        Long shipmentId = r.mapped("shipments", g.sourceKey(), "supplier_shipment");
        if (shipmentId == null) {
            return;
        }
        List<Long> ids = upload(r, g, AttachmentService.SHIPMENT, g.category() == 7 ? "到货图片" : "供方发货");
        if (!ids.isEmpty()) {
            attachments.attach(AttachmentService.SHIPMENT, shipmentId, ids);
        }
        mediaIndexer.indexDocument(AttachmentService.SHIPMENT, shipmentId, r.tenantId, new ArrayList<>(g.models().values()), null);
    }

    private List<Long> upload(MigrationRun r, Group g, String ownerType, String kind) {
        List<Long> ids = new ArrayList<>();
        for (String token : g.tokens()) {
            if (r.mapped("media", token + "|" + g.sourceKey(), "biz_attachment") != null) {
                continue;
            }
            Fetched got = fetch(r, g, token);
            if (got == null) {
                r.failed(STAGE, "素材图片（取不到文件）");
                continue;
            }
            AttachmentVO a;
            try {
                a = attachments.upload(ownerType, null, got.file());
            } finally {
                FileStorageService.deleteTemp(got.temp());
            }
            r.record("media", token + "|" + g.sourceKey(), "biz_attachment", a.getId());
            ids.add(a.getId());
            r.created(STAGE, kind);
        }
        return ids;
    }
}
