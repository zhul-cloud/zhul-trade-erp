package com.zhul.erp.modules.document.support;

import com.zhul.erp.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Excel → PDF（LibreOffice headless，图片降采样到 150 dpi、JPEG 质量 80）→ 图片（PDFBox）。
 * 单进程串行：同一时刻只跑一个 soffice（共用一个独立的用户目录），正式导出优先于预览。
 */
@Slf4j
@Component
public class DocumentConverter {

    public static final String UNAVAILABLE = "PDF / 图片暂时无法生成，可以先下载 Excel";
    private static final String PDF_FILTER = "pdf:calc_pdf_Export:{\"ReduceImageResolution\":{\"type\":\"boolean\",\"value\":\"true\"},"
            + "\"MaxImageResolution\":{\"type\":\"long\",\"value\":\"150\"},\"Quality\":{\"type\":\"long\",\"value\":\"80\"}}";
    /** 背调报告等 HTML 文档用 Writer/Web 导出 */
    private static final String HTML_PDF_FILTER = "pdf:writer_web_pdf_Export";
    private static final int TIMEOUT_SECONDS = 30;
    private static final int PREVIEW_CACHE_SIZE = 50;
    /** 预览图分辨率：放大查看时 A4 宽约 1240 像素，高分屏上文字仍清晰 */
    private static final int PREVIEW_DPI = 150;
    /** 图片导出分辨率：裁掉页边后 A4 正文宽约 2000 像素 */
    private static final int IMAGE_DPI = 240;
    /** 比这个更亮的像素算空白（0–255，取 RGB 最暗的通道） */
    private static final int INK_THRESHOLD = 240;
    /** 页脚块最大高度、与正文之间的最小空白（占页高比例）；裁边后四周留白（占页宽比例） */
    private static final double FOOTER_MAX_RATIO = 0.03;
    private static final double FOOTER_GAP_RATIO = 0.05;
    private static final float TRIM_PAD_RATIO = 0.02f;

    private final String configuredPath;
    private final Path workDir;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final AtomicInteger waitingExports = new AtomicInteger();
    private final Map<String, List<byte[]>> previewCache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<byte[]>> eldest) {
            return size() > PREVIEW_CACHE_SIZE;
        }
    };
    private volatile String sofficePath;

    public DocumentConverter(@Value("${zhul.document.soffice-path:}") String configuredPath,
                             @Value("${zhul.document.work-dir:${java.io.tmpdir}/zhul-document}") String workDir) {
        this.configuredPath = configuredPath;
        this.workDir = Path.of(workDir);
    }

    /** 探测 soffice；找不到时 PDF / 图片不可用，Excel 不受影响 */
    public boolean available() {
        return soffice() != null;
    }

    private String soffice() {
        String p = sofficePath;
        if (p != null) {
            return p;
        }
        List<String> candidates = new ArrayList<>();
        if (configuredPath != null && !configuredPath.isBlank()) {
            // 明确配置了路径就只认它，不再自动探测
            return new File(configuredPath).canExecute() ? (sofficePath = configuredPath) : null;
        }
        candidates.add("/Applications/LibreOffice.app/Contents/MacOS/soffice");
        candidates.add("/usr/bin/soffice");
        candidates.add("/usr/lib/libreoffice/program/soffice");
        candidates.add("/opt/libreoffice/program/soffice");
        for (String c : candidates) {
            if (new File(c).canExecute()) {
                sofficePath = c;
                return c;
            }
        }
        return null;
    }

    /** 正式导出：优先于排队中的预览 */
    public byte[] toPdf(byte[] xlsx) {
        waitingExports.incrementAndGet();
        try {
            lock.lock();
        } finally {
            waitingExports.decrementAndGet();
        }
        try {
            return convert(xlsx);
        } finally {
            lock.unlock();
        }
    }

    /** HTML → PDF（背调报告导出），与 Excel 导出共用同一个转换队列 */
    public byte[] htmlToPdf(byte[] html) {
        waitingExports.incrementAndGet();
        try {
            lock.lock();
        } finally {
            waitingExports.decrementAndGet();
        }
        try {
            return convert(html, "document.html", HTML_PDF_FILTER);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 预览：返回逐页 JPG（96 dpi、质量 0.7）。同样内容命中缓存不再转换；
     * 拿到转换机会时若 {@code stillWanted} 已不成立（用户又改了内容），直接放弃。
     */
    public List<byte[]> previewPages(String cacheKey, byte[] xlsx, java.util.function.BooleanSupplier stillWanted) {
        synchronized (previewCache) {
            List<byte[]> hit = previewCache.get(cacheKey);
            if (hit != null) {
                return hit;
            }
        }
        while (true) {
            lock.lock();
            if (waitingExports.get() == 0) {
                break;
            }
            lock.unlock();
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        try {
            if (!stillWanted.getAsBoolean()) {
                return null;
            }
            List<byte[]> pages = renderPages(convert(xlsx), 96, 0.7f);
            synchronized (previewCache) {
                previewCache.put(cacheKey, pages);
            }
            return pages;
        } finally {
            lock.unlock();
        }
    }

    private byte[] convert(byte[] xlsx) {
        return convert(xlsx, "document.xlsx", PDF_FILTER);
    }

    private byte[] convert(byte[] content, String fileName, String filter) {
        String bin = soffice();
        if (bin == null) {
            throw BizException.of("CONVERTER_UNAVAILABLE", UNAVAILABLE);
        }
        Path dir = null;
        try {
            Files.createDirectories(workDir);
            dir = Files.createTempDirectory(workDir, "job-");
            Path in = dir.resolve(fileName);
            Files.write(in, content);
            Path profile = workDir.resolve("profile");
            ProcessBuilder pb = new ProcessBuilder(bin, "--headless", "--norestore", "--nolockcheck",
                    "-env:UserInstallation=" + profile.toUri(), "--convert-to", filter, "--outdir", dir.toString(), in.toString());
            pb.redirectErrorStream(true);
            pb.redirectOutput(dir.resolve("soffice.log").toFile());
            Process process = pb.start();
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.error("LibreOffice 转换超时（{} 秒）", TIMEOUT_SECONDS);
                throw BizException.of("CONVERTER_UNAVAILABLE", UNAVAILABLE);
            }
            Path pdf = dir.resolve("document.pdf");
            if (process.exitValue() != 0 || !Files.isRegularFile(pdf)) {
                log.error("LibreOffice 转换失败，exit={}, log={}", process.exitValue(), Files.readString(dir.resolve("soffice.log")));
                throw BizException.of("CONVERTER_UNAVAILABLE", UNAVAILABLE);
            }
            return Files.readAllBytes(pdf);
        } catch (IOException e) {
            log.error("LibreOffice 转换异常", e);
            throw BizException.of("CONVERTER_UNAVAILABLE", UNAVAILABLE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BizException.of("CONVERTER_UNAVAILABLE", UNAVAILABLE);
        } finally {
            deleteQuietly(dir);
        }
    }

    /**
     * 图片导出：每页 240 dpi 渲染、裁掉四周空白（最后一页底部单独的页码行一并去掉），纵向拼成一张，
     * 宽度不超过 maxWidth，JPG 质量 0.9。图片用于聊天工具里直接发给客户，只留内容，放大也清晰。
     */
    public byte[] toJpeg(byte[] pdf, int maxWidth) {
        List<BufferedImage> pages = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                pages.add(trim(renderer.renderImageWithDPI(i, IMAGE_DPI, ImageType.RGB)));
            }
        } catch (IOException e) {
            throw new BizException("图片生成失败，可以先下载 Excel", e);
        }
        int width = pages.stream().mapToInt(BufferedImage::getWidth).max().orElse(1);
        int height = pages.stream().mapToInt(BufferedImage::getHeight).sum();
        double scale = width > maxWidth ? (double) maxWidth / width : 1.0;
        int w = (int) Math.round(width * scale);
        int h = (int) Math.round(height * scale);
        BufferedImage out = new BufferedImage(w, Math.max(h, 1), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, w, Math.max(h, 1));
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        int y = 0;
        for (BufferedImage p : pages) {
            int ph = (int) Math.round(p.getHeight() * scale);
            g.drawImage(p, 0, y, (int) Math.round(p.getWidth() * scale), ph, null);
            y += ph;
        }
        g.dispose();
        return jpeg(out, 0.9f);
    }

    /**
     * 裁到内容区域，四周各留一点边。页面最底部单独的一小块（页码）与正文之间隔着大片空白时视为页脚去掉。
     * 整页空白时原样返回。
     */
    public static BufferedImage trim(BufferedImage page) {
        int pw = page.getWidth();
        int ph = page.getHeight();
        boolean[] inkRow = new boolean[ph];
        int left = pw;
        int right = -1;
        for (int y = 0; y < ph; y++) {
            for (int x = 0; x < pw; x++) {
                if (isInk(page.getRGB(x, y))) {
                    inkRow[y] = true;
                    left = Math.min(left, x);
                    right = Math.max(right, x);
                }
            }
        }
        int top = 0;
        while (top < ph && !inkRow[top]) {
            top++;
        }
        if (top == ph) {
            return page;
        }
        int bottom = ph - 1;
        while (!inkRow[bottom]) {
            bottom--;
        }
        // 最底部的一块：高度很小、上方空白很大 → 页脚（页码），去掉后重新计算左右边界
        int blockTop = bottom;
        while (blockTop > top && inkRow[blockTop - 1]) {
            blockTop--;
        }
        int gapTop = blockTop - 1;
        while (gapTop > top && !inkRow[gapTop]) {
            gapTop--;
        }
        boolean footer = blockTop > top && bottom - blockTop + 1 < ph * FOOTER_MAX_RATIO
                && blockTop - gapTop - 1 > ph * FOOTER_GAP_RATIO;
        if (footer) {
            bottom = gapTop;
            left = pw;
            right = -1;
            for (int y = top; y <= bottom; y++) {
                for (int x = 0; x < pw; x++) {
                    if (isInk(page.getRGB(x, y))) {
                        left = Math.min(left, x);
                        right = Math.max(right, x);
                    }
                }
            }
        }
        int pad = Math.round(pw * TRIM_PAD_RATIO);
        int x0 = Math.max(0, left - pad);
        int y0 = Math.max(0, top - pad);
        int x1 = Math.min(pw, right + 1 + pad);
        int y1 = Math.min(ph, bottom + 1 + pad);
        return page.getSubimage(x0, y0, x1 - x0, y1 - y0);
    }

    private static boolean isInk(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        return Math.min(r, Math.min(g, b)) < INK_THRESHOLD;
    }

    private List<byte[]> renderPages(byte[] pdf, int dpi, float quality) {
        List<byte[]> list = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFRenderer renderer = new PDFRenderer(doc);
            for (int i = 0; i < doc.getNumberOfPages(); i++) {
                list.add(jpeg(renderer.renderImageWithDPI(i, dpi, ImageType.RGB), quality));
            }
        } catch (IOException e) {
            throw new BizException("预览生成失败", e);
        }
        return list;
    }

    static byte[] jpeg(BufferedImage image, float quality) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new BizException("图片生成失败", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var files = Files.walk(dir)) {
            files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            log.warn("清理转换临时目录失败：{}", dir);
        }
    }
}
