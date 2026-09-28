## Context

- 客户表已有来源渠道（`source_channel`，8 个枚举值）、负责人（`owner_id`）、联系人、邮箱、WhatsApp、电话，以及「名称 + 国家」查重（`name_key`）；数据权限 `DataScope`（全部 / 自定义部门 / 仅本人）已在客户模块使用
- 客户询盘：`customer_id` 必填，附件经 `AttachmentStorageService` 存公开目录 `/uploads/customer-inquiry`，供 AI 解析读取
- 供应商附件已有私有存储（`SupplierAttachmentStorage`：非公开目录、文件头校验、租户分目录、经权限接口下载）
- 讨论结论见 proposal：一个客户最多一条商机；老客户新需求走客户询盘；阶段前期全部手动；SOP 清单第二期

## Goals / Non-Goals

**Goals:** 一次录入同时得到客户档案和商机；统计口径稳定（按首次接触日期归属、有效 = 曾进入 S3）；阶段是配置数据，第二期挂清单不改结构。

**Non-Goals:** SOP 清单、阶段自动推进、渠道接口拉取、小满同步、跟进提醒。

## Decisions

### 1. 数据结构（新迁移）

```
opportunity（业务表）
  id, tenant_id, opportunity_code  OPP{yyyyMMdd}{NNN}
  customer_id        一个客户最多一条未删除商机（应用层保证）
  source_channel     同客户来源渠道枚举
  first_contact_date date，统计归属日
  owner_id
  stage_code         当前阶段（opportunity_stage.code）
  stage_changed_at   最近一次阶段变更时间（列表页「S1 超过 2 天未推进」计数用，只统计不提醒）
  reopen_stage_code  进入结束状态前的阶段，重新打开时回到这里
  close_reason tinyint, close_note varchar(500), closed_at
  reached_valid      tinyint，是否曾进入 S3 及以后（统计用，只会从 0 变 1）
  demand_summary     varchar(2000)
  deleted_at + 审计字段
  索引：(tenant_id, first_contact_date)、owner_id、customer_id、stage_code

opportunity_stage（阶段配置，平台级 tenant_id=0，本期只读）
  code  S1..S7 / WON / LOST / INVALID
  name, sort_order
  category  1-进行中 2-赢单 3-输单 4-无效
  counts_as_valid  进入该阶段即计为有效（S3–S7、赢单为 1）
  （第二期：opportunity_stage_task 挂清单项，opportunity_task_check 记勾选）

opportunity_stage_log
  opportunity_id, from_stage, to_stage, reason, note, operator(create_by), create_time

opportunity_attachment（同 supplier_attachment 结构，file_key 指私有目录）

customer 新增 email_key / whatsapp_key / phone_key（规整后的比较键，建索引），存量回填
customer_inquiry 新增 opportunity_id（可空，建索引）
```

`reached_valid` 冗余存储而不是每次从日志计算：统计是高频查询，按首次接触日期分组后直接 `SUM`，日志只用于展示与追溯。

### 2. 登记事务与查重

`crm` 模块的商机服务在一个事务里：① 查重 ② 通过客户服务的内部方法创建客户（允许无名称，写入联系方式比较键）③ 创建商机与第一条阶段记录 ④ 关联本次上传的附件。查重在客户服务里实现（客户是查重对象），商机服务只调用，避免 crm 直接读客户表。

比较键：邮箱转小写去空格；WhatsApp、电话只保留数字，去掉开头的 `00` 国际前缀（`0049…` 与 `+49…` 视为相同）；少于 6 位数字不参与比较（防误伤「123」之类的占位）。客户管理页新增 / 编辑时同步维护比较键，但本次不改变客户管理页的查重行为。

### 3. 无名称客户

`customer.name` 保持 `NOT NULL DEFAULT ''`，空串表示未填写；出参新增 `displayName`（有名称用名称，否则联系人名称）和 `nameMissing`。客户管理页编辑时名称仍必填（前端与 `UpdateCustomerRequest` 校验不变），只有商机登记路径跳过名称校验。报价单、PI 等出单环节的强制补全留给对应模块。

### 4. 阶段规则放在服务层

合法变更：进行中阶段之间任意前进 / 回退；S1、S2 → 无效；S3–S7 → 输单；S7 → 赢单；结束状态 → 重新打开回到 `reopen_stage_code`。进入 S3–S7 或赢单时 `reached_valid = 1`。规则按 `opportunity_stage.category`、`sort_order` 与 `counts_as_valid` 判断，不写死阶段编码。

### 5. 附件：抽出通用私有存储

把 `SupplierAttachmentStorage` 抽成 `framework/storage/PrivateFileStorage`（按模块前缀 + 允许类型构造），供应商与商机各用一个实例；对外行为不变，供应商附件测试保持通过。商机附件允许 JPG、PNG、xlsx、xls、csv（Excel 按 ZIP / OLE 文件头，csv 按扩展名 + 文本内容），≤10MB，≤10 个。

### 6. 转询盘

前端从商机详情跳到新建客户询盘，带 `opportunityId`；询盘页预填客户和需求摘要。附件：用户在询盘页勾选要带过去的商机附件，后端把文件**复制**到询盘附件存储（询盘 AI 解析读取的是询盘附件地址）。询盘保存时校验来源商机与客户一致。

### 7. 统计

`GET /api/v1/crm/opportunities/stats?from&to&groupBy=channel|owner|date` 一条 `GROUP BY` 查询，指标 `COUNT(*)`、`SUM(stage=INVALID)`、`SUM(reached_valid)`、`SUM(stage=WON)`、`SUM(stage=LOST)`；数据权限作用在 `owner_id` 上。有效率由前端计算并按一位小数显示。日期范围上限 366 天。

### 8. 菜单与权限

「询盘管理」下新增菜单「商机管理」（`/inquiry/opportunities`，排在询盘单列表之前），页面内两个页签：商机列表、每日统计。按钮权限：`crm:opportunity:add`（登记）、`crm:opportunity:edit`（编辑基本信息、阶段操作）、`crm:opportunity:export`（导出列表，本期可不做前端入口）。附件下载要求能访问该菜单。

## Risks / Trade-offs

- **[统计依赖录入完整度]** 业务员漏登记则统计偏低 → 列表与统计都按业务员拆分，便于主管核对；本期不做强制
- **[比较键误判]** 共用座机或公司总机可能让两个不同客户电话相同 → 只作为拒绝登记的依据时会误伤；提示中给出已有客户与负责人，由业务员找负责人确认。后续如误伤多，可把电话降为「提示但允许」
- **[询盘附件公开]** 复制到询盘的附件进入现有的公开目录，这是询盘模块的既有问题，本次不扩大、单独立项修复
- **[与小满重复]** 国际站新客可能同时在小满跟进 → 本期接受，ERP 以「新客登记与统计」为主，跟进细节仍可在小满
