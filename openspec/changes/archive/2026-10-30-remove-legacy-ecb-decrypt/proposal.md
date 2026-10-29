## Why

`crypto-aes-gcm`（v1.3.12）把敏感数据改为 AES-256-GCM（`v2:`）存储，同时保留了旧格式（AES/ECB）的解密和启动时迁移，约定下一个版本删除。2026-10-30 已在生产、本机开发库、测试库核对全部加密列（系统参数加密项、收款人身份证号、`sys_ai_config.api_key`、`sys_ai_provider_config.api_key`），没有任何旧格式密文，生产启动日志也一直是「没有旧格式密文需要处理」。旧格式解密继续保留只会留下一条弱加密的读取路径。

## What Changes

- **BREAKING**：删除旧格式（AES/ECB）解密。没有 `v2:` 前缀的非空密文一律解密失败并报错，不再尝试按旧格式解开。
- 删除启动时的旧格式迁移任务 `EncryptionMigrationRunner` 及其测试。
- 删除 `AesUtils` 中旧格式相关代码（`decryptLegacy`、`isLegacy`、hutool `AES` 依赖的用法），更新类注释。

## Capabilities

### New Capabilities

（无）

### Modified Capabilities

- `system/data-encryption`：删除「旧格式密文的兼容与迁移」；「密文被篡改时拒绝解密」增加没有 `v2:` 前缀时报错的场景。

## Impact

- 后端：`common/utils/AesUtils.java`、删除 `modules/system/crypto/EncryptionMigrationRunner.java`；对应测试。
- 无数据库迁移，无 `.env` 变更，前端不变。
- 风险：如果某个环境仍残留旧格式密文，读取时会报「数据解密失败」。已核对的三个库都没有；其他环境（如某人的本机库）若有，先用上一版本启动一次完成迁移再升级。
