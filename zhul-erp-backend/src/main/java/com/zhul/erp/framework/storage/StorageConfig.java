package com.zhul.erp.framework.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 装配存储方式：选择 OSS 但配置不完整时启动失败，并说明缺少哪一项 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public LocalFileStorage localFileStorage(@Value("${zhul.upload.private-dir:./private-uploads}") String privateDir,
                                             @Value("${zhul.upload.dir:./uploads}") String publicDir,
                                             @Value("${jwt.secret}") String secret) {
        return new LocalFileStorage(privateDir, publicDir, secret);
    }

    @Bean
    public FileStorages fileStorages(StorageProperties props, LocalFileStorage local) {
        boolean ossReady = props.getOss().missing().isEmpty();
        OssFileStorage oss = ossReady ? new OssFileStorage(props.getOss()) : null;
        return new FileStorages(props, local, oss);
    }
}
