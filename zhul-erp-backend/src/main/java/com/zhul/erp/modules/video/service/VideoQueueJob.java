package com.zhul.erp.modules.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.framework.tenant.TenantScope;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import com.zhul.erp.modules.attachment.repository.BizAttachmentMapper;
import com.zhul.erp.modules.video.constants.VideoConstants;
import com.zhul.erp.modules.video.dto.VideoScript;
import com.zhul.erp.modules.video.entity.VideoTaskDO;
import com.zhul.erp.modules.video.repository.VideoTaskMapper;
import com.zhul.erp.modules.video.support.ScriptRules;
import com.zhul.erp.modules.video.support.VideoSettings;
import com.zhul.erp.modules.video.support.VideoWindow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 视频生成队列（design 决策 2）：每分钟一次，全服务器所有公司一个队列。
 * 先把超过 15 分钟没有结果的「生成中」标为失败；只在夜间时段内、没有生成中的任务、当晚没有暂停时，
 * 按加入队列的先后取一个任务提交给生成服务。认领用一条带条件的 UPDATE 完成，保证同一时间只有一个生成中。
 * 生成服务不可用时任务退回排队，10 分钟后重试；当晚连续 3 次不可用后暂停到时段结束。
 * 没有登录上下文：读队列在 TenantScope.platform 内，按任务写回时用 TenantContext.runAs(任务的租户)。
 */
@Slf4j
@Component
public class VideoQueueJob {

    private static final String UNAVAILABLE_KEY = "zhul:erp:video:unavailable";

    private final VideoTaskMapper taskMapper;
    private final BizAttachmentMapper attachmentMapper;
    private final VideoTaskService tasks;
    private final VideoResultService results;
    private final VideoGeneratorClient client;
    private final VideoSettings settings;
    private final StringRedisTemplate redis;
    private final String callbackBase;

    public VideoQueueJob(VideoTaskMapper taskMapper, BizAttachmentMapper attachmentMapper, VideoTaskService tasks,
                         VideoResultService results, VideoGeneratorClient client, VideoSettings settings,
                         StringRedisTemplate redis, @Value("${zhul.ai-task.callback-base-url}") String callbackBase) {
        this.taskMapper = taskMapper;
        this.attachmentMapper = attachmentMapper;
        this.tasks = tasks;
        this.results = results;
        this.client = client;
        this.settings = settings;
        this.redis = redis;
        this.callbackBase = callbackBase.replaceAll("/+$", "");
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void tick() {
        if (!client.enabled()) {
            return;
        }
        TenantScope.platform(() -> {
            LocalDateTime now = LocalDateTime.now();
            expire(now);
            dispatch(settings.window(), now);
        });
    }

    /** 生成中超过 15 分钟的标为失败 */
    void expire(LocalDateTime now) {
        List<VideoTaskDO> stale = taskMapper.selectList(new LambdaQueryWrapper<VideoTaskDO>()
                .eq(VideoTaskDO::getStatus, VideoConstants.STATUS_GENERATING)
                .lt(VideoTaskDO::getStartedAt, now.minusMinutes(VideoConstants.TIMEOUT_MINUTES))
                .isNull(VideoTaskDO::getDeletedAt));
        for (VideoTaskDO t : stale) {
            TenantContext.runAs(t.getTenantId(), () -> results.fail(t.getId(), t.getCurrentVersion(), "生成超时"));
        }
    }

    /** 时段内、没有暂停时派发一个任务 */
    void dispatch(VideoWindow window, LocalDateTime now) {
        if (!window.contains(now.toLocalTime()) || Boolean.TRUE.equals(redis.hasKey(VideoConstants.PAUSE_KEY))) {
            return;
        }
        VideoTaskDO next = taskMapper.selectOne(new LambdaQueryWrapper<VideoTaskDO>()
                .eq(VideoTaskDO::getStatus, VideoConstants.STATUS_QUEUED)
                .isNull(VideoTaskDO::getDeletedAt)
                .and(w -> w.isNull(VideoTaskDO::getNextTryAt).or().le(VideoTaskDO::getNextTryAt, now))
                .orderByAsc(VideoTaskDO::getQueuedAt)
                .orderByAsc(VideoTaskDO::getId)
                .last("LIMIT 1"));
        if (next == null || taskMapper.claim(next.getId(), now) == 0) {
            return;
        }
        int version = next.getCurrentVersion() + 1;
        TenantContext.runAs(next.getTenantId(), () -> submit(next, version, window, now));
    }

    private void submit(VideoTaskDO t, int version, VideoWindow window, LocalDateTime now) {
        VideoGeneratorClient.Submit r = client.submit(request(t, version));
        if (r.outcome() == VideoGeneratorClient.Outcome.ACCEPTED) {
            redis.delete(UNAVAILABLE_KEY);
            t.setCurrentVersion(version);
            tasks.addLog(t, VideoConstants.LOG_START, version, "");
            log.info("视频任务开始生成，taskId={}，version={}", t.getId(), version);
            return;
        }
        // 没有提交成功：退回排队，版本号还原
        boolean busy = r.outcome() == VideoGeneratorClient.Outcome.BUSY;
        taskMapper.update(null, new LambdaUpdateWrapper<VideoTaskDO>()
                .set(VideoTaskDO::getStatus, VideoConstants.STATUS_QUEUED)
                .set(VideoTaskDO::getStartedAt, null)
                .set(VideoTaskDO::getCurrentVersion, version - 1)
                .set(VideoTaskDO::getNextTryAt, busy ? now.plusMinutes(1) : now.plusMinutes(VideoConstants.RETRY_MINUTES))
                .eq(VideoTaskDO::getId, t.getId())
                .eq(VideoTaskDO::getStatus, VideoConstants.STATUS_GENERATING));
        if (busy) {
            log.info("视频生成服务正忙，taskId={} 1 分钟后重试", t.getId());
            return;
        }
        Long failures = redis.opsForValue().increment(UNAVAILABLE_KEY);
        redis.expire(UNAVAILABLE_KEY, Duration.ofHours(12));
        log.warn("视频生成服务不可用（第 {} 次），taskId={} {} 分钟后重试：{}", failures, t.getId(), VideoConstants.RETRY_MINUTES, r.message());
        if (failures != null && failures >= VideoConstants.MAX_UNAVAILABLE) {
            Duration rest = Duration.between(now, window.currentEnd(now));
            redis.opsForValue().set(VideoConstants.PAUSE_KEY, "1", rest.isNegative() || rest.isZero() ? Duration.ofMinutes(1) : rest);
            redis.delete(UNAVAILABLE_KEY);
            log.error("视频生成服务连续 {} 次不可用，暂停今晚的队列", failures);
        }
    }

    /** 选用的图片按顺序给出下载地址（生成服务带令牌回来取）；镜头里的图片换成下标 */
    private VideoGeneratorClient.Request request(VideoTaskDO t, int version) {
        VideoScript script = tasks.readScript(t);
        List<Long> used = tasks.usedAttachments(t.getId());
        List<VideoGeneratorClient.Image> images = new ArrayList<>();
        for (Long id : used) {
            BizAttachmentDO a = attachmentMapper.selectById(id);
            if (a != null && a.getDeletedAt() == null) {
                images.add(new VideoGeneratorClient.Image(id, callbackBase + "/api/v1/video-generator/tasks/" + t.getId() + "/images/" + id));
            }
        }
        List<Long> ids = images.stream().map(VideoGeneratorClient.Image::id).toList();
        List<VideoGeneratorClient.Shot> shots = new ArrayList<>();
        if (script != null) {
            for (VideoScript.Shot s : script.getShots()) {
                shots.add(new VideoGeneratorClient.Shot(ids.indexOf(s.getAttachmentId()), s.getCaption(), ScriptRules.seconds(s.getCaption())));
            }
        }
        return new VideoGeneratorClient.Request(t.getId(), version, script == null ? "" : script.getTitle(), images, shots,
                List.of(VideoConstants.PORTRAIT, VideoConstants.LANDSCAPE), "en-US-GuyNeural", 1.1,
                callbackBase + "/api/v1/video-generator/tasks/" + t.getId() + "/result");
    }
}
