package com.zhul.erp.framework.storage;

import com.zhul.erp.common.exception.BizException;
import org.springframework.beans.factory.DisposableBean;

import java.util.Optional;

/**
 * 可用的存储方式：本地始终可用；OSS 配置完整时可用。新文件写 current，读取按记录上的存储方式选择，
 * 所以切回本地后，已迁到 OSS 的文件仍能读取（只要 OSS 配置还在）。
 */
public class FileStorages implements DisposableBean {

    private final LocalFileStorage local;
    private final OssFileStorage oss;
    private final FileStorage current;

    public FileStorages(StorageProperties props, LocalFileStorage local, OssFileStorage oss) {
        if (props.ossSelected()) {
            if (!props.getOss().missing().isEmpty()) {
                throw new IllegalStateException("文件存储选择了 OSS，但缺少配置：" + String.join("、", props.getOss().missing()));
            }
            if (oss == null) {
                throw new IllegalStateException("文件存储选择了 OSS，但 OSS 存储没有初始化");
            }
        } else if (!"local".equalsIgnoreCase(props.getType())) {
            throw new IllegalStateException("文件存储方式只支持 local 或 oss，当前为：" + props.getType());
        }
        this.local = local;
        this.oss = oss;
        this.current = props.ossSelected() ? oss : local;
    }

    /** 新上传文件写入的存储方式 */
    public FileStorage current() {
        return current;
    }

    public LocalFileStorage local() {
        return local;
    }

    public Optional<OssFileStorage> oss() {
        return Optional.ofNullable(oss);
    }

    /** 按记录上的存储方式选择；为空按本地处理（迁 OSS 之前的旧记录） */
    public FileStorage of(String code) {
        if (code == null || code.isBlank() || FileStorage.LOCAL.equalsIgnoreCase(code)) {
            return local;
        }
        if (FileStorage.OSS.equalsIgnoreCase(code)) {
            if (oss == null) {
                throw new BizException("文件存放在 OSS，但当前没有配置 OSS，请联系管理员");
            }
            return oss;
        }
        throw new BizException("不支持的存储方式：" + code);
    }

    @Override
    public void destroy() {
        if (oss != null) {
            oss.destroy();
        }
    }
}
