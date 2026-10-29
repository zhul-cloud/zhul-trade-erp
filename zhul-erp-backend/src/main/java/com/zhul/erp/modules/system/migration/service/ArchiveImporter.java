package com.zhul.erp.modules.system.migration.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.zhul.erp.common.exception.BizException;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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
 * <p>
 * 事务边界：型号每 200 行一个事务，图片每张单据一个事务，下载在事务外进行，避免长时间锁住候选池。
 * 干跑时这些事务并入外层事务，一起回滚。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArchiveImporter {

    static final String STAGE = "候选商品与素材";
    private static final int SYNC_BATCH = 200;
    /** 每处理这么多张图片写一次进度 */
    private static final int PROGRESS_EVERY = 100;

    private final ProductArchiver archiver;
    private final InquiryItemMapper itemMapper;
    private final AttachmentService attachments;
    private final FileStorageService files;
    private final CustomerInquiryAttachmentMapper inquiryAttachmentMapper;
    private final MediaIndexer mediaIndexer;
    private final PlatformTransactionManager transactionManager;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    /** progress：写一次进度（正式执行时写入批次的阶段统计） */
    void run(MigrationRun r, Collection<Long> itemIds, Runnable progress) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        List<Long> ids = new ArrayList<>(new LinkedHashSet<>(itemIds));
        for (int i = 0; i < ids.size(); i += SYNC_BATCH) {
            List<Long> part = ids.subList(i, Math.min(ids.size(), i + SYNC_BATCH));
            tx.executeWithoutResult(s -> archiver.sync(part));
        }
        r.created(STAGE, "型号建档或入池（询盘型号行）", ids.size());
        progress.run();
        media(r, tx, progress);
    }

    private record Group(int category, String sourceKey, Set<String> tokens, Map<String, MediaIndexer.ModelRef> models,
                         Map<String, String> files, Map<String, String> urls) {
    }

    /** 取到的图片；temp 为下载到临时目录的，用完删除 */
    private record Fetched(LocalMultipartFile file, Path temp) {
    }

    private void media(MigrationRun r, TransactionTemplate tx, Runnable progress) {
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
        if (!r.dryRun) {
            probe(groups.values());
        }
        int done = 0;
        for (Group g : groups.values()) {
            if (r.dryRun) {
                r.created(STAGE, "素材图片（干跑不上传）");
                continue;
            }
            Map<String, Fetched> got = new LinkedHashMap<>();
            try {
                if (owner(r, g) == null) {
                    continue;
                }
                String target = g.category() == 3 ? "customer_inquiry_attachment" : "biz_attachment";
                for (String token : g.tokens()) {
                    if (r.mapped("media", token + "|" + g.sourceKey(), target) == null) {
                        Fetched f = fetch(r, g, token);
                        if (f == null) {
                            r.failed(STAGE, "素材图片（取不到文件）");
                        } else {
                            got.put(token, f);
                        }
                    }
                    if (++done % PROGRESS_EVERY == 0) {
                        progress.run();
                    }
                }
                tx.executeWithoutResult(s -> {
                    switch (g.category()) {
                        case 3 -> inquiryImages(r, g, got);
                        case 4 -> quoteImages(r, g, got);
                        default -> shipmentImages(r, g, got);
                    }
                });
            } catch (RuntimeException e) {
                log.warn("素材归档失败，category={}，sourceKey={}", g.category(), g.sourceKey(), e);
                r.failed(STAGE, "素材图片");
            } finally {
                got.values().forEach(f -> FileStorageService.deleteTemp(f.temp()));
            }
        }
    }

    /** 图片所属单据的ID；单据没迁移过来时为 null */
    private Long owner(MigrationRun r, Group g) {
        return switch (g.category()) {
            case 3 -> r.mapped("inquiries", g.sourceKey(), "customer_inquiry");
            case 4 -> r.mapped("quotes", g.sourceKey(), "sourcing_quote");
            default -> r.mapped("shipments", g.sourceKey(), "supplier_shipment");
        };
    }

    /** 开始归档前先试下载一张：服务器访问不了图片地址或链接已过期时立即失败，不逐张空等超时 */
    private void probe(Collection<Group> groups) {
        String url = groups.stream().flatMap(g -> g.urls().values().stream())
                .filter(u -> u != null && !u.isBlank()).findFirst().orElse(null);
        if (url == null) {
            return;
        }
        long start = System.currentTimeMillis();
        try {
            HttpResponse<Void> res = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
            if (res.statusCode() != 200) {
                throw new BizException("试下载迁移图片失败（HTTP " + res.statusCode() + "），403 一般是图片链接已过期，需要重新打包；"
                        + "已完成的阶段保留");
            }
        } catch (IOException e) {
            throw new BizException("服务器访问不了迁移图片地址（" + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : "：" + e.getMessage()) + "），请检查服务器能否访问开发 OSS；已完成的阶段保留", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException("试下载迁移图片被中断", e);
        }
        log.info("迁移图片试下载成功，耗时 {} 毫秒", System.currentTimeMillis() - start);
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
    private void inquiryImages(MigrationRun r, Group g, Map<String, Fetched> fetched) {
        Long inquiryId = r.mapped("inquiries", g.sourceKey(), "customer_inquiry");
        if (inquiryId == null) {
            return;
        }
        int sort = 0;
        for (String token : g.tokens()) {
            if (r.mapped("media", token + "|" + g.sourceKey(), "customer_inquiry_attachment") != null) {
                continue;
            }
            Fetched got = fetched.get(token);
            if (got == null) {
                continue;
            }
            LocalMultipartFile f = got.file();
            String ext = f.getOriginalFilename().substring(f.getOriginalFilename().lastIndexOf('.') + 1);
            FileStorageService.StoredFile s = files.storeValidated(StorageArea.INQUIRY, r.tenantId, f, ext);
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
    private void quoteImages(MigrationRun r, Group g, Map<String, Fetched> fetched) {
        Long quoteId = r.mapped("quotes", g.sourceKey(), "sourcing_quote");
        if (quoteId == null) {
            return;
        }
        List<Long> ids = upload(r, g, fetched, AttachmentService.SOURCING_QUOTE, "供方实拍");
        if (!ids.isEmpty()) {
            attachments.attach(AttachmentService.SOURCING_QUOTE, quoteId, ids);
        }
        MediaIndexer.ModelRef m = g.models().values().iterator().next();
        mediaIndexer.indexSourcingQuote(quoteId, r.tenantId, m.brand(), m.model());
    }

    /** 发货、到货图片：存为供应商发货单附件，标注订单的型号 */
    private void shipmentImages(MigrationRun r, Group g, Map<String, Fetched> fetched) {
        Long shipmentId = r.mapped("shipments", g.sourceKey(), "supplier_shipment");
        if (shipmentId == null) {
            return;
        }
        List<Long> ids = upload(r, g, fetched, AttachmentService.SHIPMENT, g.category() == 7 ? "到货图片" : "供方发货");
        if (!ids.isEmpty()) {
            attachments.attach(AttachmentService.SHIPMENT, shipmentId, ids);
        }
        mediaIndexer.indexDocument(AttachmentService.SHIPMENT, shipmentId, r.tenantId, new ArrayList<>(g.models().values()), null);
    }

    private List<Long> upload(MigrationRun r, Group g, Map<String, Fetched> fetched, String ownerType, String kind) {
        List<Long> ids = new ArrayList<>();
        for (String token : g.tokens()) {
            if (r.mapped("media", token + "|" + g.sourceKey(), "biz_attachment") != null) {
                continue;
            }
            Fetched got = fetched.get(token);
            if (got == null) {
                continue;
            }
            AttachmentVO a = attachments.upload(ownerType, null, got.file());
            r.record("media", token + "|" + g.sourceKey(), "biz_attachment", a.getId());
            ids.add(a.getId());
            r.created(STAGE, kind);
        }
        return ids;
    }
}
