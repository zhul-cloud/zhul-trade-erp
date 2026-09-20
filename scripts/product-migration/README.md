# 商品主数据迁移（独立站 → ERP）

一次性迁移，把独立站 `fouwell-website/js/data.js` 里的 87 个商品导入 v1.2.0 的商品主数据。设计依据见
`openspec/changes/archive/2026-09-20-add-product-master-core/design.md` 的「Migration Plan」。**没有任何一步会写库，除非你自己执行生成的 SQL。**

## 步骤

```bash
# 1. 干跑：只输出统计与异常，不写库
node scripts/product-migration/dry-run.js

# 2. 人工复核 FAQ 排除清单；误伤的条目加进 faq-allowlist.json 后再干跑

# 3. 生成 SQL（目标库默认 zhul_erp；先在 zhul_erp_test 上演练）
node scripts/product-migration/generate-sql.js --db zhul_erp_test

# 4. 执行（先在测试库空库上演练，再对目标库执行）
mysql -uroot -p < scripts/product-migration/out/migrate.sql

# 5. 核对：与独立站原文逐项比对
node scripts/product-migration/verify-import.js --db zhul_erp_test

# 回滚（软删除所有 create_by='migration' 的行）
mysql -uroot -p < scripts/product-migration/out/rollback.sql
```

数据源默认 `~/Documents/llm-wiki/fouwell-website/js/data.js`，可用环境变量 `FOUWELL_DATA_JS` 指定。

## 性质

- **幂等**：`migrate.sql` 重复执行不产生重复行，也不覆盖人工改过的内容（命中唯一键或已存在的行原样保留）。
- **回滚是软删除**：软删除的行仍占着唯一键，回滚后再次执行 `migrate.sql` 不会让它们复活，需要重新导入先人工恢复或清理。
- **不迁移**：实拍图与营销图、采购报价（14 条）、成色备注、品牌 Logo；物流、海关、图片视频为空。
- **不静默丢弃**：所有被排除、需人工处理、超出字段长度的条目都会出现在干跑报告里。

## 测试

```bash
node --test scripts/product-migration/migration.test.js
```
