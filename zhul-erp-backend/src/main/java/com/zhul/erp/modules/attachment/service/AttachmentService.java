package com.zhul.erp.modules.attachment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.FileStorageService.FileLink;
import com.zhul.erp.framework.storage.StorageArea;
import com.zhul.erp.framework.storage.UploadRules;
import com.zhul.erp.framework.storage.VideoTranscoder;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.attachment.dto.ModelTagVO;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import com.zhul.erp.modules.attachment.entity.BizAttachmentModelDO;
import com.zhul.erp.modules.attachment.repository.BizAttachmentMapper;
import com.zhul.erp.modules.attachment.repository.BizAttachmentModelMapper;
import com.zhul.erp.modules.attachment.support.ModelFolderResolver;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 业务附件：先上传拿到 ID（owner_id = 0），保存单据时挂到单据上；24 小时内没挂上的清理掉。
 * 图片 JPG / PNG / WEBP 不超过 10MB，视频 MP4 / MOV 不超过 200MB，按文件内容识别。
 * 文件按所属单据类型放到对应环节的目录（实拍任务按品牌与型号），图片上传时压缩，视频上传后在后台转码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

    public static final String SHIPMENT = "SHIPMENT";
    public static final String RECEIPT = "RECEIPT";
    public static final String SHOOT = "SHOOT";
    /** 出运单的面单（PDF 或图片） */
    public static final String LOGISTICS = "LOGISTICS";
    /** 付款单、供应商退款的截图（图片或 PDF） */
    public static final String PAYMENT = "PAYMENT";
    /** 报销用的付款截图：国内快递运费、退货运费（补传在来源上）、费用单凭证、报销付款的转账截图 */
    public static final String COURIER_PROOF = "COURIER_PROOF";
    public static final String RETURN_PROOF = "RETURN_PROOF";
    public static final String EXPENSE = "EXPENSE";
    public static final String REIMBURSE = "REIMBURSE";
    /** 采购订单的电子发票 */
    public static final String PO_INVOICE = "PO_INVOICE";
    /** 退款单与其他收入的凭证 */
    public static final String REFUND = "REFUND";
    public static final String OTHER_INCOME = "OTHER_INCOME";
    /** 询价回价的截图、报价单（图片或 PDF），挂在回价上，修改回价时搬到新版本 */
    public static final String SOURCING_QUOTE = "SOURCING_QUOTE";
    /** 验收入库的不良品照片（素材库「到货异常」） */
    public static final String RECEIPT_DEFECT = "RECEIPT_DEFECT";
    /** 打包出库的打包照片（素材库「打包发货」） */
    public static final String PACKING = "PACKING";
    /** 素材库里为某个型号上传的网络图片，挂在素材索引上 */
    public static final String NETWORK = "NETWORK";
    /** 销售订单附件：PI、合同、客户 PO 等，收 PDF、图片与 Excel */
    public static final String SALES_ORDER = "SALES_ORDER";
    /** 只收图片或 PDF 的归属类型 */
    private static final Set<String> PROOF_TYPES = Set.of(PAYMENT, COURIER_PROOF, RETURN_PROOF, EXPENSE, REIMBURSE, PO_INVOICE, REFUND, OTHER_INCOME,
            SOURCING_QUOTE);
    public static final Map<String, Integer> MAX_PER_OWNER = Map.ofEntries(Map.entry(SHIPMENT, 20), Map.entry(RECEIPT, 20),
            Map.entry(SHOOT, 30), Map.entry(LOGISTICS, 10), Map.entry(PAYMENT, 10), Map.entry(COURIER_PROOF, 10),
            Map.entry(RETURN_PROOF, 10), Map.entry(EXPENSE, 10), Map.entry(REIMBURSE, 10), Map.entry(PO_INVOICE, 10),
            Map.entry(REFUND, 10), Map.entry(OTHER_INCOME, 10), Map.entry(SOURCING_QUOTE, 5), Map.entry(RECEIPT_DEFECT, 20),
            Map.entry(PACKING, 20), Map.entry(NETWORK, 1), Map.entry(SALES_ORDER, 10));
    /** 只收图片的归属类型（不传视频） */
    private static final Set<String> IMAGE_ONLY_TYPES = Set.of(RECEIPT_DEFECT, PACKING, NETWORK);
    public static final int IMAGE = 1;
    public static final int VIDEO = 2;
    public static final int FILE = 3;

    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "png", "webp");
    private static final Set<String> VIDEO_EXTS = Set.of("mp4", "mov");
    private static final Set<String> FACE_SHEET_EXTS = Set.of("pdf", "jpg", "png", "webp");
    private static final Set<String> DOC_EXTS = Set.of("pdf", "jpg", "png", "webp", "xlsx", "xls");
    private static final long IMAGE_MAX = 10L * 1024 * 1024;
    private static final long VIDEO_MAX = 200L * 1024 * 1024;

    /** 所属单据类型 → 存储目录对应的业务环节 */
    private static final Map<String, StorageArea> AREAS = Map.ofEntries(Map.entry(SHIPMENT, StorageArea.SHIPMENT),
            Map.entry(RECEIPT, StorageArea.RECEIPT), Map.entry(SHOOT, StorageArea.MEDIA), Map.entry(LOGISTICS, StorageArea.LOGISTICS),
            Map.entry(PAYMENT, StorageArea.FINANCE), Map.entry(COURIER_PROOF, StorageArea.FINANCE),
            Map.entry(RETURN_PROOF, StorageArea.FINANCE), Map.entry(EXPENSE, StorageArea.FINANCE),
            Map.entry(REIMBURSE, StorageArea.FINANCE), Map.entry(REFUND, StorageArea.FINANCE),
            Map.entry(OTHER_INCOME, StorageArea.FINANCE), Map.entry(PO_INVOICE, StorageArea.PURCHASE),
            Map.entry(SOURCING_QUOTE, StorageArea.SOURCING), Map.entry(RECEIPT_DEFECT, StorageArea.RECEIPT),
            Map.entry(PACKING, StorageArea.PACKING), Map.entry(NETWORK, StorageArea.MEDIA),
            Map.entry(SALES_ORDER, StorageArea.SALES));

    private final BizAttachmentMapper mapper;
    private final BizAttachmentModelMapper tagMapper;
    private final FileStorageService files;
    private final VideoTranscoder transcoder;
    private final List<ModelFolderResolver> folders;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;

    /**
     * 上传附件。ownerId 选填：实拍任务等按型号归档的单据传入时，文件直接放到该型号的目录。
     */
    public AttachmentVO upload(String ownerType, Long ownerId, MultipartFile file) {
        requireOwnerType(ownerType);
        String name = file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean video = name.endsWith(".mp4") || name.endsWith(".mov")
                || (file != null && file.getContentType() != null && file.getContentType().startsWith("video/"));
        int tenantId = PiStore.tenantId();
        StorageArea area = AREAS.get(ownerType);
        FileStorageService.StoredFile s;
        int kind;
        if (SALES_ORDER.equals(ownerType)) {
            s = files.store(area, tenantId, file, DOC_EXTS, IMAGE_MAX, "只支持 PDF、JPG、PNG、WEBP、Excel");
            kind = s.contentType().startsWith("image/") ? IMAGE : FILE;
        } else if (LOGISTICS.equals(ownerType) || PROOF_TYPES.contains(ownerType)) {
            s = files.store(area, tenantId, file, FACE_SHEET_EXTS, IMAGE_MAX,
                    PROOF_TYPES.contains(ownerType) ? "截图只支持 PDF、JPG、PNG、WEBP" : "面单只支持 PDF、JPG、PNG、WEBP");
            kind = s.contentType().startsWith("image/") ? IMAGE : FILE;
        } else if (video && !IMAGE_ONLY_TYPES.contains(ownerType)) {
            s = store(area, ownerType, ownerId, tenantId, file, VIDEO_EXTS, VIDEO_MAX, "只支持 MP4、MOV 视频");
            kind = VIDEO;
        } else {
            s = store(area, ownerType, ownerId, tenantId, file, IMAGE_EXTS, IMAGE_MAX, "只支持 JPG、PNG、WEBP 图片");
            kind = IMAGE;
        }
        BizAttachmentDO a = new BizAttachmentDO();
        a.setTenantId(tenantId);
        a.setOwnerType(ownerType);
        a.setOwnerId(0L);
        a.setKind(kind);
        a.setStorage(s.storage());
        a.setFileKey(s.fileKey());
        a.setUrl("");
        a.setFileName(s.fileName());
        a.setFileSize(s.fileSize());
        a.setContentType(s.contentType());
        a.setProcessingStatus(kind == VIDEO ? VideoTranscoder.PROCESSING : VideoTranscoder.NONE);
        Long me = currentUser.resolve();
        a.setUploadedBy(me == null ? 0L : me);
        mapper.insert(a);
        if (kind == VIDEO) {
            transcode(a);
        }
        return toVos(List.of(a)).get(0);
    }

    /** 所属单据类型对应的存储环节（历史文件迁移按它放目录） */
    public static StorageArea areaOf(String ownerType) {
        return AREAS.get(ownerType);
    }

    private FileStorageService.StoredFile store(StorageArea area, String ownerType, Long ownerId, int tenantId, MultipartFile file,
                                                Set<String> exts, long max, String message) {
        if (ownerId != null && ownerId > 0) {
            for (ModelFolderResolver r : folders) {
                if (r.ownerType().equals(ownerType)) {
                    String ext = UploadRules.check(file, exts, max, message);
                    return r.modelOf(ownerId, tenantId)
                            .map(m -> files.storeForModel(area, tenantId, m.brand(), m.model(), file, ext))
                            .orElseGet(() -> files.storeValidated(area, tenantId, file, ext));
                }
            }
        }
        return files.store(area, tenantId, file, exts, max, message);
    }

    /** 上传一张图片，直接放到指定型号的素材目录（素材库的网络图片），之后由调用方挂到归属上 */
    public AttachmentVO uploadForModel(String ownerType, String brand, String model, MultipartFile file) {
        requireOwnerType(ownerType);
        int tenantId = PiStore.tenantId();
        String ext = UploadRules.check(file, IMAGE_EXTS, IMAGE_MAX, "只支持 JPG、PNG、WEBP 图片");
        FileStorageService.StoredFile s = files.storeForModel(StorageArea.MEDIA, tenantId, brand, model, file, ext);
        BizAttachmentDO a = new BizAttachmentDO();
        a.setTenantId(tenantId);
        a.setOwnerType(ownerType);
        a.setOwnerId(0L);
        a.setKind(IMAGE);
        a.setStorage(s.storage());
        a.setFileKey(s.fileKey());
        a.setUrl("");
        a.setFileName(s.fileName());
        a.setFileSize(s.fileSize());
        a.setContentType(s.contentType());
        a.setProcessingStatus(VideoTranscoder.NONE);
        Long me = currentUser.resolve();
        a.setUploadedBy(me == null ? 0L : me);
        mapper.insert(a);
        return toVos(List.of(a)).get(0);
    }

    /** 视频后台转码：完成后换成转好的文件，失败时保留原视频 */
    private void transcode(BizAttachmentDO a) {
        Long id = a.getId();
        transcoder.submit(a.getStorage(), a.getFileKey(), false, done -> {
            BizAttachmentDO upd = new BizAttachmentDO();
            upd.setId(id);
            upd.setFileKey(done.key());
            upd.setFileSize(done.size());
            upd.setContentType(done.contentType());
            upd.setFileName(UploadRules.withExt(a.getFileName(), "mp4"));
            upd.setProcessingStatus(VideoTranscoder.DONE);
            mapper.updateById(upd);
        }, () -> {
            BizAttachmentDO upd = new BizAttachmentDO();
            upd.setId(id);
            upd.setProcessingStatus(VideoTranscoder.FAILED);
            mapper.updateById(upd);
        });
    }

    /** 服务重启时仍在转码中的视频重新排队 */
    @EventListener(ApplicationReadyEvent.class)
    public void resumeTranscoding() {
        List<BizAttachmentDO> pending = mapper.selectList(new LambdaQueryWrapper<BizAttachmentDO>()
                .eq(BizAttachmentDO::getProcessingStatus, VideoTranscoder.PROCESSING)
                .isNull(BizAttachmentDO::getDeletedAt));
        if (!pending.isEmpty()) {
            log.info("重新排队转码的视频 {} 个", pending.size());
            pending.forEach(this::transcode);
        }
    }

    /** 把上传好的附件挂到单据上（还没挂的或已挂在这张单据上的），校验数量上限 */
    public void attach(String ownerType, Long ownerId, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<Long> keys = ids.stream().filter(Objects::nonNull).distinct().toList();
        List<BizAttachmentDO> rows = mapper.selectBatchIds(keys);
        for (BizAttachmentDO a : rows) {
            if (a.getDeletedAt() != null || !Objects.equals(a.getTenantId(), PiStore.tenantId()) || !ownerType.equals(a.getOwnerType())
                    || (a.getOwnerId() != 0 && !a.getOwnerId().equals(ownerId))) {
                throw new BizException("附件不存在，请重新上传");
            }
        }
        if (rows.size() != keys.size()) {
            throw new BizException("附件不存在，请重新上传");
        }
        mapper.update(null, new LambdaUpdateWrapper<BizAttachmentDO>()
                .set(BizAttachmentDO::getOwnerId, ownerId)
                .in(BizAttachmentDO::getId, keys)
                .eq(BizAttachmentDO::getOwnerId, 0L));
        long count = mapper.selectCount(owned(ownerType, List.of(ownerId)));
        int max = MAX_PER_OWNER.getOrDefault(ownerType, 20);
        if (count > max) {
            throw new BizException("每张单据最多 " + max + " 个附件");
        }
    }

    /**
     * 把附件挂到新的归属上：还没挂的，或原来挂在 fromOwners 之一上的（如被新版本替换掉的旧回价），其余拒绝；校验数量上限
     */
    public void reassign(String ownerType, Collection<Long> ids, Collection<Long> fromOwners, Long toOwner) {
        List<Long> keys = ids == null ? List.of() : ids.stream().filter(Objects::nonNull).distinct().toList();
        if (keys.isEmpty()) {
            return;
        }
        List<BizAttachmentDO> rows = mapper.selectBatchIds(keys);
        for (BizAttachmentDO a : rows) {
            if (a.getDeletedAt() != null || !Objects.equals(a.getTenantId(), PiStore.tenantId()) || !ownerType.equals(a.getOwnerType())
                    || (a.getOwnerId() != 0 && !fromOwners.contains(a.getOwnerId()))) {
                throw new BizException("附件不存在，请重新上传");
            }
        }
        if (rows.size() != keys.size()) {
            throw new BizException("附件不存在，请重新上传");
        }
        int max = MAX_PER_OWNER.getOrDefault(ownerType, 20);
        if (keys.size() > max) {
            throw new BizException("每条最多 " + max + " 个附件");
        }
        mapper.update(null, new LambdaUpdateWrapper<BizAttachmentDO>()
                .set(BizAttachmentDO::getOwnerId, toOwner)
                .in(BizAttachmentDO::getId, keys));
    }

    /** 修改单据时：附件以 ids 为准，不在其中的软删除，新的挂上 */
    public void sync(String ownerType, Long ownerId, Collection<Long> ids) {
        Set<Long> keep = ids == null ? Set.of() : ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        LambdaQueryWrapper<BizAttachmentDO> gone = owned(ownerType, List.of(ownerId));
        if (!keep.isEmpty()) {
            gone.notIn(BizAttachmentDO::getId, keep);
        }
        mapper.selectList(gone).forEach(a -> {
            a.setDeletedAt(LocalDateTime.now());
            mapper.updateById(a);
        });
        attach(ownerType, ownerId, keep);
    }

    /** 单据的附件，按上传先后 */
    public Map<Long, List<AttachmentVO>> list(String ownerType, Collection<Long> ownerIds) {
        List<Long> keys = ownerIds.stream().filter(Objects::nonNull).distinct().toList();
        if (keys.isEmpty()) {
            return Map.of();
        }
        List<BizAttachmentDO> rows = mapper.selectList(owned(ownerType, keys).orderByAsc(BizAttachmentDO::getId));
        Map<Long, AttachmentVO> vos = toVos(rows).stream().collect(Collectors.toMap(AttachmentVO::getId, v -> v));
        Map<Long, List<AttachmentVO>> out = new LinkedHashMap<>();
        for (BizAttachmentDO a : rows) {
            out.computeIfAbsent(a.getOwnerId(), k -> new java.util.ArrayList<>()).add(vos.get(a.getId()));
        }
        return out;
    }

    public List<AttachmentVO> listOf(String ownerType, Long ownerId) {
        return list(ownerType, List.of(ownerId)).getOrDefault(ownerId, List.of());
    }

    public Map<Long, Long> counts(String ownerType, Collection<Long> ownerIds) {
        Map<Long, Long> out = new HashMap<>();
        list(ownerType, ownerIds).forEach((k, v) -> out.put(k, (long) v.size()));
        return out;
    }

    /** 从单据上删除一个附件（软删除） */
    public void remove(String ownerType, Long ownerId, Long attachmentId) {
        BizAttachmentDO a = attachmentId == null ? null : mapper.selectById(attachmentId);
        if (a == null || a.getDeletedAt() != null || !Objects.equals(a.getTenantId(), PiStore.tenantId())
                || !ownerType.equals(a.getOwnerType()) || !Objects.equals(a.getOwnerId(), ownerId)) {
            throw new BizException("附件不存在");
        }
        a.setDeletedAt(LocalDateTime.now());
        mapper.updateById(a);
    }

    /** 本租户的附件；调用方已按所属单据类型校验过菜单权限 */
    public BizAttachmentDO require(Long id) {
        BizAttachmentDO a = id == null ? null : mapper.selectById(id);
        if (a == null || a.getDeletedAt() != null || !Objects.equals(a.getTenantId(), PiStore.tenantId())) {
            throw new BizException("附件不存在");
        }
        return a;
    }

    /** 附件的临时访问链接；调用方已按所属单据类型校验过菜单权限 */
    public FileLink link(BizAttachmentDO a, boolean download) {
        return files.link(a.getStorage(), a.getFileKey(), a.getFileName(), download);
    }

    /** 上传后 24 小时还没挂到单据上的附件软删除 */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 600_000L)
    public void cleanupOrphans() {
        mapper.update(null, new LambdaUpdateWrapper<BizAttachmentDO>()
                .set(BizAttachmentDO::getDeletedAt, LocalDateTime.now())
                .eq(BizAttachmentDO::getOwnerId, 0L)
                .isNull(BizAttachmentDO::getDeletedAt)
                .lt(BizAttachmentDO::getCreateTime, LocalDateTime.now().minusHours(24)));
    }

    private LambdaQueryWrapper<BizAttachmentDO> owned(String ownerType, Collection<Long> ownerIds) {
        return new LambdaQueryWrapper<BizAttachmentDO>()
                .eq(BizAttachmentDO::getTenantId, PiStore.tenantId())
                .eq(BizAttachmentDO::getOwnerType, ownerType)
                .in(BizAttachmentDO::getOwnerId, ownerIds)
                .isNull(BizAttachmentDO::getDeletedAt);
    }

    private static void requireOwnerType(String ownerType) {
        if (!MAX_PER_OWNER.containsKey(ownerType)) {
            throw new BizException("附件类型不正确");
        }
    }

    private List<AttachmentVO> toVos(List<BizAttachmentDO> rows) {
        Map<Long, String> users = lookups.userNames(rows.stream().map(BizAttachmentDO::getUploadedBy).toList());
        Map<Long, List<ModelTagVO>> tags = rows.isEmpty() ? Map.of() : tagMapper.selectList(new LambdaQueryWrapper<BizAttachmentModelDO>()
                        .in(BizAttachmentModelDO::getAttachmentId, rows.stream().map(BizAttachmentDO::getId).toList())
                        .isNull(BizAttachmentModelDO::getDeletedAt)
                        .orderByAsc(BizAttachmentModelDO::getId)).stream()
                .collect(Collectors.groupingBy(BizAttachmentModelDO::getAttachmentId,
                        Collectors.mapping(t -> new ModelTagVO(t.getBrand(), t.getModel()), Collectors.toList())));
        return rows.stream().map(a -> {
            AttachmentVO vo = new AttachmentVO();
            vo.setId(a.getId());
            vo.setKind(a.getKind());
            vo.setFileName(a.getFileName());
            vo.setFileSize(a.getFileSize());
            vo.setContentType(a.getContentType());
            vo.setProcessingStatus(a.getProcessingStatus());
            vo.setModels(tags.getOrDefault(a.getId(), List.of()));
            vo.setUploadedByName(users.get(a.getUploadedBy()));
            vo.setCreateTime(a.getCreateTime());
            return vo;
        }).toList();
    }
}
