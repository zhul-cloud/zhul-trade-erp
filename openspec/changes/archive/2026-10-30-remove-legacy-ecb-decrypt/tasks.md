# Tasks

## 1. 数据核对

- [x] 1.1 生产、本机开发库、测试库核对四个加密列（`sys_config.config_value`（`is_encrypted = 1`）、`supplier_bank_account.payee_id_no`、`sys_ai_config.api_key`、`sys_ai_provider_config.api_key`）中非空且不以 `v2:` 开头的记录数。（结果：2026-10-30 三处均为 0）

## 2. 后端

- [x] 2.1 `AesUtils`：删除 `decryptLegacy`、`isLegacy` 与 hutool `AES`，非空且没有 `v2:` 前缀时抛「数据解密失败：密文格式不正确」；更新类注释。
- [x] 2.2 删除 `EncryptionMigrationRunner` 及其集成测试。
- [x] 2.3 测试：`AesUtilsTest` 改为断言旧格式与任意无前缀值解密失败；加密相关调用方（系统参数、收款账户、AI 配置）测试通过。（结果：AesUtils 9、AiConfigContract 14、SupplierBankAccountSync 7、BankAccountContract 3、Config 相关 14 个通过）

## 3. 文档

- [x] 3.1 检查部署与安全文档中关于旧格式迁移的说明，同步改为已删除。（结果：docs、AGENTS.md、.claude 中没有相关说明，无需修改）
