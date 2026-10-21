package com.zhul.erp.modules.warehouse.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.common.result.PageResult;
import com.zhul.erp.modules.attachment.dto.AttachmentVO;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import com.zhul.erp.modules.attachment.service.AttachmentService;
import com.zhul.erp.modules.media.service.MediaIndexer;
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
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptDO;
import com.zhul.erp.modules.warehouse.entity.PurchaseReceiptItemDO;
import com.zhul.erp.modules.warehouse.entity.ShootTaskDO;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 实拍任务（营销拍摄）：每个型号拍一次 6 张营销图，拍好的图进素材库「营销实拍」，以后同型号一直可以用。
 * 采购入库时，为还没有营销实拍、也没有待拍摄任务的合格入库型号生成（同一次入库按型号去重）。验货证据在验收入库时上传，不在这里。
 * 写操作用读已提交：同一任务并发上传多张图时，拿到行锁后要看到别人已提交的图片，才能判定是否够 6 张。
 */
@Service
@RequiredArgsConstructor
public class ShootService {

    private final ShootTaskMapper taskMapper;
    private final PurchaseReceiptMapper receiptMapper;
    private final SalesOrderMapper soMapper;
    private final AttachmentService attachments;
    private final MediaIndexer media;
    private final CurrentUserResolver currentUser;
    private final InquiryLookups lookups;
    private final LogService logService;

    /** 采购入库确认时调用（调用方事务内）：还没有营销实拍、也没有待拍摄任务的合格入库型号，每个型号一条任务 */
    void createForReceipt(PurchaseReceiptDO receipt, List<PurchaseReceiptItemDO> items, Map<Long, Long> soIdByPoItem) {
        Map<String, PurchaseReceiptItemDO> byKey = new LinkedHashMap<>();
        for (PurchaseReceiptItemDO i : items) {
            if (i.getQualifiedQty() > 0) {
                byKey.putIfAbsent(ReceivingSupport.assetKey(i.getBrand(), i.getModel()), i);
            }
        }
        if (byKey.isEmpty()) {
            return;
        }
        Set<String> missing = new HashSet<>();
        byKey.forEach((k, i) -> {
            if (!media.missingMarketing(receipt.getTenantId(), List.of(new MediaIndexer.ModelRef(i.getBrand(), i.getModel()))).isEmpty()) {
                missing.add(k);
            }
        });
        Set<String> pending = taskMapper.selectList(new LambdaQueryWrapper<ShootTaskDO>()
                        .select(ShootTaskDO::getAssetKey)
                        .eq(ShootTaskDO::getTenantId, receipt.getTenantId())
                        .in(ShootTaskDO::getAssetKey, byKey.keySet())
                        .eq(ShootTaskDO::getStatus, WarehouseConstants.SHOOT_PENDING)
                        .isNull(ShootTaskDO::getDeletedAt)).stream()
                .map(ShootTaskDO::getAssetKey).collect(Collectors.toSet());
        byKey.forEach((key, i) -> {
            if (!missing.contains(key) || pending.contains(key)) {
                return;
            }
            ShootTaskDO t = new ShootTaskDO();
            t.setTenantId(receipt.getTenantId());
            t.setReceiptId(receipt.getId());
            t.setReceiptItemId(i.getId());
            t.setSoId(Objects.requireNonNullElse(soIdByPoItem.get(i.getPoItemId()), 0L));
            t.setModel(i.getModel());
            t.setBrand(i.getBrand());
            t.setCategory(i.getCategory());
            t.setAssetKey(key);
            t.setStatus(WarehouseConstants.SHOOT_PENDING);
            t.setSkipReason("");
            taskMapper.insert(t);
        });
    }

    /** 采购入库单生成的任务都还没开始（待拍摄、没有图片）时返回 true */
    boolean notStarted(Long receiptId) {
        List<ShootTaskDO> tasks = tasksOfReceipt(receiptId);
        if (tasks.stream().anyMatch(t -> t.getStatus() != WarehouseConstants.SHOOT_PENDING)) {
            return false;
        }
        return tasks.isEmpty() || attachments.counts(AttachmentService.SHOOT, tasks.stream().map(ShootTaskDO::getId).toList())
                .values().stream().allMatch(n -> n == 0);
    }

    /** 冲销入库时作废它生成的实拍任务（调用方事务内） */
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
        vo.setMedia(attachments.listOf(AttachmentService.SHOOT, mediaOwner(t)).stream().map(ShootService::toMedia).toList());
        return vo;
    }

    private static ShootVO.Media toMedia(AttachmentVO a) {
        ShootVO.Media x = new ShootVO.Media();
        x.setId(a.getId());
        x.setAttachmentId(a.getId());
        x.setMediaType(WarehouseConstants.MEDIA_PHOTO);
        x.setKind(a.getKind());
        x.setFileName(a.getFileName());
        x.setFileSize(a.getFileSize());
        x.setContentType(a.getContentType());
        x.setCreateTime(a.getCreateTime());
        return x;
    }

    private List<ShootListVO> toListVos(List<ShootTaskDO> rows) {
        Map<Long, Long> counts = attachments.counts(AttachmentService.SHOOT, rows.stream().map(ShootService::mediaOwner).distinct().toList());
        Map<Long, String> grNos = new HashMap<>();
        ReceivingSupport.byId(rows.stream().map(ShootTaskDO::getReceiptId).toList(), receiptMapper::selectBatchIds, PurchaseReceiptDO::getId)
                .forEach((k, v) -> grNos.put(k, v.getGrNo()));
        Map<Long, String> soNos = new HashMap<>();
        ReceivingSupport.byId(rows.stream().map(ShootTaskDO::getSoId).filter(x -> x != null && x > 0).toList(),
                soMapper::selectBatchIds, SalesOrderDO::getId).forEach((k, v) -> soNos.put(k, v.getSoNo()));
        Map<Long, String> users = lookups.userNames(rows.stream().map(ShootTaskDO::getShooterId).toList());
        List<ShootListVO> out = new ArrayList<>(rows.size());
        for (ShootTaskDO t : rows) {
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
            vo.setPhotoCount(counts.getOrDefault(mediaOwner(t), 0L).intValue());
            vo.setNeedPhotos(WarehouseConstants.NEED_PHOTOS);
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

    // ---------------------------------------------------------------- 拍摄

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ShootVO addMedia(Long id, AddMediaRequest req) {
        ShootTaskDO t = lock(id);
        requireEditable(t);
        BizAttachmentDO a = attachments.require(req.getAttachmentId());
        if (a.getKind() != AttachmentService.IMAGE) {
            throw new BizException("营销图请上传图片");
        }
        attachments.attach(AttachmentService.SHOOT, t.getId(), List.of(a.getId()));
        media.indexShoot(t.getId(), t.getTenantId(), t.getBrand(), t.getModel());
        refreshStatus(t);
        return detail(id);
    }

    /** 删除一张营销图：attachmentId 为图片的附件 ID */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public ShootVO removeMedia(Long id, Long attachmentId) {
        ShootTaskDO t = lock(id);
        requireEditable(t);
        attachments.remove(AttachmentService.SHOOT, t.getId(), attachmentId);
        media.indexShoot(t.getId(), t.getTenantId(), t.getBrand(), t.getModel());
        refreshStatus(t);
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

    /** 营销图够 6 张 → 已完成；完成后删到不足 → 回到待拍摄 */
    private void refreshStatus(ShootTaskDO t) {
        long photos = attachments.counts(AttachmentService.SHOOT, List.of(t.getId())).getOrDefault(t.getId(), 0L);
        boolean complete = photos >= WarehouseConstants.NEED_PHOTOS;
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
        // 图片变化也刷新更新时间与更新人
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

    /** 拆分前复用了其他任务素材的历史任务，显示被复用任务的图片 */
    private static Long mediaOwner(ShootTaskDO t) {
        return t.getReusedFromTaskId() == null ? t.getId() : t.getReusedFromTaskId();
    }

    private ShootTaskDO visible(Long id) {
        ShootTaskDO t = id == null ? null : taskMapper.selectById(id);
        if (t == null || t.getDeletedAt() != null || !Objects.equals(t.getTenantId(), PiStore.tenantId())) {
            throw new BizException("实拍任务不存在");
        }
        return t;
    }

    private ShootTaskDO lock(Long id) {
        visible(id);
        taskMapper.lockById(id);
        return visible(id);
    }
}
