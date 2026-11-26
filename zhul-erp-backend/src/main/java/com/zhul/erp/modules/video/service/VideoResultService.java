package com.zhul.erp.modules.video.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.StorageArea;
import com.zhul.erp.framework.storage.UploadRules;
import com.zhul.erp.framework.storage.VideoTranscoder;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import com.zhul.erp.modules.attachment.repository.BizAttachmentMapper;
import com.zhul.erp.modules.video.constants.VideoConstants;
import com.zhul.erp.modules.video.entity.VideoOutputDO;
import com.zhul.erp.modules.video.entity.VideoTaskDO;
import com.zhul.erp.modules.video.repository.VideoOutputMapper;
import com.zhul.erp.modules.video.repository.VideoTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 生成服务的回调与取图（design 决策 1、2）：生成服务没有登录上下文，先在 TenantScope.platform 内按任务号找到任务，
 * 再用 TenantContext.runAs(任务的租户) 写回。任务已经不是「生成中」或版本号对不上（超时后才到的回调）时丢弃。
 * 事务边界：一次回调一个事务（两个画幅的结果、附件、任务状态一起提交）；文件先写存储，库写失败时文件成为孤儿。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoResultService {

    private static final long VIDEO_MAX = 95L * 1024 * 1024;
    private static final long COVER_MAX = 10L * 1024 * 1024;

    private final VideoTaskMapper taskMapper;
    private final VideoOutputMapper outputMapper;
    private final BizAttachmentMapper attachmentMapper;
    private final FileStorageService files;
    private final VideoTaskService tasks;

    /** 一个画幅的结果 */
    public record Part(String aspect, MultipartFile video, MultipartFile cover, int durationSec) {
    }

    /** 任务所属租户；任务不存在时为空 */
    public Integer tenantOf(Long taskId) {
        VideoTaskDO t = TenantScope.platform(() -> taskMapper.selectById(taskId));
        return t == null || t.getDeletedAt() != null ? null : t.getTenantId();
    }

    /** 生成服务取一张选用的图片：只能是这个任务正在生成时的图片 */
    public FileStream image(Long taskId, Long attachmentId) {
        Integer tenantId = tenantOf(taskId);
        if (tenantId == null) {
            throw new BizException("任务不存在");
        }
        AtomicReference<FileStream> out = new AtomicReference<>();
        TenantContext.runAs(tenantId, () -> {
            VideoTaskDO t = taskMapper.selectById(taskId);
            BizAttachmentDO a = attachmentMapper.selectById(attachmentId);
            if (t.getStatus() != VideoConstants.STATUS_GENERATING || a == null || a.getDeletedAt() != null
                    || !VideoConstants.OWNER_TASK.equals(a.getOwnerType()) || !Objects.equals(a.getOwnerId(), taskId)) {
                throw new BizException("图片不存在");
            }
            out.set(new FileStream(files.open(a.getStorage(), a.getFileKey()), a.getContentType()));
        });
        return out.get();
    }

    public record FileStream(InputStream in, String contentType) {
    }

    /** 两个画幅都成功：保存视频与封面，任务变为待审核 */
    @Transactional(rollbackFor = Exception.class)
    public void success(Long taskId, int version, Map<String, Part> parts) {
        VideoTaskDO t = current(taskId, version);
        if (t == null) {
            return;
        }
        for (String aspect : new String[]{VideoConstants.PORTRAIT, VideoConstants.LANDSCAPE}) {
            Part p = parts.get(aspect);
            if (p == null || p.video() == null || p.video().isEmpty()) {
                throw new BizException("缺少" + aspectName(aspect) + "视频");
            }
        }
        for (String aspect : new String[]{VideoConstants.PORTRAIT, VideoConstants.LANDSCAPE}) {
            Part p = parts.get(aspect);
            VideoOutputDO o = new VideoOutputDO();
            o.setTenantId(t.getTenantId());
            o.setTaskId(taskId);
            o.setVersion(version);
            o.setAspect(aspect);
            o.setVideoAttachmentId(0L);
            o.setCoverAttachmentId(0L);
            o.setDurationSec(Math.max(0, p.durationSec()));
            o.setStatus(VideoConstants.OUTPUT_REVIEW);
            outputMapper.insert(o);
            VideoOutputDO upd = new VideoOutputDO();
            upd.setId(o.getId());
            upd.setVideoAttachmentId(store(t, o.getId(), p.video(), Set.of("mp4"), VIDEO_MAX, 2));
            if (p.cover() != null && !p.cover().isEmpty()) {
                upd.setCoverAttachmentId(store(t, o.getId(), p.cover(), Set.of("jpg", "png", "webp"), COVER_MAX, 1));
            }
            outputMapper.updateById(upd);
        }
        finish(t, VideoConstants.STATUS_REVIEW, "");
        tasks.addLog(t, VideoConstants.LOG_DONE, version, "");
        log.info("视频任务生成完成，taskId={}，version={}", taskId, version);
    }

    /** 生成失败（生成服务报错，或超时）：记录原因 */
    @Transactional(rollbackFor = Exception.class)
    public void fail(Long taskId, int version, String reason) {
        VideoTaskDO t = current(taskId, version);
        if (t == null) {
            return;
        }
        String r = reason == null || reason.isBlank() ? "生成失败" : reason.trim();
        r = r.length() > 300 ? r.substring(0, 300) : r;
        finish(t, VideoConstants.STATUS_FAILED, r);
        tasks.addLog(t, VideoConstants.LOG_FAIL, version, r);
        log.warn("视频任务生成失败，taskId={}，version={}：{}", taskId, version, r);
    }

    /** 仍在生成这个版本的任务；否则为迟到的回调，丢弃 */
    private VideoTaskDO current(Long taskId, int version) {
        VideoTaskDO t = taskMapper.selectById(taskId);
        if (t == null || t.getDeletedAt() != null || t.getStatus() != VideoConstants.STATUS_GENERATING
                || !Objects.equals(t.getCurrentVersion(), version)) {
            log.warn("视频任务回调已过期，丢弃，taskId={}，version={}", taskId, version);
            return null;
        }
        return t;
    }

    private void finish(VideoTaskDO t, int status, String reason) {
        LocalDateTime now = LocalDateTime.now();
        taskMapper.update(null, new LambdaUpdateWrapper<VideoTaskDO>()
                .set(VideoTaskDO::getStatus, status)
                .set(VideoTaskDO::getFinishedAt, now)
                .set(VideoTaskDO::getFailReason, reason)
                .set(VideoTaskDO::getQueuedAt, null)
                .set(VideoTaskDO::getNextTryAt, null)
                .set(VideoTaskDO::getUpdateTime, now)
                .set(VideoTaskDO::getUpdateBy, "sys")
                .eq(VideoTaskDO::getId, t.getId())
                .eq(VideoTaskDO::getStatus, VideoConstants.STATUS_GENERATING));
    }

    private Long store(VideoTaskDO t, Long outputId, MultipartFile file, Set<String> exts, long max, int kind) {
        String ext = UploadRules.check(file, exts, max, kind == 2 ? "视频只支持 MP4" : "封面只支持 JPG、PNG、WEBP");
        FileStorageService.StoredFile s = files.storeForModel(StorageArea.MEDIA, t.getTenantId(), t.getBrand(), t.getModel(), file, ext);
        BizAttachmentDO a = new BizAttachmentDO();
        a.setTenantId(t.getTenantId());
        a.setOwnerType(VideoConstants.OWNER_OUTPUT);
        a.setOwnerId(outputId);
        a.setKind(kind);
        a.setStorage(s.storage());
        a.setFileKey(s.fileKey());
        a.setUrl("");
        a.setFileName(s.fileName());
        a.setFileSize(s.fileSize());
        a.setContentType(s.contentType());
        a.setProcessingStatus(VideoTranscoder.NONE);
        a.setUploadedBy(0L);
        attachmentMapper.insert(a);
        return a.getId();
    }

    static String aspectName(String aspect) {
        return VideoConstants.PORTRAIT.equals(aspect) ? "竖屏" : "横屏";
    }
}
