package com.zhul.erp.framework.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * 视频转码队列（规格「视频转码」）：原视频先按上传保存，这里在后台转成 H.264 + AAC 的 MP4（长边不超过 1920、支持边下边播），
 * 转完上传为新文件键、回调业务更新记录，再删除原视频；失败时保留原视频。线程池 media-transcode-pool 限制同时转码的数量。
 * 没有安装 ffmpeg 时直接按失败处理，不影响上传。
 */
@Slf4j
@Component
public class VideoTranscoder implements DisposableBean {

    /** 处理状态（0-无需处理、1-转码中、2-已完成、3-失败） */
    public static final int NONE = 0;
    public static final int PROCESSING = 1;
    public static final int DONE = 2;
    public static final int FAILED = 3;

    private static final long TIMEOUT_MINUTES = 30;
    private static final String SCALE = "scale='trunc(min(1,1920/max(iw,ih))*iw/2)*2':'trunc(min(1,1920/max(iw,ih))*ih/2)*2'";

    private final FileStorageService files;
    private final StorageProperties props;
    private final ThreadPoolExecutor pool;
    private volatile Boolean ffmpegAvailable;

    public VideoTranscoder(FileStorageService files, StorageProperties props) {
        this.files = files;
        this.props = props;
        AtomicInteger seq = new AtomicInteger();
        int threads = Math.max(1, props.getTranscodeThreads());
        this.pool = new ThreadPoolExecutor(threads, threads, 60, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, props.getTranscodeQueueCapacity())),
                r -> {
                    Thread t = new Thread(r, "media-transcode-pool-" + seq.incrementAndGet());
                    t.setDaemon(true);
                    return t;
                });
    }

    /** 转码完成：新文件键、大小、类型 */
    public record Done(String key, long size, String contentType) {
    }

    /**
     * 提交转码。onSuccess 在后台线程里更新业务记录（抛异常时新文件会被删除、原视频保留）；
     * onFailure 把记录标为失败。
     */
    public void submit(String storage, String key, boolean publicRead, Consumer<Done> onSuccess, Runnable onFailure) {
        if (!ffmpegAvailable()) {
            log.warn("没有找到 ffmpeg，视频不转码，key={}", key);
            onFailure.run();
            return;
        }
        try {
            pool.execute(() -> run(storage, key, publicRead, onSuccess, onFailure));
        } catch (RejectedExecutionException e) {
            log.warn("转码队列已满，视频不转码，key={}", key);
            onFailure.run();
        }
    }

    private void run(String storage, String key, boolean publicRead, Consumer<Done> onSuccess, Runnable onFailure) {
        Path in = null;
        Path out = null;
        String newKey = null;
        FileStorage fs = files.storages().of(storage);
        try {
            in = Files.createTempFile(files.workDir(), "tc-in-", "." + UploadRules.extOf(key));
            try (InputStream src = fs.open(publicRead, key)) {
                Files.copy(src, in, StandardCopyOption.REPLACE_EXISTING);
            }
            out = Files.createTempFile(files.workDir(), "tc-out-", ".mp4");
            if (!ffmpeg(in, out)) {
                onFailure.run();
                return;
            }
            newKey = key.substring(0, key.lastIndexOf('/') + 1) + UUID.randomUUID().toString().replace("-", "") + ".mp4";
            fs.put(publicRead, newKey, out, "video/mp4");
            onSuccess.accept(new Done(newKey, Files.size(out), "video/mp4"));
            fs.delete(publicRead, key);
            log.info("视频转码完成，key={} -> {}", key, newKey);
        } catch (IOException | RuntimeException e) {
            log.warn("视频转码失败，保留原视频，key={}：{}", key, e.toString());
            if (newKey != null) {
                files.deleteQuietly(storage, newKey, publicRead);
            }
            onFailure.run();
        } finally {
            FileStorageService.deleteTemp(in);
            FileStorageService.deleteTemp(out);
        }
    }

    private boolean ffmpeg(Path in, Path out) throws IOException {
        List<String> cmd = new ArrayList<>(24);
        if (new File("/usr/bin/nice").canExecute()) {
            cmd.addAll(List.of("/usr/bin/nice", "-n", "10"));
        }
        cmd.addAll(List.of(props.getFfmpegPath(), "-nostdin", "-y", "-loglevel", "error", "-i", in.toString(),
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "28", "-pix_fmt", "yuv420p", "-vf", SCALE,
                "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", out.toString()));
        Path logFile = Files.createTempFile(files.workDir(), "tc-log-", ".txt");
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(logFile.toFile()).start();
            if (!p.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
                p.destroyForcibly();
                log.warn("ffmpeg 超过 {} 分钟未完成，已终止", TIMEOUT_MINUTES);
                return false;
            }
            if (p.exitValue() != 0) {
                String msg = Files.readString(logFile);
                log.warn("ffmpeg 失败，exit={}：{}", p.exitValue(), msg.length() > 500 ? msg.substring(0, 500) : msg);
                return false;
            }
            return Files.size(out) > 0;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            FileStorageService.deleteTemp(logFile);
        }
    }

    /** 启动时检测一次并写日志，部署后看启动日志即可确认视频能否压缩 */
    @EventListener(ApplicationReadyEvent.class)
    public void reportFfmpeg() {
        if (ffmpegAvailable()) {
            log.info("视频转码：ffmpeg 可用（{}），上传的视频会在后台压缩", props.getFfmpegPath());
        } else {
            log.warn("视频转码：没有找到 ffmpeg（{}），上传的视频保留原样不压缩；Docker 镜像已自带，直接运行时请安装 ffmpeg",
                    props.getFfmpegPath());
        }
    }

    /** 首次使用时检测 ffmpeg 是否可用 */
    public boolean ffmpegAvailable() {
        Boolean v = ffmpegAvailable;
        if (v == null) {
            try {
                Process p = new ProcessBuilder(props.getFfmpegPath(), "-version").redirectErrorStream(true)
                        .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                v = p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
            } catch (IOException e) {
                v = false;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                v = false;
            }
            ffmpegAvailable = v;
        }
        return v;
    }

    @Override
    public void destroy() {
        pool.shutdownNow();
    }
}
