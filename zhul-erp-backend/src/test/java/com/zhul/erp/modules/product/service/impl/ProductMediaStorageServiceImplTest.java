package com.zhul.erp.modules.product.service.impl;

import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.product.constants.ProductErrorCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductMediaStorageServiceImplTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1, 1, 0, 0, 1};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0x10, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};
    private static final byte[] MP4 = {0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'i', 's', 'o', 'm', 0, 0, 2, 0};
    private static final byte[] WEBM = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x9F, 0x42, (byte) 0x86, (byte) 0x81, 1, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] EXE = {'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0, (byte) 0xFF, (byte) 0xFF, 0, 0};

    @TempDir
    Path uploadDir;

    private ProductMediaStorageServiceImpl storage;

    @BeforeEach
    void setUp() {
        storage = new ProductMediaStorageServiceImpl(uploadDir.toString());
    }

    private static MockMultipartFile file(String name, byte[] content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content);
    }

    private static byte[] withPadding(byte[] header, int totalSize) {
        byte[] data = Arrays.copyOf(header, totalSize);
        return data;
    }

    private void assertRejected(org.junit.jupiter.api.function.Executable e, String errorCode) {
        BizException ex = assertThrows(BizException.class, e);
        assertEquals(errorCode, ex.getErrorCode(), ex.getMessage());
    }

    private long filesOnDisk() throws IOException {
        try (Stream<Path> walk = Files.walk(uploadDir)) {
            return walk.filter(Files::isRegularFile).count();
        }
    }

    @Test
    void validFilesOfEveryAllowedFormatAreStored() throws IOException {
        record Case(String name, byte[] content, int type, String expectedExt) {
        }
        for (Case c : List.of(
                new Case("a.png", PNG, 1, "png"), new Case("a.jpg", JPEG, 1, "jpg"),
                new Case("a.jpeg", JPEG, 1, "jpg"), new Case("a.webp", WEBP, 1, "webp"),
                new Case("a.mp4", MP4, 2, "mp4"), new Case("a.webm", WEBM, 2, "webm"))) {
            var stored = storage.store(file(c.name(), withPadding(c.content(), 64)), c.type());

            assertTrue(stored.url().startsWith("/uploads/product/"), stored.url());
            assertTrue(stored.url().endsWith("." + c.expectedExt()), stored.url());
            assertEquals(64, stored.size());
            assertTrue(Files.exists(uploadDir.resolve(stored.url().substring("/uploads/".length()))));
        }
    }

    @Test
    void uppercaseExtensionIsAccepted() {
        var stored = storage.store(file("PHOTO.PNG", withPadding(PNG, 32)), 1);
        assertTrue(stored.url().endsWith(".png"));
    }

    @Test
    void storedNameIsRandomAndNeverContainsClientFilename() {
        var a = storage.store(file("secret-name.png", withPadding(PNG, 32)), 1);
        var b = storage.store(file("secret-name.png", withPadding(PNG, 32)), 1);

        assertFalse(a.url().contains("secret-name"));
        assertFalse(a.url().equals(b.url()));
    }

    @Test
    void clientFilenameWithTraversalCannotEscapeUploadDirectory() throws IOException {
        var stored = storage.store(file("../../../etc/evil.png", withPadding(PNG, 32)), 1);

        Path saved = uploadDir.resolve(stored.url().substring("/uploads/".length())).normalize();
        assertTrue(saved.startsWith(uploadDir));
        assertFalse(stored.url().contains(".."));
        assertEquals(1, filesOnDisk());
    }

    @Test
    void extensionMustMatchFileHeader() throws IOException {
        assertRejected(() -> storage.store(file("a.png", EXE), 1), ProductErrorCodes.MEDIA_FILE_INVALID);
        assertRejected(() -> storage.store(file("a.png", withPadding(JPEG, 32)), 1), ProductErrorCodes.MEDIA_FILE_INVALID);
        assertRejected(() -> storage.store(file("a.jpg", withPadding(PNG, 32)), 1), ProductErrorCodes.MEDIA_FILE_INVALID);
        assertRejected(() -> storage.store(file("a.mp4", withPadding(WEBM, 32)), 2), ProductErrorCodes.MEDIA_FILE_INVALID);
        assertRejected(() -> storage.store(file("a.webm", withPadding(MP4, 32)), 2), ProductErrorCodes.MEDIA_FILE_INVALID);
        assertEquals(0, filesOnDisk(), "校验不通过时不应留下任何文件");
    }

    @Test
    void svgIsAlwaysRejected() {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);

        BizException e = assertThrows(BizException.class, () -> storage.store(file("a.svg", svg), 1));
        assertEquals(ProductErrorCodes.MEDIA_FILE_INVALID, e.getErrorCode());
        assertTrue(e.getMessage().contains("SVG"));
        assertRejected(() -> storage.store(file("a.SVG", svg), 1), ProductErrorCodes.MEDIA_FILE_INVALID);
        assertRejected(() -> storage.store(file("a.svgz", svg), 1), ProductErrorCodes.MEDIA_FILE_INVALID);
        // 改成图片扩展名也不行：文件头不是图片
        assertRejected(() -> storage.store(file("a.png", svg), 1), ProductErrorCodes.MEDIA_FILE_INVALID);
    }

    @Test
    void unsupportedExtensionsAreRejected() {
        assertRejected(() -> storage.store(file("a.gif", withPadding(PNG, 32)), 1), ProductErrorCodes.MEDIA_FILE_INVALID);
        assertRejected(() -> storage.store(file("a.exe", EXE), 1), ProductErrorCodes.MEDIA_FILE_INVALID);
        assertRejected(() -> storage.store(file("noextension", withPadding(PNG, 32)), 1), ProductErrorCodes.MEDIA_FILE_INVALID);
        assertRejected(() -> storage.store(file(null, withPadding(PNG, 32)), 1), ProductErrorCodes.MEDIA_FILE_INVALID);
        // 视频扩展名不能当图片传，反之亦然
        assertRejected(() -> storage.store(file("a.mp4", withPadding(MP4, 32)), 1), ProductErrorCodes.MEDIA_FILE_INVALID);
        assertRejected(() -> storage.store(file("a.png", withPadding(PNG, 32)), 2), ProductErrorCodes.MEDIA_FILE_INVALID);
    }

    @Test
    void emptyOrMissingFileIsRejected() {
        assertRejected(() -> storage.store(file("a.png", new byte[0]), 1), ProductErrorCodes.MEDIA_FILE_INVALID);
        assertRejected(() -> storage.store(null, 1), ProductErrorCodes.MEDIA_FILE_INVALID);
    }

    @Test
    void headerShorterThanSignatureIsRejected() {
        assertRejected(() -> storage.store(file("a.png", new byte[]{(byte) 0x89, 'P'}), 1), ProductErrorCodes.MEDIA_FILE_INVALID);
    }

    @Test
    void invalidMediaTypeIsRejected() {
        assertRejected(() -> storage.store(file("a.png", withPadding(PNG, 32)), 3), ProductErrorCodes.PARAM_INVALID);
    }

    @Test
    void imageSizeBoundaryIsFiveMegabytes() throws IOException {
        long limit = 5L * 1024 * 1024;

        var atLimit = storage.store(file("a.png", withPadding(PNG, (int) limit)), 1);
        assertEquals(limit, atLimit.size());

        assertRejected(() -> storage.store(file("a.png", withPadding(PNG, (int) limit + 1)), 1), ProductErrorCodes.MEDIA_TOO_LARGE);
        assertRejected(() -> storage.store(file("a.jpg", withPadding(JPEG, 6 * 1024 * 1024)), 1), ProductErrorCodes.MEDIA_TOO_LARGE);
        assertEquals(1, filesOnDisk(), "超限的文件不应保存");
    }

    @Test
    void videoAllowsMoreThanImageButNotMoreThanHundredMegabytes() {
        // 6MB 的视频可以，但同样大小的图片不行
        storage.store(file("a.mp4", withPadding(MP4, 6 * 1024 * 1024)), 2);
        assertRejected(() -> storage.store(file("a.png", withPadding(PNG, 6 * 1024 * 1024)), 1), ProductErrorCodes.MEDIA_TOO_LARGE);
    }

    @Test
    void oversizedByDeclaredSizeIsRejectedWithoutWriting() throws IOException {
        var declaredHuge = new MockMultipartFile("file", "a.mp4", "video/mp4", withPadding(MP4, 32)) {
            @Override
            public long getSize() {
                return 120L * 1024 * 1024;
            }
        };

        assertRejected(() -> storage.store(declaredHuge, 2), ProductErrorCodes.MEDIA_TOO_LARGE);
        assertEquals(0, filesOnDisk());
    }

    @Test
    void actualBytesOverLimitAreCaughtEvenIfDeclaredSizeIsSmall() throws IOException {
        var lying = new MockMultipartFile("file", "a.png", "image/png", withPadding(PNG, 5 * 1024 * 1024 + 100)) {
            @Override
            public long getSize() {
                return 100;
            }
        };

        assertRejected(() -> storage.store(lying, 1), ProductErrorCodes.MEDIA_TOO_LARGE);
        assertEquals(0, filesOnDisk(), "写盘中途超限，已写入的部分要清理掉");
    }

    @Test
    void deleteQuietlyRemovesOnlyFilesInsideUploadDirectory() throws IOException {
        var stored = storage.store(file("a.png", withPadding(PNG, 32)), 1);
        Path outside = Files.createTempFile("outside", ".txt");

        storage.deleteQuietly(stored.url());
        storage.deleteQuietly("/uploads/../" + outside.getFileName());
        storage.deleteQuietly("https://example.com/a.png");
        storage.deleteQuietly(null);

        assertEquals(0, filesOnDisk());
        assertTrue(Files.exists(outside));
        Files.deleteIfExists(outside);
    }

    @Test
    void resolvedPathOutsideUploadRootIsRefused() {
        Path root = uploadDir.toAbsolutePath().normalize();

        assertRejected(() -> ProductMediaStorageServiceImpl.resolveInside(root, root.resolve("product"), "../../evil.png"),
                ProductErrorCodes.MEDIA_FILE_INVALID);
        assertRejected(() -> ProductMediaStorageServiceImpl.resolveInside(root, root, "/etc/passwd"),
                ProductErrorCodes.MEDIA_FILE_INVALID);
        assertTrue(ProductMediaStorageServiceImpl.resolveInside(root, root.resolve("product"), "a.png").startsWith(root));
    }

    @Test
    void nullClientFilenameIsRejected() {
        var noName = new MockMultipartFile("file", null, "image/png", withPadding(PNG, 32)) {
            @Override
            public String getOriginalFilename() {
                return null;
            }
        };

        assertRejected(() -> storage.store(noName, 1), ProductErrorCodes.MEDIA_FILE_INVALID);
    }

    @Test
    void ioFailureWhileSavingIsReportedAndLeavesNothingBehind() throws IOException {
        // 让目标目录无法创建：上传根目录下的 product 是一个普通文件
        Files.writeString(uploadDir.resolve("product"), "not a directory");

        assertRejected(() -> storage.store(file("a.png", withPadding(PNG, 32)), 1), ProductErrorCodes.MEDIA_FILE_INVALID);
    }

    @Test
    void failureToDeleteIsSwallowed() throws IOException {
        Path dir = Files.createDirectories(uploadDir.resolve("full"));
        Files.writeString(dir.resolve("inner.txt"), "x");

        storage.deleteQuietly("/uploads/full");   // 非空目录删不掉，只记日志，不抛异常

        assertTrue(Files.exists(dir.resolve("inner.txt")));
    }

    @Test
    void shortHeadersOfEachFormatAreNotMistakenForThatFormat() {
        for (byte[] truncated : List.of(Arrays.copyOf(WEBP, 8), Arrays.copyOf(MP4, 8), Arrays.copyOf(WEBM, 3),
                Arrays.copyOf(PNG, 7), Arrays.copyOf(JPEG, 2))) {
            assertEquals("", ProductMediaStorageServiceImpl.detectFormat(truncated));
        }
        assertEquals("jpeg", ProductMediaStorageServiceImpl.detectFormat(JPEG));
        assertEquals("png", ProductMediaStorageServiceImpl.detectFormat(PNG));
        assertEquals("webp", ProductMediaStorageServiceImpl.detectFormat(WEBP));
        assertEquals("mp4", ProductMediaStorageServiceImpl.detectFormat(MP4));
        assertEquals("webm", ProductMediaStorageServiceImpl.detectFormat(WEBM));
    }
}
