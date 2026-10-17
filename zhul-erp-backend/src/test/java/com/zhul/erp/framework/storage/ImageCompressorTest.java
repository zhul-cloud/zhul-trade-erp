package com.zhul.erp.framework.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** 规格「上传压缩」的三个场景，以及方向、元数据处理 */
class ImageCompressorTest {

    @TempDir
    Path tmp;

    @Test
    void phonePhoto_scaledTo2048_noLocation_smaller() throws Exception {
        byte[] jpg = withExif(jpeg(photo(4032, 3024), 0.98f), 1);
        Path src = Files.write(tmp.resolve("p.jpg"), jpg);
        ImageCompressor.Result r = ImageCompressor.compress(src, "jpg", tmp);
        BufferedImage out = ImageIO.read(r.file().toFile());
        assertThat(r.ext()).isEqualTo("jpg");
        assertThat(out.getWidth()).isEqualTo(2048);
        assertThat(out.getHeight()).isEqualTo(1536);
        byte[] saved = Files.readAllBytes(r.file());
        assertThat(saved.length).isLessThan(jpg.length / 2);
        assertThat(new String(saved, StandardCharsets.ISO_8859_1)).doesNotContain("Exif").doesNotContain("GPS-SECRET");
    }

    @Test
    void rotatedPhoto_turnedUpright() throws Exception {
        // 方向 6：传感器横拍、需顺时针转 90 度显示；左上角红色转正后应在右上角
        BufferedImage img = photo(400, 300);
        paint(img, Color.RED, 0, 0, 40, 40);
        Path src = Files.write(tmp.resolve("r.jpg"), withExif(jpeg(img, 0.95f), 6));
        BufferedImage out = ImageIO.read(ImageCompressor.compress(src, "jpg", tmp).file().toFile());
        assertThat(out.getWidth()).isEqualTo(300);
        assertThat(out.getHeight()).isEqualTo(400);
        assertThat(isRed(out.getRGB(280, 20))).isTrue();
        assertThat(isRed(out.getRGB(20, 20))).isFalse();
    }

    @Test
    void pngScreenshot_staysPng_longEdge2048() throws Exception {
        BufferedImage img = new BufferedImage(1170, 2532, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 1170, 2532);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 64));
        for (int y = 200; y < 2500; y += 120) {
            g.drawString("USD 12,345.67", 100, y);
        }
        g.dispose();
        Path src = tmp.resolve("s.png");
        ImageIO.write(img, "png", src.toFile());
        ImageCompressor.Result r = ImageCompressor.compress(src, "png", tmp);
        BufferedImage out = ImageIO.read(r.file().toFile());
        assertThat(r.ext()).isEqualTo("png");
        assertThat(out.getHeight()).isEqualTo(2048);
        assertThat(out.getWidth()).isEqualTo(946);
    }

    @Test
    void smallCompressedJpeg_keptAsIs() throws Exception {
        byte[] jpg = jpeg(photo(800, 600), 0.3f);
        Path src = Files.write(tmp.resolve("small.jpg"), jpg);
        ImageCompressor.Result r = ImageCompressor.compress(src, "jpg", tmp);
        assertThat(Files.readAllBytes(r.file())).isEqualTo(jpg);
    }

    @Test
    void undecodable_keptWithMetadataStripped() throws Exception {
        byte[] broken = withExif(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9}, 1);
        Path src = Files.write(tmp.resolve("b.jpg"), broken);
        byte[] saved = Files.readAllBytes(ImageCompressor.compress(src, "jpg", tmp).file());
        assertThat(new String(saved, StandardCharsets.ISO_8859_1)).doesNotContain("GPS-SECRET");
    }

    @Test
    void stripPngAndWebp_dropTextChunks() {
        byte[] png = ImageCompressor.stripPng(concat(new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10},
                pngChunk("IHDR", new byte[13]), pngChunk("tEXt", "GPS-SECRET".getBytes(StandardCharsets.US_ASCII)),
                pngChunk("IEND", new byte[0])));
        assertThat(new String(png, StandardCharsets.ISO_8859_1)).contains("IHDR").contains("IEND").doesNotContain("GPS-SECRET");

        byte[] body = concat("WEBP".getBytes(StandardCharsets.US_ASCII), riffChunk("VP8 ", new byte[10]),
                riffChunk("EXIF", "GPS-SECRET".getBytes(StandardCharsets.US_ASCII)));
        byte[] webp = concat("RIFF".getBytes(StandardCharsets.US_ASCII), le(body.length), body);
        byte[] out = ImageCompressor.stripWebp(webp);
        assertThat(new String(out, StandardCharsets.ISO_8859_1)).contains("VP8 ").doesNotContain("GPS-SECRET");
        assertThat(ByteBuffer.wrap(out, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt()).isEqualTo(out.length - 8);
    }

    // ---------- 样例图片 ----------

    private static BufferedImage photo(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(7);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int base = (x * 255 / w + y * 255 / h) / 2;
                int n = rnd.nextInt(40);
                img.setRGB(x, y, new Color(Math.min(255, base + n), Math.min(255, 120 + n), Math.min(255, 255 - base)).getRGB());
            }
        }
        return img;
    }

    private static void paint(BufferedImage img, Color c, int x, int y, int w, int h) {
        Graphics2D g = img.createGraphics();
        g.setColor(c);
        g.fillRect(x, y, w, h);
        g.dispose();
    }

    private static boolean isRed(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > 200 && c.getGreen() < 80 && c.getBlue() < 80;
    }

    private static byte[] jpeg(BufferedImage img, float quality) throws Exception {
        ImageWriter w = ImageIO.getImageWritersByFormatName("jpeg").next();
        ImageWriteParam p = w.getDefaultWriteParam();
        p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        p.setCompressionQuality(quality);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            w.setOutput(out);
            w.write(null, new IIOImage(img, null, null), p);
        }
        w.dispose();
        return bytes.toByteArray();
    }

    /** 在 SOI 之后插入一个 APP1 Exif 段：方向标签 + 一个模拟拍摄位置的文本标签 */
    private static byte[] withExif(byte[] jpg, int orientation) {
        byte[] text = "GPS-SECRET\0".getBytes(StandardCharsets.US_ASCII);
        ByteBuffer tiff = ByteBuffer.allocate(8 + 2 + 2 * 12 + 4 + text.length).order(ByteOrder.BIG_ENDIAN);
        tiff.put((byte) 'M').put((byte) 'M').putShort((short) 42).putInt(8);
        tiff.putShort((short) 2);
        tiff.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        tiff.putShort((short) 0x010E).putShort((short) 2).putInt(text.length).putInt(8 + 2 + 2 * 12 + 4);
        tiff.putInt(0).put(text);
        byte[] payload = concat("Exif\0\0".getBytes(StandardCharsets.US_ASCII), tiff.array());
        byte[] seg = concat(new byte[] {(byte) 0xFF, (byte) 0xE1, (byte) ((payload.length + 2) >> 8), (byte) (payload.length + 2)},
                payload);
        return concat(new byte[] {jpg[0], jpg[1]}, seg, java.util.Arrays.copyOfRange(jpg, 2, jpg.length));
    }

    private static byte[] pngChunk(String type, byte[] data) {
        return concat(ByteBuffer.allocate(4).putInt(data.length).array(), type.getBytes(StandardCharsets.US_ASCII), data, new byte[4]);
    }

    private static byte[] riffChunk(String type, byte[] data) {
        return concat(type.getBytes(StandardCharsets.US_ASCII), le(data.length), data);
    }

    private static byte[] le(int v) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            out.writeBytes(p);
        }
        return out.toByteArray();
    }
}
