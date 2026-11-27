package com.zhul.erp.modules.video.controller;

import com.zhul.erp.common.result.Result;
import com.zhul.erp.common.result.ResultCode;
import com.zhul.erp.framework.tenant.TenantContext;
import com.zhul.erp.modules.video.constants.VideoConstants;
import com.zhul.erp.modules.video.service.VideoGeneratorClient;
import com.zhul.erp.modules.video.service.VideoResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 视频生成服务专用的接口（SecurityConfig 白名单）：不走用户登录，改用共享令牌（请求头 X-Video-Token）校验，
 * 令牌不对的请求拒绝并记录。生成服务从这里取图片、回调结果。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/video-generator/tasks")
@RequiredArgsConstructor
public class VideoGeneratorController {

    private final VideoGeneratorClient client;
    private final VideoResultService results;

    @GetMapping("/{taskId}/images/{attachmentId}")
    public ResponseEntity<?> image(@PathVariable Long taskId, @PathVariable Long attachmentId,
                                   @RequestHeader(value = VideoGeneratorClient.TOKEN_HEADER, required = false) String token) {
        if (!client.tokenMatches(token)) {
            log.warn("视频生成服务取图鉴权失败，taskId={}", taskId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.fail(ResultCode.UNAUTHORIZED));
        }
        VideoResultService.FileStream f = results.image(taskId, attachmentId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(f.contentType())).body(new InputStreamResource(f.in()));
    }

    /**
     * 生成结果：status=success 时带竖屏、横屏两个视频及封面与时长；status=failed 时带失败原因（如「竖屏：配音服务不可用」）
     */
    @PostMapping("/{taskId}/result")
    public ResponseEntity<Result<Void>> result(@PathVariable Long taskId,
                                               @RequestHeader(value = VideoGeneratorClient.TOKEN_HEADER, required = false) String token,
                                               @RequestParam int version, @RequestParam String status,
                                               @RequestParam(required = false) String error,
                                               @RequestPart(value = "portrait", required = false) MultipartFile portrait,
                                               @RequestPart(value = "portraitCover", required = false) MultipartFile portraitCover,
                                               @RequestParam(defaultValue = "0") int portraitDuration,
                                               @RequestPart(value = "landscape", required = false) MultipartFile landscape,
                                               @RequestPart(value = "landscapeCover", required = false) MultipartFile landscapeCover,
                                               @RequestParam(defaultValue = "0") int landscapeDuration) {
        if (!client.tokenMatches(token)) {
            log.warn("视频生成服务回调鉴权失败，taskId={}", taskId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Result.fail(ResultCode.UNAUTHORIZED));
        }
        Integer tenantId = results.tenantOf(taskId);
        if (tenantId == null) {
            log.warn("视频生成服务回调的任务不存在，taskId={}", taskId);
            return ResponseEntity.ok(Result.ok());
        }
        TenantContext.runAs(tenantId, () -> {
            if ("success".equals(status)) {
                results.success(taskId, version, Map.of(
                        VideoConstants.PORTRAIT, new VideoResultService.Part(VideoConstants.PORTRAIT, portrait, portraitCover, portraitDuration),
                        VideoConstants.LANDSCAPE, new VideoResultService.Part(VideoConstants.LANDSCAPE, landscape, landscapeCover, landscapeDuration)));
            } else {
                results.fail(taskId, version, error);
            }
        });
        return ResponseEntity.ok(Result.ok());
    }
}
