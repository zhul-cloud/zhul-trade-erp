# Tasks

## 1. 加解密

- [x] 1.1 `AesUtils` 新增 HKDF-SHA256 密钥派生（salt `zhul-erp`、info `data-encryption/v2`，输出 32 字节），用 JDK `Mac` 实现。验证：单元测试用 RFC 5869 测试向量 1（SHA-256）比对输出一致
- [x] 1.2 `AesUtils.encrypt` 改为 AES-256-GCM（12 字节随机初始向量、128 位标签），输出 `v2:` + Base64(初始向量 ‖ 密文 ‖ 标签)；空值原样返回。验证：单元测试覆盖往返、同一明文两次密文不同且都以 `v2:` 开头、空字符串与 null、中文与 18 位身份证号
- [x] 1.3 `AesUtils.decrypt` 按前缀分流：`v2:` 走 GCM，其他走旧 ECB（保留现有实现）；GCM 认证失败抛出业务可识别的异常，异常信息不含密文或明文。验证：单元测试覆盖改动密文任一字节、截断、换 `AES_KEY` 均报错；用旧实现生成的 hex 密文能正确解密

## 2. 启动时迁移

- [x] 2.1 新增 `EncryptionMigrationRunner`（`ApplicationRunner` + `JdbcTemplate`）：迁移 `sys_config`（`is_encrypted = 1`、非空、非 `v2:`，含软删除行）与 `supplier_bank_account.payee_id_no`（非空、非 `v2:`）；逐条带旧值条件更新，同时更新 `update_time`；单条失败记录表名和 id 后继续；结束打印每张表的迁移数与失败数。验证：见 2.2
- [x] 2.2 集成测试：预置不同租户的旧格式加密配置、旧格式身份证号、一条损坏的旧密文和一条已是 `v2:` 的记录，执行迁移后断言：旧记录变为 `v2:` 且解密结果与原值相同；损坏记录保持原样；`v2:` 记录未改变；再次执行无任何更新；日志不含明文

## 3. 回归与验证

- [x] 3.1 回归：`SupplierBankAccountSyncTest`、系统参数相关测试（含加密配置留空不修改、返回 `****`）全部通过；`mvn test` 全量通过（结果：1071 个测试，唯一失败 `ImageCompressorTest.pngScreenshot_staysPng_longEdge2048` 在 `release/1.3.0` 基线上同样失败，与本变更无关）
- [x] 3.2 本机验证：用旧版本写入一个加密配置和一个对私收款账户的身份证号，切换到新版本启动，日志显示迁移汇总且失败数为 0；数据库字段以 `v2:` 开头；供应商详情身份证号脱敏显示与迁移前一致；新建账户写入即为 `v2:`
- [x] 3.3 `openspec validate crypto-aes-gcm --strict` 通过，改动文件为 LF

## Workflow follow-up

- 与本分支一起归档本变更，PR 发往 `release/1.3.0`。PR 说明写明：无 `.env` 变更、无 Flyway 迁移；启动时自动重新加密已有密文；迁移后回退旧版本读不出 `v2:` 密文，需重新录入；迁移完成前不要更换生产 `AES_KEY`。
- 下一个版本确认生产迁移完成后，另开变更删除旧 ECB 解密分支。
- 本变更完成后再开始 `ai-config-per-tenant`。
