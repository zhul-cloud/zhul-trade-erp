package com.zhul.erp.modules.system.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhul.erp.framework.storage.FileStorageService;
import com.zhul.erp.framework.storage.FileStorages;
import com.zhul.erp.framework.storage.OssFileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Map;

/**
 * 历史文件迁移与清理的命令入口（不在正常运行的服务里触发，避免误点）：
 * <pre>
 *   java -jar app.jar --zhul.storage.migrate=dry-run   预演迁移，只出报告
 *   java -jar app.jar --zhul.storage.migrate=run       迁移（可重复执行）
 *   java -jar app.jar --zhul.storage.cleanup=dry-run   预演清理
 *   java -jar app.jar --zhul.storage.cleanup=run       删除已核对的本地文件
 * </pre>
 * 执行完输出报告并退出；有失败的文件时退出码为 1。需要配置完整的 OSS（OSS_* 环境变量）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StorageMigrationRunner implements ApplicationRunner {

    private final JdbcTemplate jdbc;
    private final FileStorageService files;
    private final ObjectMapper objectMapper;
    private final ConfigurableApplicationContext context;

    @Value("${zhul.storage.migrate:}")
    private String migrate;
    @Value("${zhul.storage.cleanup:}")
    private String cleanup;
    @Value("${zhul.upload.private-dir:./private-uploads}")
    private String privateDir;
    @Value("${zhul.upload.dir:./uploads}")
    private String publicDir;

    @Override
    public void run(ApplicationArguments args) {
        if (migrate.isBlank() && cleanup.isBlank()) {
            return;
        }
        FileStorages storages = files.storages();
        OssFileStorage oss = storages.oss().orElse(null);
        int code;
        if (oss == null) {
            log.error("迁移与清理需要先配置完整的 OSS（OSS_ENDPOINT、OSS_ACCESS_KEY_ID、OSS_ACCESS_KEY_SECRET、OSS_PRIVATE_BUCKET、OSS_PUBLIC_BUCKET）");
            code = 2;
        } else if (!migrate.isBlank()) {
            boolean dry = !"run".equalsIgnoreCase(migrate);
            log.info("开始{}历史文件迁移", dry ? "预演" : "");
            code = print(new StorageMigration(jdbc, storages.local(), oss, files.workDir(), objectMapper, dry).migrate(),
                    dry ? "迁移预演" : "迁移");
        } else {
            boolean dry = !"run".equalsIgnoreCase(cleanup);
            log.info("开始{}清理本地文件", dry ? "预演" : "");
            code = print(new StorageMigration(jdbc, storages.local(), oss, files.workDir(), objectMapper, dry)
                            .cleanup(Path.of(privateDir).toAbsolutePath().normalize(), Path.of(publicDir).toAbsolutePath().normalize()),
                    dry ? "清理预演" : "清理");
        }
        int exit = code;
        System.exit(SpringApplication.exit(context, () -> exit));
    }

    private static int print(Map<String, StorageMigration.Stat> report, String title) {
        int failed = 0;
        log.info("========== {}报告 ==========", title);
        for (Map.Entry<String, StorageMigration.Stat> e : report.entrySet()) {
            StorageMigration.Stat s = e.getValue();
            log.info("{}：文件 {}，完成 {}，找不到 {}，失败 {}，原大小 {} MB，处理后 {} MB", e.getKey(), s.total, s.moved, s.missing, s.failed,
                    s.bytesBefore / 1048576.0, s.bytesAfter / 1048576.0);
            s.problems.forEach(p -> log.info("    {}", p));
            failed += s.failed;
        }
        return failed > 0 ? 1 : 0;
    }
}
