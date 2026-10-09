package com.zhul.erp.modules.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.inquiry.support.InquiryLookups;
import com.zhul.erp.modules.sales.entity.SalesOrderDO;
import com.zhul.erp.modules.sales.repository.SalesOrderMapper;
import com.zhul.erp.modules.sales.support.PiStore;
import com.zhul.erp.modules.system.service.LogService;
import com.zhul.erp.modules.warehouse.constants.WarehouseConstants;
import com.zhul.erp.modules.warehouse.dto.AddMediaRequest;
import com.zhul.erp.modules.warehouse.dto.ShootListVO;
import com.zhul.erp.modules.warehouse.dto.ShootPageQuery;
import com.zhul.erp.modules.warehouse.dto.ShootVO;
import com.zhul.erp.modules.warehouse.entity.MediaAssetDO;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptItemDO;
import com.zhul.erp.modules.warehouse.entity.ShootTaskDO;
import com.zhul.erp.modules.warehouse.repository.MediaAssetMapper;
import com.zhul.erp.modules.warehouse.repository.PurchaseReceiptMapper;
import com.zhul.erp.modules.warehouse.repository.ShootTaskMapper;
import com.zhul.erp.modules.warehouse.support.ReceivingSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 拍摄任务：入库验收时为合格入库的型号生成；拆箱视频、验货视频各 1 个以上、实物图 6 张以上时自动完成。
 * 素材归到「品牌 + 型号」名下（media_asset），同型号已有完成的素材时可以直接复用。不管发布。
 * 写操作用读已提交：同一任务并发上传多张图时，拿到行锁后要看到别人已提交的素材，才能判定是否齐全。
 */
@Service
@RequiredArgsConstructor
public class ShootService {

    private final ShootTaskMapper taskMapper;
    private final MediaAssetMapper mediaMapper;
    private final PurchaseReceiptMapper receiptMapper;
    private final SalesOrderMapper soMapper;
    private final AttachmentService attachments;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;

    /** 入库验收确认时调用（调用方事务内）：每个合格数量大于 0 的入库行一条任务 */
    void createForReceipt(PurchaseReceiptDO receipt, List<PurchaseReceiptItemDO> items, Map<Long, Long> soIdByPoItem) {
        for (PurchaseReceiptItemDO i : items) {
            if (i.getQualifiedQty() <= 0) {
                continue;
            }
            ShootTaskDO t = new ShootTaskDO();
            t.setTenantId(receipt.getTenantId());
            t.setReceiptId(receipt.getId());
            t.setReceiptItemId(i.getId());
            t.setSoId(Objects.requireNonNullElse(soIdByPoItem.get(i.getPoItemId()), 0L));
            t.setModel(i.getModel());
            t.setBrand(i.getBrand());
            t.setCategory(i.getCategory());
            t.setAssetKey(ReceivingSupport.assetKey(i.getBrand(), i.getModel()));
            t.setStatus(WarehouseConstants.SHOOT_PENDING);
            t.setSkipReason("");
            taskMapper.insert(t);
        }
    }

    /** 入库单的任务都还没开始（待拍摄、没有素材）时返回 true */
    boolean notStarted(Long receiptId) {
        List<ShootTaskDO> tasks = tasksOfReceipt(receiptId);
        if (tasks.stream().anyMatch(t -> t.getStatus() != WarehouseConstants.SHOOT_PENDING)) {
            return false;
        }
        return tasks.isEmpty() || mediaMapper.selectCount(new LambdaQueryWrapper<MediaAssetDO>()
                .in(MediaAssetDO::getTaskId, tasks.stream().map(ShootTaskDO::getId).toList())
                .isNull(MediaAssetDO::getDeletedAt)) == 0;
    }

    /** 冲销入库时作废它的拍摄任务（调用方事务内） */
    void voidForReceipt(Long receiptId) {
        for (ShootTaskDO t : tasksOfReceipt(receiptId)) {
            t.setDeletedAt(LocalDateTime.now());
            taskMapper.updateById(t);
        }
    }

    private List<ShootTaskDO> tasksOfReceipt(Long receiptId) {
        return taskMapper.selectList(new LambdaQueryWrapper<ShootTaskDO>()
                .eq(ShootTaskDO::getReceiptId, receiptId)
                .isNull(ShootTaskDO::getDeletedAt));
    }

    // ---------------------------------------------------------------- 列表与详情

    public PageResult<ShootListVO> page(ShootPageQuery q) {
        LambdaQueryWrapper<ShootTaskDO> w = new LambdaQueryWrapper<ShootTaskDO>()
                .eq(ShootTaskDO::getTenantId, PiStore.tenantId())
                .isNull(ShootTaskDO::getDeletedAt);
        if (q.getStatus() != null) {
            w.eq(ShootTaskDO::getStatus, q.getStatus());
        }
        if (StringUtils.hasText(q.getKeyword())) {
            String kw = q.getKeyword().trim();
            List<Long> receiptIds = receiptMapper.selectList(new LambdaQueryWrapper<PurchaseReceiptDO>()
                            .select(PurchaseReceiptDO::getId)
                            .eq(PurchaseReceiptDO::getTenantId, PiStore.tenantId())
                            .like(PurchaseReceiptDO::getGrNo, kw)
                            .last("LIMIT 500"))
                    .stream().map(PurchaseReceiptDO::getId).toList();
            List<Long> soIds = soMapper.selectList(new LambdaQueryWrapper<SalesOrderDO>()
                            .select(SalesOrderDO::getId)
                            .eq(SalesOrderDO::getTenantId, PiStore.tenantId())
                            .like(SalesOrderDO::getSoNo, kw)
                            .last("LIMIT 500"))
                    .stream().map(SalesOrderDO::getId).toList();
            w.and(x -> {
                x.like(ShootTaskDO::getModel, kw).or().like(ShootTaskDO::getBrand, kw);
                if (!receiptIds.isEmpty()) {
                    x.or().in(ShootTaskDO::getReceiptId, receiptIds);
                }
                if (!soIds.isEmpty()) {
                    x.or().in(ShootTaskDO::getSoId, soIds);
                }
            });
        }
        int size = q.getPageSize() == null || q.getPageSize() <= 0 ? 20 : Math.min(q.getPageSize(), 100);
        int page = Math.max(1, q.getPage() == null ? 1 : q.getPage());
        long total = taskMapper.selectCount(w);
        if (total == 0) {
            return PageResult.of(0L, List.of());
        }
        w.last("ORDER BY update_time DESC, id DESC LIMIT " + (long) (page - 1) * size + ", " + size);
        return PageResult.of(total, toListVos(taskMapper.selectList(w)));
    }

    public long countPending() {
        return taskMapper.selectCount(new LambdaQueryWrapper<ShootTaskDO>()
                .eq(ShootTaskDO::getTenantId, PiStore.tenantId())
                .eq(ShootTaskDO::getStatus, WarehouseConstants.SHOOT_PENDING)
                .isNull(ShootTaskDO::getDeletedAt));
    }

    public ShootVO detail(Long id) {
        ShootTaskDO t = visible(id);
        ShootVO vo = new ShootVO();
        vo.setTask(toListVos(List.of(t)).get(0));
        Long own = mediaOwner(t);
        vo.setMedia(mediaVos(mediaMapper.selectList(new LambdaQueryWrapper<MediaAssetDO>()
                .eq(MediaAssetDO::getTaskId, own)
                .isNull(MediaAssetDO::getDeletedAt)
                .orderByAsc(MediaAssetDO::getId))));
        vo.setSameModel(mediaVos(mediaMapper.selectList(new LambdaQueryWrapper<MediaAssetDO>()
                .eq(MediaAssetDO::getTenantId, t.getTenantId())
                .eq(MediaAssetDO::getAssetKey, t.getAssetKey())
                .ne(MediaAssetDO::getTaskId, own)
                .isNull(MediaAssetDO::getDeletedAt)
                .orderByDesc(MediaAssetDO::getId)
                .last("LIMIT 60"))));
        return vo;
    }

    private List<ShootListVO> toListVos(List<ShootTaskDO> rows) {
        Map<Long, Map<Integer, Long>> counts = new HashMap<>();
        List<Long> owners = rows.stream().map(ShootService::mediaOwner).distinct().toList();
        if (!owners.isEmpty()) {
            for (MediaAssetDO m : mediaMapper.selectList(new LambdaQueryWrapper<MediaAssetDO>()
                    .select(MediaAssetDO::getTaskId, MediaAssetDO::getMediaType)
                    .in(MediaAssetDO::getTaskId, owners)
                    .isNull(MediaAssetDO::getDeletedAt))) {
                counts.computeIfAbsent(m.getTaskId(), k -> new HashMap<>()).merge(m.getMediaType(), 1L, Long::sum);
            }
        }
        Map<String, ShootTaskDO> sources = sources(rows.stream().map(ShootTaskDO::getAssetKey).distinct().toList());
        Map<Long, String> grNos = new HashMap<>();
        ReceivingSupport.byId(rows.stream().map(ShootTaskDO::getReceiptId).toList(), receiptMapper::selectBatchIds, PurchaseReceiptDO::getId)
                .forEach((k, v) -> grNos.put(k, v.getGrNo()));
        Map<Long, String> soNos = new HashMap<>();
        ReceivingSupport.byId(rows.stream().map(ShootTaskDO::getSoId).filter(x -> x != null && x > 0).toList(),
                soMapper::selectBatchIds, SalesOrderDO::getId).forEach((k, v) -> soNos.put(k, v.getSoNo()));
        Map<Long, String> users = lookups.userNames(rows.stream().map(ShootTaskDO::getShooterId).toList());
        List<ShootListVO> out = new ArrayList<>(rows.size());
        for (ShootTaskDO t : rows) {
            Map<Integer, Long> c = counts.getOrDefault(mediaOwner(t), Map.of());
            ShootTaskDO src = sources.get(t.getAssetKey());
            ShootListVO vo = new ShootListVO();
            vo.setId(t.getId());
            vo.setModel(t.getModel());
            vo.setBrand(t.getBrand());
            vo.setCategory(t.getCategory());
            vo.setReceiptId(t.getReceiptId());
            vo.setGrNo(grNos.get(t.getReceiptId()));
            vo.setSoId(t.getSoId());
            vo.setSoNo(soNos.get(t.getSoId()));
            vo.setStatus(t.getStatus());
            vo.setStatusName(WarehouseConstants.SHOOT_STATUS_NAMES.get(t.getStatus()));
            vo.setUnboxingCount(c.getOrDefault(WarehouseConstants.MEDIA_UNBOXING, 0L).intValue());
            vo.setInspectionCount(c.getOrDefault(WarehouseConstants.MEDIA_INSPECTION, 0L).intValue());
            vo.setPhotoCount(c.getOrDefault(WarehouseConstants.MEDIA_PHOTO, 0L).intValue());
            boolean reusable = src != null && !src.getId().equals(t.getId());
            vo.setReusable(reusable);
            vo.setReusableShotAt(reusable ? src.getCompletedAt() : null);
            vo.setReusedFromTaskId(t.getReusedFromTaskId());
            vo.setSkipReason(t.getSkipReason());
            vo.setShooterName(users.get(t.getShooterId()));
            vo.setCompletedAt(t.getCompletedAt());
            vo.setCreateTime(t.getCreateTime());
            vo.setCreateBy(t.getCreateBy());
            vo.setUpdateTime(t.getUpdateTime());
            vo.setUpdateBy(t.getUpdateBy());
            out.add(vo);
        }
        return out;
    }

    /** 素材键 → 最近完成的、自己拍的（不是复用的）任务 */
    private Map<String, ShootTaskDO> sources(List<String> keys) {
        Map<String, ShootTaskDO> out = new HashMap<>();
        if (keys.isEmpty()) {
            return out;
        }
        taskMapper.selectList(new LambdaQueryWrapper<ShootTaskDO>()
                        .eq(ShootTaskDO::getTenantId, PiStore.tenantId())
                        .in(ShootTaskDO::getAssetKey, keys)
                        .eq(ShootTaskDO::getStatus, WarehouseConstants.SHOOT_DONE)
                        .isNull(ShootTaskDO::getReusedFromTaskId)
                        .isNull(ShootTaskDO::getDeletedAt))
                .stream()
                .sorted(Comparator.comparing(ShootTaskDO::getCompletedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .forEach(t -> out.put(t.getAssetKey(), t));
        return out;
    }

    private List<ShootVO.Media> mediaVos(List<MediaAssetDO> rows) {
        Map<Long, AttachmentVO> files = new HashMap<>();
        rows.stream().map(MediaAssetDO::getTaskId).distinct().forEach(taskId ->
                attachments.listOf(AttachmentService.SHOOT, taskId).forEach(a -> files.put(a.getId(), a)));
        return rows.stream().filter(m -> files.containsKey(m.getAttachmentId())).map(m -> {
            AttachmentVO a = files.get(m.getAttachmentId());
            ShootVO.Media x = new ShootVO.Media();
            x.setId(m.getId());
            x.setAttachmentId(m.getAttachmentId());
            x.setMediaType(m.getMediaType());
            x.setKind(a.getKind());
            x.setFileName(a.getFileName());
            x.setFileSize(a.getFileSize());
            x.setContentType(a.getContentType());
            x.setCreateTime(m.getCreateTime());
            return x;
        }).toList();
    }

    // ---------------------------------------------------------------- 拍摄

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ShootVO addMedia(Long id, AddMediaRequest req) {
        ShootTaskDO t = lock(id);
        requireEditable(t);
        int type = req.getMediaType();
        if (!WarehouseConstants.MEDIA_TYPE_NAMES.containsKey(type)) {
            throw new BizException("素材类型不正确");
        }
        BizAttachmentDO a = attachments.require(req.getAttachmentId());
        boolean video = type != WarehouseConstants.MEDIA_PHOTO;
        if (video && a.getKind() != AttachmentService.VIDEO) {
            throw new BizException(WarehouseConstants.MEDIA_TYPE_NAMES.get(type) + "请上传视频");
        }
        if (!video && a.getKind() != AttachmentService.IMAGE) {
            throw new BizException("实物图请上传图片");
        }
        attachments.attach(AttachmentService.SHOOT, t.getId(), List.of(a.getId()));
        MediaAssetDO m = new MediaAssetDO();
        m.setTenantId(t.getTenantId());
        m.setAssetKey(t.getAssetKey());
        m.setBrand(t.getBrand());
        m.setModel(t.getModel());
        m.setAttachmentId(a.getId());
        m.setMediaType(type);
        m.setTaskId(t.getId());
        mediaMapper.insert(m);
        refreshStatus(t);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ShootVO removeMedia(Long id, Long mediaId) {
        ShootTaskDO t = lock(id);
        requireEditable(t);
        MediaAssetDO m = mediaId == null ? null : mediaMapper.selectById(mediaId);
        if (m == null || m.getDeletedAt() != null || !Objects.equals(m.getTaskId(), t.getId())) {
            throw new BizException("素材不存在");
        }
        m.setDeletedAt(LocalDateTime.now());
        mediaMapper.updateById(m);
        attachments.remove(AttachmentService.SHOOT, t.getId(), m.getAttachmentId());
        refreshStatus(t);
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ShootVO reuse(Long id) {
        ShootTaskDO t = lock(id);
        if (t.getStatus() != WarehouseConstants.SHOOT_PENDING) {
            throw new BizException("只有待拍摄的任务可以复用素材");
        }
        ShootTaskDO src = sources(List.of(t.getAssetKey())).get(t.getAssetKey());
        if (src == null || src.getId().equals(t.getId())) {
            throw new BizException("这个型号还没有可复用的素材");
        }
        if (mediaMapper.selectCount(new LambdaQueryWrapper<MediaAssetDO>()
                .eq(MediaAssetDO::getTaskId, t.getId()).isNull(MediaAssetDO::getDeletedAt)) > 0) {
            throw new BizException("已经上传了素材，请先删除再复用");
        }
        t.setReusedFromTaskId(src.getId());
        t.setStatus(WarehouseConstants.SHOOT_DONE);
        t.setShooterId(currentUser.resolve());
        t.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(t);
        logService.recordOperateLog(WarehouseConstants.MENU_SHOOT, "复用素材", null,
                Map.of("model", t.getBrand() + " " + t.getModel(), "from", src.getId()));
        return detail(id);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ShootVO skip(Long id, String reason) {
        ShootTaskDO t = lock(id);
        if (t.getStatus() != WarehouseConstants.SHOOT_PENDING) {
            throw new BizException("只有待拍摄的任务可以跳过");
        }
        t.setStatus(WarehouseConstants.SHOOT_SKIPPED);
        t.setSkipReason(reason.trim());
        taskMapper.updateById(t);
        logService.recordOperateLog(WarehouseConstants.MENU_SHOOT, "跳过拍摄", null,
                Map.of("model", t.getBrand() + " " + t.getModel(), "reason", t.getSkipReason()));
        return detail(id);
    }

    /** 素材齐全 → 已完成；完成后删到不齐 → 回到待拍摄 */
    private void refreshStatus(ShootTaskDO t) {
        Map<Integer, Long> c = mediaMapper.selectList(new LambdaQueryWrapper<MediaAssetDO>()
                        .select(MediaAssetDO::getMediaType)
                        .eq(MediaAssetDO::getTaskId, t.getId())
                        .isNull(MediaAssetDO::getDeletedAt))
                .stream().collect(Collectors.groupingBy(MediaAssetDO::getMediaType, Collectors.counting()));
        boolean complete = c.getOrDefault(WarehouseConstants.MEDIA_UNBOXING, 0L) >= WarehouseConstants.NEED_VIDEO
                && c.getOrDefault(WarehouseConstants.MEDIA_INSPECTION, 0L) >= WarehouseConstants.NEED_VIDEO
                && c.getOrDefault(WarehouseConstants.MEDIA_PHOTO, 0L) >= WarehouseConstants.NEED_PHOTOS;
        if (complete && t.getStatus() == WarehouseConstants.SHOOT_PENDING) {
            t.setStatus(WarehouseConstants.SHOOT_DONE);
            t.setShooterId(currentUser.resolve());
            t.setCompletedAt(LocalDateTime.now());
            logService.recordOperateLog(WarehouseConstants.MENU_SHOOT, "完成拍摄", null, Map.of("model", t.getBrand() + " " + t.getModel()));
        } else if (!complete && t.getStatus() == WarehouseConstants.SHOOT_DONE) {
            t.setStatus(WarehouseConstants.SHOOT_PENDING);
            t.setCompletedAt(null);
        } else if (t.getShooterId() == null) {
            t.setShooterId(currentUser.resolve());
        }
        // 素材变化也刷新更新时间与更新人
        taskMapper.updateById(t);
    }

    private static void requireEditable(ShootTaskDO t) {
        if (t.getStatus() == WarehouseConstants.SHOOT_SKIPPED) {
            throw new BizException("任务已跳过");
        }
        if (t.getReusedFromTaskId() != null) {
            throw new BizException("这个任务复用了已有素材，不需要再上传");
        }
    }

    /** 复用的任务看被复用任务的素材 */
    private static Long mediaOwner(ShootTaskDO t) {
        return t.getReusedFromTaskId() == null ? t.getId() : t.getReusedFromTaskId();
    }

    private ShootTaskDO visible(Long id) {
        ShootTaskDO t = id == null ? null : taskMapper.selectById(id);
        if (t == null || t.getDeletedAt() != null || !Objects.equals(t.getTenantId(), PiStore.tenantId())) {
            throw new BizException("拍摄任务不存在");
        }
        return t;
    }

    private ShootTaskDO lock(Long id) {
        visible(id);
        taskMapper.lockById(id);
        return visible(id);
    }
}
