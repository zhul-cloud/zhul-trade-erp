package com.zhul.erp.modules.video.support;

import com.zhul.erp.modules.video.constants.VideoConstants;
import com.zhul.erp.modules.video.dto.VideoScript;

import java.util.Collection;
import java.util.Optional;

/**
 * 脚本校验与时长估算（spec「视频脚本」）：按字幕词数、每秒约 2.5 个词估算，总时长要在 15–20 秒。
 * 估算按四舍五入保留一位小数，只用于显示和校验，不涉及金额。
 */
public final class ScriptRules {

    private ScriptRules() {
    }

    public static int words(String caption) {
        if (caption == null || caption.isBlank()) {
            return 0;
        }
        return caption.trim().split("\\s+").length;
    }

    public static double seconds(String caption) {
        return Math.round(words(caption) / VideoConstants.WORDS_PER_SECOND * 10) / 10.0;
    }

    public static double totalSeconds(VideoScript s) {
        if (s == null || s.getShots() == null) {
            return 0;
        }
        int total = s.getShots().stream().mapToInt(x -> words(x.getCaption())).sum();
        return Math.round(total / VideoConstants.WORDS_PER_SECOND * 10) / 10.0;
    }

    /** 第一个不符合要求的地方；全部符合时为空。usable 为任务里选用的图片附件 */
    public static Optional<String> problem(VideoScript s, Collection<Long> usable) {
        if (s == null || s.getShots() == null || s.getShots().isEmpty()) {
            return Optional.of("还没有脚本");
        }
        if (s.getTitle() == null || s.getTitle().isBlank()) {
            return Optional.of("请填写视频标题");
        }
        if (s.getTitle().trim().length() > VideoConstants.MAX_TITLE) {
            return Optional.of("标题不能超过 " + VideoConstants.MAX_TITLE + " 个字符");
        }
        int n = s.getShots().size();
        if (n < VideoConstants.MIN_SHOTS || n > VideoConstants.MAX_SHOTS) {
            return Optional.of("镜头 " + n + " 个，需要 " + VideoConstants.MIN_SHOTS + "–" + VideoConstants.MAX_SHOTS + " 个");
        }
        for (int i = 0; i < n; i++) {
            VideoScript.Shot shot = s.getShots().get(i);
            if (shot.getAttachmentId() == null || !usable.contains(shot.getAttachmentId())) {
                return Optional.of("镜头 " + (i + 1) + " 的图片不在选用的图片里");
            }
            if (shot.getCaption() == null || shot.getCaption().isBlank()) {
                return Optional.of("镜头 " + (i + 1) + " 没有字幕");
            }
            if (shot.getCaption().trim().length() > VideoConstants.MAX_CAPTION) {
                return Optional.of("镜头 " + (i + 1) + " 的字幕超过 " + VideoConstants.MAX_CAPTION + " 个字符");
            }
        }
        double total = totalSeconds(s);
        if (total < VideoConstants.MIN_SECONDS || total > VideoConstants.MAX_SECONDS) {
            return Optional.of("估算时长 " + fmt(total) + " 秒，需要 15–20 秒，请" + (total > VideoConstants.MAX_SECONDS ? "精简字幕" : "补充字幕"));
        }
        return Optional.empty();
    }

    public static String fmt(double seconds) {
        return seconds == Math.floor(seconds) ? String.valueOf((long) seconds) : String.valueOf(seconds);
    }
}
