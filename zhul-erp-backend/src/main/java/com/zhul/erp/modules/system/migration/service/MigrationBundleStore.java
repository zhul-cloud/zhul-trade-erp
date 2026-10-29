package com.zhul.erp.modules.system.migration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.common.exception.BizException;
import com.zhul.erp.modules.system.migration.dto.MigrationBundleVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 上传的迁移包（打包脚本 pack.js 生成的 zip：清洗结果、旧报表数字、图片清单，都是根目录下的 JSON 文件）。
 * 解压到私有上传目录的 history-migration/{内容哈希}，同一个包重复上传不重复解压。
 */
@Slf4j
@Component
public class MigrationBundleStore {

    /** 只接受根目录下的小写 JSON 文件，防止越界路径 */
    private static final Pattern ENTRY = Pattern.compile("[a-z][a-z0-9-]*\\.json");
    private static final Pattern ID = Pattern.compile("[0-9a-f]{16}");
    /** 解压后的总大小上限，防止压缩炸弹 */
    private static final long MAX_UNZIPPED = 500L * 1024 * 1024;

    private final Path root;
    private final ObjectMapper objectMapper;

    public MigrationBundleStore(@Value("${zhul.upload.private-dir:./private-uploads}") String privateDir, ObjectMapper objectMapper) {
        this.root = Path.of(privateDir, "history-migration").toAbsolutePath().normalize();
        this.objectMapper = objectMapper;
    }

    public MigrationBundleVO save(MultipartFile zip) {
        if (zip == null || zip.isEmpty()) {
            throw new BizException("请选择迁移包（zip）");
        }
        Path tmp = null;
        try {
            Files.createDirectories(root);
            tmp = Files.createTempDirectory(root, "upload-");
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            long total = 0;
            int count = 0;
            try (InputStream in = new DigestInputStream(zip.getInputStream(), sha); ZipInputStream z = new ZipInputStream(in)) {
                for (ZipEntry e = z.getNextEntry(); e != null; e = z.getNextEntry()) {
                    if (e.isDirectory() || e.getName().startsWith("__MACOSX/")) {
                        continue;
                    }
                    if (!ENTRY.matcher(e.getName()).matches()) {
                        throw new BizException("迁移包里有不认识的文件：" + e.getName());
                    }
                    try (OutputStream out = Files.newOutputStream(tmp.resolve(e.getName()))) {
                        byte[] buf = new byte[64 * 1024];
                        for (int n = z.read(buf); n > 0; n = z.read(buf)) {
                            total += n;
                            if (total > MAX_UNZIPPED) {
                                throw new BizException("迁移包解压后超过 500MB");
                            }
                            out.write(buf, 0, n);
                        }
                    }
                    count++;
                }
                in.transferTo(OutputStream.nullOutputStream());
            }
            if (count == 0 || !Files.isRegularFile(tmp.resolve("customers.json"))) {
                throw new BizException("不是迁移包：里面没有 customers.json");
            }
            String id = HexFormat.of().formatHex(sha.digest()).substring(0, 16);
            Path dir = root.resolve(id);
            if (Files.isDirectory(dir)) {
                deleteTree(tmp);
            } else {
                Files.move(tmp, dir, StandardCopyOption.ATOMIC_MOVE);
            }
            tmp = null;
            log.info("历史迁移包已上传，id={}，文件数={}，解压后 {} 字节", id, count, total);
            return describe(id);
        } catch (IOException e) {
            throw new BizException("保存迁移包失败：" + e.getMessage(), e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        } finally {
            deleteTree(tmp);
        }
    }

    /** 迁移包目录；不存在时报错 */
    public Path dir(String id) {
        if (id == null || !ID.matcher(id).matches() || !Files.isDirectory(root.resolve(id))) {
            throw new BizException("迁移包不存在，请重新上传");
        }
        return root.resolve(id);
    }

    /** 迁移包概况：版本、各类数据的条数、图片数与链接到期时间 */
    public MigrationBundleVO describe(String id) {
        Path dir = dir(id);
        MigrationBundleVO vo = new MigrationBundleVO();
        vo.setId(id);
        Map<String, Integer> counts = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path p : files.sorted().toList()) {
                String name = p.getFileName().toString().replace(".json", "");
                JsonNode n = objectMapper.readTree(p.toFile());
                if ("manifest".equals(name)) {
                    vo.setVersion(n.path("version").asText(""));
                    vo.setLinksExpireAt(n.path("linksExpireAt").asText(""));
                    vo.setSourceExportedAt(n.path("sourceExportedAt").asText(""));
                } else {
                    counts.put(name, n.isArray() ? n.size() : 1);
                }
            }
        } catch (IOException e) {
            throw new BizException("读取迁移包失败：" + e.getMessage(), e);
        }
        vo.setCounts(counts);
        return vo;
    }

    private static void deleteTree(Path p) {
        if (p == null || !Files.exists(p)) {
            return;
        }
        try (Stream<Path> s = Files.walk(p)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(x -> {
                try {
                    Files.deleteIfExists(x);
                } catch (IOException e) {
                    log.warn("删除临时文件失败：{}", x);
                }
            });
        } catch (IOException e) {
            log.warn("删除临时目录失败：{}", p);
        }
    }
}
