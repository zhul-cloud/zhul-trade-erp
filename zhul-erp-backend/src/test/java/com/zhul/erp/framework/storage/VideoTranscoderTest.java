package com.zhul.erp.framework.storage;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 视频转码：没装 ffmpeg 时直接失败；装了 ffmpeg 的机器上（如后端镜像）再验证 MOV 转 MP4 与损坏文件保留原片。
 */
class VideoTranscoderTest {

    @TempDir
    Path tmp;

    private FileStorageService service(String ffmpeg) {
        StorageProperties props = new StorageProperties();
        props.setFfmpegPath(ffmpeg);
        LocalFileStorage local = new LocalFileStorage(tmp.resolve("priv").toString(), tmp.resolve("pub").toString(), "s");
        return new FileStorageService(new FileStorages(props, local, null), props);
    }

    private VideoTranscoder transcoder(FileStorageService files, String ffmpeg) {
        StorageProperties props = new StorageProperties();
        props.setFfmpegPath(ffmpeg);
        return new VideoTranscoder(files, props);
    }

    @Test
    void noFfmpeg_failsImmediately_originalKept() throws Exception {
        FileStorageService files = service("/no/such/ffmpeg");
        String key = "tenant/1/shipment/202610/0123456789abcdef0123456789abcdef.mov";
        Path src = Files.write(tmp.resolve("a.mov"), new byte[] {1, 2, 3});
        files.storages().local().put(false, key, src, "video/quicktime");
        CompletableFuture<String> result = new CompletableFuture<>();
        transcoder(files, "/no/such/ffmpeg").submit("LOCAL", key, false, d -> result.complete("done"), () -> result.complete("failed"));
        assertThat(result.get(5, TimeUnit.SECONDS)).isEqualTo("failed");
        assertThat(files.storages().local().size(false, key)).isEqualTo(3);
    }

    @Test
    void mov_becomesMp4_originalDeleted() throws Exception {
        Assumptions.assumeTrue(transcoder(service("ffmpeg"), "ffmpeg").ffmpegAvailable(), "本机没有 ffmpeg，跳过");
        FileStorageService files = service("ffmpeg");
        Path mov = tmp.resolve("in.mov");
        Process p = new ProcessBuilder("ffmpeg", "-nostdin", "-y", "-loglevel", "error", "-f", "lavfi", "-i",
                "testsrc=size=2560x1440:rate=25", "-t", "2", "-c:v", "mpeg4", mov.toString()).inheritIO().start();
        assertThat(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0).isTrue();
        String key = "tenant/1/shipment/202610/0123456789abcdef0123456789abcdef.mov";
        files.storages().local().put(false, key, mov, "video/quicktime");

        CompletableFuture<VideoTranscoder.Done> done = new CompletableFuture<>();
        transcoder(files, "ffmpeg").submit("LOCAL", key, false, done::complete, () -> done.completeExceptionally(new AssertionError("失败")));
        VideoTranscoder.Done d = done.get(120, TimeUnit.SECONDS);
        assertThat(d.key()).startsWith("tenant/1/shipment/202610/").endsWith(".mp4");
        assertThat(d.contentType()).isEqualTo("video/mp4");
        assertThat(files.storages().local().size(false, d.key())).isEqualTo(d.size());
        // 原片删除是在回调之后，稍等再看
        for (int i = 0; i < 50 && files.storages().local().size(false, key) >= 0; i++) {
            Thread.sleep(100);
        }
        assertThat(files.storages().local().size(false, key)).isEqualTo(-1);
    }

    @Test
    void brokenVideo_failed_originalStillReadable() throws Exception {
        Assumptions.assumeTrue(transcoder(service("ffmpeg"), "ffmpeg").ffmpegAvailable(), "本机没有 ffmpeg，跳过");
        FileStorageService files = service("ffmpeg");
        String key = "tenant/1/shipment/202610/fedcba9876543210fedcba9876543210.mp4";
        Path bad = Files.write(tmp.resolve("bad.mp4"), new byte[] {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'm', 'p', '4', '2', 9, 9});
        files.storages().local().put(false, key, bad, "video/mp4");
        CompletableFuture<String> result = new CompletableFuture<>();
        transcoder(files, "ffmpeg").submit("LOCAL", key, false, d -> result.complete("done"), () -> result.complete("failed"));
        assertThat(result.get(60, TimeUnit.SECONDS)).isEqualTo("failed");
        assertThat(files.storages().local().size(false, key)).isEqualTo(14);
    }
}
