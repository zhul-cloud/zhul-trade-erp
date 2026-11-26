package com.zhul.erp.modules.video.constants;

import java.util.Map;

/** 视频任务常量（spec product/video-task） */
public final class VideoConstants {

    private VideoConstants() {
    }

    public static final int STATUS_PENDING = 1;
    public static final int STATUS_QUEUED = 2;
    public static final int STATUS_GENERATING = 3;
    public static final int STATUS_REVIEW = 4;
    public static final int STATUS_APPROVED = 5;
    public static final int STATUS_REJECTED = 6;
    public static final int STATUS_FAILED = 7;

    public static final Map<Integer, String> STATUS_NAMES = Map.of(STATUS_PENDING, "待生成", STATUS_QUEUED, "排队中",
            STATUS_GENERATING, "生成中", STATUS_REVIEW, "待审核", STATUS_APPROVED, "已通过", STATUS_REJECTED, "已退回",
            STATUS_FAILED, "生成失败");

    public static final int SCRIPT_NONE = 0;
    public static final int SCRIPT_RUNNING = 1;
    public static final int SCRIPT_READY = 2;
    public static final int SCRIPT_FAILED = 3;

    public static final int SCRIPT_SOURCE_AI = 1;
    public static final int SCRIPT_SOURCE_MANUAL = 2;

    public static final int OUTPUT_REVIEW = 1;
    public static final int OUTPUT_APPROVED = 2;
    public static final int OUTPUT_REPLACED = 3;
    public static final int OUTPUT_REJECTED = 4;

    public static final String PORTRAIT = "9:16";
    public static final String LANDSCAPE = "16:9";

    public static final String LOG_QUEUE = "QUEUE";
    public static final String LOG_DEQUEUE = "DEQUEUE";
    public static final String LOG_START = "START";
    public static final String LOG_DONE = "DONE";
    public static final String LOG_FAIL = "FAIL";
    public static final String LOG_APPROVE = "APPROVE";
    public static final String LOG_REJECT = "REJECT";
    public static final String SYSTEM = "系统";

    public static final String MENU = "/operations/videos";
    public static final String PERM_UPLOAD = "operations:video:upload";
    public static final String PERM_REVIEW = "operations:video:review";

    /** 图片素材挂在视频任务上；生成的视频与封面挂在生成结果上 */
    public static final String OWNER_TASK = "VIDEO_TASK";
    public static final String OWNER_OUTPUT = "VIDEO_OUTPUT";

    public static final int MAX_IMAGES = 12;
    public static final long IMAGE_MAX_BYTES = 20L * 1024 * 1024;
    public static final int MIN_SHOTS = 4;
    public static final int MAX_SHOTS = 8;
    public static final int MAX_TITLE = 100;
    public static final int MAX_CAPTION = 120;
    public static final int MAX_REJECT = 200;
    /** 估算语速：每秒约 2.5 个词 */
    public static final double WORDS_PER_SECOND = 2.5;
    public static final double MIN_SECONDS = 15;
    public static final double MAX_SECONDS = 20;

    public static final String AI_SKILL = "video-script";
    public static final String CONFIG_WINDOW = "video.queue.window";
    public static final String DEFAULT_WINDOW = "20:00-07:00";
    public static final int TIMEOUT_MINUTES = 15;
    public static final int RETRY_MINUTES = 10;
    public static final int MAX_UNAVAILABLE = 3;
    public static final String DISPATCH_LOCK = "zhul:erp:lock:video:dispatch";
    public static final String PAUSE_KEY = "zhul:erp:video:pause";
}
