package com.zhul.erp.framework.storage;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Set;

/**
 * 上传图片压缩（规格「上传压缩」）：按拍摄方向转正 → 长边缩到 2048 像素 → 重新编码（JPG、WEBP 输出质量 0.85 的 JPG，
 * PNG 保持 PNG 无损）。重新编码会丢弃全部元数据（拍摄位置、设备等）。结果不小于原文件时保留原文件，只去掉元数据；
 * 但原图带非默认拍摄方向时仍用重新编码的结果，否则去掉方向信息后图片会显示歪。解码失败或像素过多时原样保留（去元数据）。
 */
@Slf4j
public final class ImageCompressor {

    static final int MAX_EDGE = 2048;
    static final float JPEG_QUALITY = 0.85f;
    /** 超过这个像素数不解码（防止超大图片占满内存），原样保留 */
    static final long MAX_PIXELS = 80_000_000L;
    private static final Set<String> PNG_DROP = Set.of("tEXt", "zTXt", "iTXt", "eXIf", "tIME");
    private static final Set<String> WEBP_DROP = Set.of("EXIF", "XMP ");

    private ImageCompressor() {
    }

    /** 压缩结果：文件与扩展名（WEBP 可能变成 jpg） */
    public record Result(Path file, String ext) {
    }

    public static Result compress(Path src, String ext, Path workDir) {
        try {
            byte[] original = Files.readAllBytes(src);
            int orientation = "jpg".equals(ext) ? jpegOrientation(original) : 1;
            BufferedImage img = decode(src);
            if (img == null) {
                return write(workDir, strip(original, ext), ext);
            }
            img = orient(scale(img), orientation);
            String outExt = "png".equals(ext) ? "png" : "jpg";
            byte[] encoded = "png".equals(outExt) ? encodePng(img) : encodeJpeg(img);
            if (encoded.length >= original.length && orientation == 1) {
                return write(workDir, strip(original, ext), ext);
            }
            return write(workDir, encoded, outExt);
        } catch (IOException e) {
            throw new UncheckedIOException("压缩图片失败", e);
        }
    }

    private static BufferedImage decode(Path src) {
        try (ImageInputStream in = ImageIO.createImageInputStream(src.toFile())) {
            Iterator<ImageReader> readers = in == null ? null : ImageIO.getImageReaders(in);
            if (readers == null || !readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    log.warn("图片像素过多，跳过压缩，size={}x{}", reader.getWidth(0), reader.getHeight(0));
                    return null;
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            log.warn("图片解码失败，原样保存（去掉元数据）：{}", e.toString());
            return null;
        }
    }

    /** 长边缩到 MAX_EDGE：先逐次减半再一次到位，缩小倍数大时也不失真 */
    static BufferedImage scale(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int edge = Math.max(w, h);
        if (edge <= MAX_EDGE) {
            return img;
        }
        double ratio = (double) MAX_EDGE / edge;
        int tw = Math.max(1, (int) Math.round(w * ratio));
        int th = Math.max(1, (int) Math.round(h * ratio));
        BufferedImage cur = img;
        while (cur.getWidth() / 2 >= tw && cur.getHeight() / 2 >= th) {
            cur = draw(cur, cur.getWidth() / 2, cur.getHeight() / 2, null);
        }
        return draw(cur, tw, th, null);
    }

    /** 按 EXIF 方向（1-8）转正 */
    static BufferedImage orient(BufferedImage img, int o) {
        int w = img.getWidth();
        int h = img.getHeight();
        AffineTransform t;
        switch (o) {
            case 2 -> t = new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3 -> t = new AffineTransform(-1, 0, 0, -1, w, h);
            case 4 -> t = new AffineTransform(1, 0, 0, -1, 0, h);
            case 5 -> t = new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6 -> t = new AffineTransform(0, 1, -1, 0, h, 0);
            case 7 -> t = new AffineTransform(0, -1, -1, 0, h, w);
            case 8 -> t = new AffineTransform(0, -1, 1, 0, 0, w);
            default -> {
                return img;
            }
        }
        boolean swap = o >= 5;
        return draw(img, swap ? h : w, swap ? w : h, t);
    }

    private static BufferedImage draw(BufferedImage src, int w, int h, AffineTransform t) {
        int type = src.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage out = new BufferedImage(w, h, type);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (t == null) {
                g.drawImage(src, 0, 0, w, h, null);
            } else {
                g.drawImage(src, t, null);
            }
        } finally {
            g.dispose();
        }
        return out;
    }

    private static byte[] encodeJpeg(BufferedImage img) throws IOException {
        BufferedImage rgb = img;
        if (img.getType() != BufferedImage.TYPE_INT_RGB) {
            // JPG 没有透明通道：透明部分填白
            rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            try {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, img.getWidth(), img.getHeight());
                g.drawImage(img, 0, 0, null);
            } finally {
                g.dispose();
            }
        }
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(JPEG_QUALITY);
        return encode(writer, param, rgb);
    }

    private static byte[] encodePng(BufferedImage img) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("png").next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            // PNG 写入器的质量 0 表示最高压缩级别（无损）
            param.setCompressionQuality(0.0f);
        }
        return encode(writer, param, img);
    }

    private static byte[] encode(ImageWriter writer, ImageWriteParam param, BufferedImage img) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(64 * 1024);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(img, null, null), param);
        } finally {
            writer.dispose();
        }
        return bytes.toByteArray();
    }

    private static Result write(Path workDir, byte[] data, String ext) throws IOException {
        Path out = Files.createTempFile(workDir, "img-", "." + ext);
        Files.write(out, data);
        return new Result(out, ext);
    }

    // ---------- 去掉元数据（不重新编码） ----------

    static byte[] strip(byte[] data, String ext) {
        try {
            return switch (ext) {
                case "jpg" -> stripJpeg(data);
                case "png" -> stripPng(data);
                case "webp" -> stripWebp(data);
                default -> data;
            };
        } catch (RuntimeException e) {
            log.warn("去掉图片元数据失败，原样保存：{}", e.toString());
            return data;
        }
    }

    /** JPG：去掉 APP1（EXIF、XMP）、APP3-APP13、APP15 与注释段；保留 APP0（JFIF）、APP2（色彩配置）、APP14（Adobe） */
    static byte[] stripJpeg(byte[] d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(d.length);
        out.write(d, 0, 2);
        int i = 2;
        while (i + 4 <= d.length && (d[i] & 0xFF) == 0xFF) {
            int marker = d[i + 1] & 0xFF;
            if (marker == 0xDA) {
                break;
            }
            int len = ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
            boolean drop = marker == 0xE1 || (marker >= 0xE3 && marker <= 0xED) || marker == 0xEF || marker == 0xFE;
            if (!drop) {
                out.write(d, i, len + 2);
            }
            i += len + 2;
        }
        out.write(d, i, d.length - i);
        return out.toByteArray();
    }

    static byte[] stripPng(byte[] d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(d.length);
        out.write(d, 0, 8);
        int i = 8;
        while (i + 12 <= d.length) {
            int len = ByteBuffer.wrap(d, i, 4).getInt();
            String type = new String(d, i + 4, 4, StandardCharsets.US_ASCII);
            if (len < 0 || i + 12L + len > d.length) {
                break;
            }
            if (!PNG_DROP.contains(type)) {
                out.write(d, i, len + 12);
            }
            i += len + 12;
        }
        // 结构不完整的尾部原样保留
        out.write(d, i, d.length - i);
        return out.toByteArray();
    }

    static byte[] stripWebp(byte[] d) {
        ByteArrayOutputStream body = new ByteArrayOutputStream(d.length);
        body.write(d, 8, 4);
        int i = 12;
        while (i + 8 <= d.length) {
            int len = ByteBuffer.wrap(d, i + 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            int total = 8 + len + (len & 1);
            String type = new String(d, i, 4, StandardCharsets.US_ASCII);
            if (len < 0 || i + (long) total > d.length) {
                break;
            }
            if (!WEBP_DROP.contains(type)) {
                body.write(d, i, total);
            }
            i += total;
        }
        if (i < d.length) {
            body.write(d, i, d.length - i);
        }
        byte[] b = body.toByteArray();
        ByteBuffer out = ByteBuffer.allocate(8 + b.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put(d, 0, 4).putInt(b.length).put(b);
        return out.array();
    }

    // ---------- 读取 JPG 的拍摄方向 ----------

    /** EXIF 方向标签（0x0112）的值，没有或无法识别时为 1 */
    static int jpegOrientation(byte[] d) {
        int i = 2;
        while (i + 4 <= d.length && (d[i] & 0xFF) == 0xFF) {
            int marker = d[i + 1] & 0xFF;
            int len = ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
            if (marker == 0xDA) {
                break;
            }
            if (marker == 0xE1 && len > 16 && new String(d, i + 4, 4, StandardCharsets.US_ASCII).equals("Exif")) {
                return tiffOrientation(d, i + 10, Math.min(d.length, i + 2 + len));
            }
            i += len + 2;
        }
        return 1;
    }

    private static int tiffOrientation(byte[] d, int tiff, int end) {
        if (tiff + 8 > end) {
            return 1;
        }
        ByteOrder order = d[tiff] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        ByteBuffer b = ByteBuffer.wrap(d, 0, end).order(order);
        int ifd = tiff + b.getInt(tiff + 4);
        if (ifd + 2 > end) {
            return 1;
        }
        int count = b.getShort(ifd) & 0xFFFF;
        for (int n = 0; n < count; n++) {
            int entry = ifd + 2 + n * 12;
            if (entry + 12 > end) {
                return 1;
            }
            if ((b.getShort(entry) & 0xFFFF) == 0x0112) {
                int v = b.getShort(entry + 8) & 0xFFFF;
                return v >= 1 && v <= 8 ? v : 1;
            }
        }
        return 1;
    }
}
