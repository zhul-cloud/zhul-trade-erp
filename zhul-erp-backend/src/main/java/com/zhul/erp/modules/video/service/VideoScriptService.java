package com.zhul.erp.modules.video.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.aitask.constants.AiTaskStatus;
import com.zhul.erp.modules.aitask.dto.AiTaskVO;
import com.zhul.erp.modules.aitask.entity.AiTaskDO;
import com.zhul.erp.modules.aitask.service.AiTaskService;
import com.zhul.erp.modules.attachment.entity.BizAttachmentDO;
import com.zhul.erp.modules.attachment.repository.BizAttachmentMapper;
import com.zhul.erp.modules.inquiry.support.CurrentUserResolver;
import com.zhul.erp.modules.video.constants.VideoConstants;
import com.zhul.erp.modules.video.dto.VideoScript;
import com.zhul.erp.modules.video.dto.VideoTaskDetailVO;
import com.zhul.erp.modules.video.entity.VideoTaskDO;
import com.zhul.erp.modules.video.repository.VideoTaskMapper;
import com.zhul.erp.modules.video.support.ScriptRules;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分镜脚本由 AI 编排服务的 video-script 技能生成（design 决策 4）：输入商品事实、社媒视频方案和选用图片的顺序，
 * 输出标题和镜头（图片序号 + 一句英文字幕）。结果回来后按选用图片换成附件 ID，并校验镜头数和估算时长。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VideoScriptService {

    private final VideoTaskService tasks;
    private final VideoTaskMapper taskMapper;
    private final BizAttachmentMapper attachmentMapper;
    private final VideoFacts facts;
    private final AiTaskService aiTaskService;
    private final CurrentUserResolver currentUser;
    private final ObjectMapper objectMapper;

    /**
     * 请求 AI 生成脚本。onlyIfMissing 为 true 时只在还没有脚本时生成（上传完图片后自动生成），
     * 否则为运营点「重新生成脚本」，覆盖现有脚本。
     */
    @Transactional(rollbackFor = Exception.class)
    public VideoTaskDetailVO generate(Long id, boolean onlyIfMissing) {
        VideoTaskDO t = tasks.require(id);
        if (onlyIfMissing && t.getScriptStatus() != VideoConstants.SCRIPT_NONE) {
            return tasks.detail(id);
        }
        tasks.requireEditable(t);
        if (t.getScriptStatus() == VideoConstants.SCRIPT_RUNNING) {
            throw new BizException("脚本正在生成，请稍候");
        }
        List<Long> used = tasks.usedAttachments(id);
        if (used.isEmpty()) {
            throw new BizException("请先上传并选用图片");
        }
        Map<Long, BizAttachmentDO> atts = attachmentMapper.selectBatchIds(used).stream()
                .collect(Collectors.toMap(BizAttachmentDO::getId, Function.identity()));
        List<Map<String, Object>> images = new ArrayList<>();
        for (int i = 0; i < used.size(); i++) {
            BizAttachmentDO a = atts.get(used.get(i));
            Map<String, Object> img = new LinkedHashMap<>();
            img.put("index", i + 1);
            img.put("fileName", a == null ? "" : a.getFileName());
            images.add(img);
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("product", facts.of(t.getProductId(), t.getBrand(), t.getModel()));
        input.put("factsUnverified", facts.unverified(t.getProductId()));
        input.put("images", images);
        input.put("rules", Map.of("minShots", VideoConstants.MIN_SHOTS, "maxShots", VideoConstants.MAX_SHOTS,
                "maxTitle", VideoConstants.MAX_TITLE, "maxCaption", VideoConstants.MAX_CAPTION,
                "wordsPerSecond", VideoConstants.WORDS_PER_SECOND, "minSeconds", VideoConstants.MIN_SECONDS,
                "maxSeconds", VideoConstants.MAX_SECONDS));
        String json;
        try {
            json = objectMapper.writeValueAsString(input);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("脚本请求序列化失败", e);
        }
        AiTaskVO ai = aiTaskService.createAndSubmit(VideoConstants.AI_SKILL, json, currentUser.resolve());
        taskMapper.update(null, new LambdaUpdateWrapper<VideoTaskDO>()
                .set(VideoTaskDO::getScriptStatus, VideoConstants.SCRIPT_RUNNING)
                .set(VideoTaskDO::getScriptAiTaskId, ai.getId())
                .set(VideoTaskDO::getScriptError, "")
                .set(VideoTaskDO::getUpdateTime, LocalDateTime.now())
                .set(VideoTaskDO::getUpdateBy, VideoTaskService.operator())
                .eq(VideoTaskDO::getId, id));
        return tasks.detail(id);
    }

    /** AI 任务结束（回调或超时）：按租户上下文执行（由 AiTaskServiceImpl 保证） */
    @Transactional(rollbackFor = Exception.class)
    public void onResult(AiTaskDO ai) {
        VideoTaskDO t = taskMapper.selectOne(new LambdaQueryWrapper<VideoTaskDO>()
                .eq(VideoTaskDO::getScriptAiTaskId, ai.getId())
                .isNull(VideoTaskDO::getDeletedAt)
                .last("LIMIT 1"));
        if (t == null || t.getScriptStatus() != VideoConstants.SCRIPT_RUNNING) {
            log.info("视频脚本结果已过期，丢弃，aiTaskId={}", ai.getId());
            return;
        }
        VideoTaskDO upd = new VideoTaskDO();
        upd.setId(t.getId());
        if (!Objects.equals(ai.getStatus(), AiTaskStatus.COMPLETED)) {
            upd.setScriptStatus(VideoConstants.SCRIPT_FAILED);
            String err = ai.getErrorMessage() == null || ai.getErrorMessage().isBlank() ? "AI 生成失败" : ai.getErrorMessage();
            upd.setScriptError(("AI 生成脚本失败：" + err + "，可以手写脚本").substring(0, Math.min(300, ("AI 生成脚本失败：" + err + "，可以手写脚本").length())));
            taskMapper.updateById(upd);
            return;
        }
        List<Long> used = tasks.usedAttachments(t.getId());
        VideoScript script = toScript(ai.getOutput(), used);
        upd.setScript(tasks.writeScript(script));
        upd.setScriptSource(VideoConstants.SCRIPT_SOURCE_AI);
        upd.setScriptStatus(VideoConstants.SCRIPT_READY);
        upd.setScriptError(ScriptRules.problem(script, used).map(p -> "脚本需修改：" + p).orElse(""));
        taskMapper.updateById(upd);
    }

    /** AI 输出 {title, shots:[{image: 图片序号（从 1 起）, caption}]} 转成脚本；序号超出选用图片的镜头丢弃 */
    VideoScript toScript(String output, List<Long> used) {
        VideoScript s = new VideoScript();
        JsonNode root;
        try {
            root = objectMapper.readTree(output == null ? "{}" : output);
        } catch (JsonProcessingException e) {
            log.warn("视频脚本 AI 结果不是 JSON：{}", e.getOriginalMessage());
            return s;
        }
        String title = root.path("title").asText("").trim();
        s.setTitle(title.length() > VideoConstants.MAX_TITLE ? title.substring(0, VideoConstants.MAX_TITLE) : title);
        for (JsonNode n : root.path("shots")) {
            int idx = n.path("image").asInt(0);
            if (idx < 1 || idx > used.size()) {
                continue;
            }
            VideoScript.Shot shot = new VideoScript.Shot();
            shot.setAttachmentId(used.get(idx - 1));
            shot.setCaption(n.path("caption").asText("").trim());
            s.getShots().add(shot);
        }
        return s;
    }
}
