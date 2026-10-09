package com.zhul.erp.modules.attachment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.attachment.dto.AttachmentFile;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import com.zhul.erp.modules.attachment.repository.BizAttachmentMapper;
import com.zhul.erp.modules.attachment.support.AttachmentStore;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.sales.support.PiStore;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
 */
@Service
@RequiredArgsConstructor
public class AttachmentService {

    public static final String SHIPMENT = "SHIPMENT";
    public static final String RECEIPT = "RECEIPT";
    public static final String SHOOT = "SHOOT";
    public static final Map<String, Integer> MAX_PER_OWNER = Map.of(SHIPMENT, 20, RECEIPT, 20, SHOOT, 30);
    public static final int IMAGE = 1;
    public static final int VIDEO = 2;

    private static final Set<String> IMAGE_EXTS = Set.of("jpg", "png", "webp");
    private static final Set<String> VIDEO_EXTS = Set.of("mp4", "mov");
    private static final long IMAGE_MAX = 10L * 1024 * 1024;
    private static final long VIDEO_MAX = 200L * 1024 * 1024;

    private final BizAttachmentMapper mapper;
    private final List<AttachmentStore> stores;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;

    /** 新上传文件用的存储方式（LOCAL / OSS） */
    @Value("${zhul.attachment.storage:LOCAL}")
    private String currentStorage;

    public AttachmentVO upload(String ownerType, MultipartFile file) {
        requireOwnerType(ownerType);
        String name = file == null || file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        boolean video = name.endsWith(".mp4") || name.endsWith(".mov")
                || (file != null && file.getContentType() != null && file.getContentType().startsWith("video/"));
        AttachmentStore store = store(currentStorage);
        AttachmentStore.Stored s = video
                ? store.put(PiStore.tenantId(), file, VIDEO_EXTS, VIDEO_MAX, "只支持 MP4、MOV 视频")
                : store.put(PiStore.tenantId(), file, IMAGE_EXTS, IMAGE_MAX, "只支持 JPG、PNG、WEBP 图片");
        BizAttachmentDO a = new BizAttachmentDO();
        a.setTenantId(PiStore.tenantId());
        a.setOwnerType(ownerType);
        a.setOwnerId(0L);
        a.setKind(video ? VIDEO : IMAGE);
        a.setStorage(store.storage());
        a.setFileKey(s.fileKey());
        a.setUrl(s.url());
        a.setFileName(s.fileName());
        a.setFileSize(s.fileSize());
        a.setContentType(s.contentType());
        Long me = currentUser.resolve();
        a.setUploadedBy(me == null ? 0L : me);
        mapper.insert(a);
        return toVos(List.of(a)).get(0);
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

    public AttachmentFile file(BizAttachmentDO a) {
        return new AttachmentFile(store(a.getStorage()).open(a), a.getFileName(), a.getContentType());
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

    private AttachmentStore store(String storage) {
        return stores.stream().filter(s -> s.storage().equalsIgnoreCase(storage)).findFirst()
                .orElseThrow(() -> new IllegalStateException("没有配置附件存储方式：" + storage));
    }

    private static void requireOwnerType(String ownerType) {
        if (!MAX_PER_OWNER.containsKey(ownerType)) {
            throw new BizException("附件类型不正确");
        }
    }

    private List<AttachmentVO> toVos(List<BizAttachmentDO> rows) {
        Map<Long, String> users = lookups.userNames(rows.stream().map(BizAttachmentDO::getUploadedBy).toList());
        return rows.stream().map(a -> {
            AttachmentVO vo = new AttachmentVO();
            vo.setId(a.getId());
            vo.setKind(a.getKind());
            vo.setFileName(a.getFileName());
            vo.setFileSize(a.getFileSize());
            vo.setContentType(a.getContentType());
            vo.setUploadedByName(users.get(a.getUploadedBy()));
            vo.setCreateTime(a.getCreateTime());
            return vo;
        }).toList();
    }
}
