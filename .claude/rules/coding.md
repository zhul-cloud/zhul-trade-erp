# 编码规范

## 数据库 — 建表规范

### 字段规范

- 金额：`DECIMAL(18,2)`，绝对不用 FLOAT
- 状态字段：`tinyint(2) NOT NULL DEFAULT 1`，COMMENT 必须列举枚举值，如 `'状态（0-禁用、1-启用）'`
- 时间字段：`datetime NOT NULL DEFAULT CURRENT_TIMESTAMP`，存本地时间（业务展示层无需转换）
- 表名、字段名：统一 snake_case，反引号包裹

### 必填字段（所有表）

```sql
`id`          int(11)     NOT NULL AUTO_INCREMENT COMMENT '主键',
`create_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
`create_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '创建人',
`update_time` datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
`update_by`   varchar(32) NOT NULL DEFAULT 'sys' COMMENT '更新人',
PRIMARY KEY (`id`)
```

### 业务表额外必填字段

业务表（订单、凭证、采购单、客户等）额外加：

```sql
`tenant_id`   int(11)     NOT NULL DEFAULT 0  COMMENT '租户ID',
`deleted_at`  datetime    NULL                COMMENT '软删除时间，NULL表示未删除',
```

基础表（用户、权限、字典、资源等）只用 `status`，不加 `deleted_at`。

### 建表风格

- 统一使用反引号 + NOT NULL DEFAULT 风格（参考 resource 表）
- 每个字段必须有 COMMENT，枚举值在注释中列出
- ENGINE=InnoDB，DEFAULT CHARSET=utf8mb4
- AUTO_INCREMENT 起始值按表设置（避免 id=1 歧义），如用户表从 1000000000 起

### 编码字段规范

- 业务编码格式：前缀缩写 + 主键，如 `DP10000`（部门）、`RS100000`（资源）
- 编码字段用 `varchar`，长度按实际前缀+位数设置，COMMENT 注明格式

### 索引规范

- tenant_id 列：必须加索引
- status 列：必须加索引
- 外键关联列（如 order_id, customer_id）：必须加索引
- 时间范围查询列（create_time, deleted_at）：必须加索引
- **不使用数据库外键约束**，一致性由应用层代码保证

### 禁止事项

- 禁用 FLOAT/DOUBLE 存金额
- 禁止物理删除业务数据，统一用 deleted_at 软删除
- 禁止直接修改已锁定凭证（status=LOCKED）的任何字段

## API 设计

- RESTful 风格，资源用复数名词：/orders, /customers
- 分页参数统一：page, page_size，响应包含 total
- 错误响应格式：{"code": "ORDER_NOT_FOUND", "message": "...", "detail": {...}}
- 所有金额在 API 响应中附带 currency_code

## 安全规范（阿里规约）

- 所有用户输入必须服务端校验，禁止只依赖前端验证
- SQL 参数必须用参数绑定（`#{}`），禁止字符串拼接防 SQL 注入
- 表单/接口必须有 CSRF 防护（Spring Security 默认开启，禁止关闭）
- 接口限流：高频/资源密集操作加速率限制（如汇率查询、报表导出）
- 所有金融操作记录操作人（`operator_id`）和操作时间
- 敏感操作（修改收款信息、调整汇率）需二次确认
- 防止并发重复提交：幂等键（`idempotency_key`）
- 敏感数据（手机号、邮箱、银行卡号）日志中脱敏展示

## 测试要求（AIR 原则）

- **自动化**：所有测试必须可自动运行，不依赖人工输入
- **独立性**：用例之间不互相依赖，可单独执行
- **可重复**：任何环境执行结果一致，不依赖外部状态

覆盖率要求：
- 整体语句覆盖率 ≥ 70%
- 财务计算、状态流转等核心模块覆盖率 = 100%

用例分类（BCDE 原则）：
- 边界值（Boundary）：零值、负值、最大金额、临界日期
- 正确输入（Correct）：正常业务场景
- 设计（Design）：状态机合法/非法转换
- 错误输入（Error）：非法参数、权限不足、并发冲突

特殊要求：
- 财务计算函数：必须有单元测试，覆盖零值/负值/超大金额/多币种
- 状态流转：必须有集成测试验证合法和非法转换
- API：必须有契约测试

## Redis 规范

### Key 命名

- 格式：`zhul:{模块}:{数据类型}:{唯一标识}`
- 示例：
  - `zhul:erp:rate:USD:CNY` — 汇率缓存
  - `zhul:erp:session:{token}` — 会话
  - `zhul:erp:lock:order:{orderId}` — 分布式锁
  - `zhul:erp:list:currency` — 货币列表
- 禁止使用空格、大写字母；层级用冒号分隔

### TTL 规范（统一固定值）

| 数据类型 | TTL |
|---------|-----|
| 汇率缓存 | 86400s（1天） |
| 用户会话 / Token | 7200s（2小时） |
| 列表/字典缓存 | 300s（5分钟） |
| 分布式锁 | 30s（看门狗自动续期） |
| 验证码 | 300s（5分钟） |

### 分布式锁

- 统一使用 Redisson，不手写 SET NX + Lua
- 锁 Key 格式：`zhul:erp:lock:{业务}:{唯一ID}`
- 必须在 finally 块中释放锁，避免死锁
- 防止幂等重复提交：业务操作前先获取锁，成功后写入 idempotency_key

### 缓存使用原则

- 读多写少的基础数据才缓存（汇率、字典、货币列表）
- 业务单据（订单、凭证）不缓存，直接查库
- 缓存更新策略：先更新数据库，再删除缓存（Cache-Aside）
- 多租户缓存 Key 必须带 tenant_id：`zhul:erp:rate:{tenantId}:USD:CNY`

## Java 代码分层规范

- 严格三层：Controller → Service → Repository，禁止跨层调用
- Controller：只做参数校验（@Valid）和响应封装，不写业务逻辑
- Service：业务逻辑层，事务边界在此声明（@Transactional）
- Repository：继承 JPA/MyBatis，不写业务判断
- DTO/VO 分离：Request DTO 入参，Response VO 出参，Entity 不暴露到 Controller 层
- 包结构：`com.zhul.erp.{模块名}.{controller|service|repository|dto|entity}`
- 模型命名：`XxxDO`（数据库实体）、`XxxDTO`（跨层传输）、`XxxVO`（前端响应）、`XxxQuery`（查询入参）

## 命名规范（阿里规约）

- 类名：UpperCamelCase，如 `OrderService`、`ExchangeRateJob`
- 抽象类：前缀 `Abstract`，如 `AbstractVoucherService`
- 异常类：后缀 `Exception`，如 `OrderNotFoundException`
- 方法名/变量名：lowerCamelCase，如 `calcAmountInCny()`
- 常量：全大写+下划线，如 `MAX_RETRY_COUNT`，定义在专用 Constants 类
- 禁止：以下划线或 `$` 开头/结尾；中英文混拼（如 `getPingFenByName`）
- 布尔类型：POJO 字段不加 `is` 前缀（用 `deleted` 不用 `isDeleted`），数据库字段用 `is_xxx`
- 包名：全小写，单数形式，如 `controller` 不用 `controllers`

## 代码格式

- 缩进：4个空格，禁用 Tab
- 单行最长：120字符
- 大括号：左括号不换行，右括号独占一行
- 运算符两侧加空格：`amount = price * quantity`
- 文件编码：UTF-8

## 异常处理（阿里规约）

- 禁止用异常做流程控制（如用 try-catch 判断 null）
- catch 要区分异常类型，不能笼统 catch `Exception`
- 资源释放统一用 try-with-resources，不在 finally 里手动 close
- 向上抛出异常时必须保留原始异常：`throw new BizException("...", e)`
- 禁止 catch 后吞掉异常（空 catch 块）
- 自定义业务异常继承 `RuntimeException`，命名以 `Exception` 结尾

## 日志规范（SLF4J）

- 统一使用 SLF4J，禁止直接使用 Log4j / System.out
- 使用占位符，禁止字符串拼接：`log.info("订单创建，orderId={}", orderId)`
- 日志级别：
  - DEBUG：开发调试，生产关闭
  - INFO：关键业务节点（订单创建、凭证生成、汇率更新）
  - WARN：可恢复的异常（重试、降级）
  - ERROR：需人工介入的错误
- 日志保留：生产环境 ≥ 15 天
- 禁止在循环中打印大量日志

## ORM / MyBatis 规范（阿里规约）

- 禁用 `SELECT *`，必须列出所需字段
- 参数绑定用 `#{}`，禁用 `${}` 防 SQL 注入
- 更新操作必须同时更新 `update_time` 字段
- 超过 3 张表禁止 JOIN，拆分为多次查询在 Service 层组装
- 判断 NULL 用 `IS NULL` / `IS NOT NULL`，禁用 `= NULL`
- 禁用存储过程
- count 统计用 `COUNT(*)`，不用 `COUNT(列名)`

## OOP 规约（阿里规约）

- 所有重写方法必须加 `@Override` 注解
- POJO 类属性使用包装类型（`Integer`/`Long`/`BigDecimal`），本地变量使用基本类型（`int`/`long`）
- 所有 POJO 类必须实现 `toString()`，用 IDE 或 Lombok `@ToString` 生成
- 构造方法只做赋值，复杂初始化逻辑放 `init()` 方法

## 集合处理（阿里规约）

- 重写 `equals()` 必须同时重写 `hashCode()`
- 禁止在 foreach 循环中删除/添加集合元素，需要修改时改用 `Iterator`
- 初始化集合时指定容量，如 `new ArrayList<>(16)`，避免频繁扩容
- 遍历 Map 用 `entrySet()`，禁用 `keySet()` + `get()` 二次查找

## 并发规范（阿里规约）

- 禁止手动 `new Thread()`，统一使用线程池（Spring `@Async` 或自定义 `ThreadPoolExecutor`）
- 禁止用 `Executors` 创建线程池（存在资源耗尽风险），必须用 `ThreadPoolExecutor` 显式配置
- 线程池必须命名，如 `finance-async-pool`
- `SimpleDateFormat` 线程不安全，改用 `DateTimeFormatter`（Java 8+）
- 共享变量可见性问题用 `volatile`；计数器用 `AtomicInteger`/`AtomicLong`
- 多锁场景保持一致的加锁顺序，防止死锁
- 单例 Bean（Spring 默认）中禁止使用实例变量保存请求状态，用 ThreadLocal 或方法参数传递

## API 版本规范

- 统一前缀：/api/v1/{resource}
- 版本升级时新建 v2 包，不修改 v1
- 响应统一封装：{"code": 0, "data": {}, "message": "ok"}

## 多租户规范

- tenant_id 从 JWT Token 解析，存入 ThreadLocal（TenantContext）
- 所有业务表必须有 tenant_id 字段，且加索引
- Repository 层查询必须自动注入 tenant_id 过滤（MyBatis 拦截器或 JPA @Filter）
- 禁止在业务代码中手动传递 tenant_id 参数，统一从 TenantContext 获取
- 超级管理员（platform admin）可跨租户查询，需显式标注

## 前端规范

- 状态管理：使用 Ant Design Pro 自带 umi model（dva），按模块拆分 model 文件
- 请求：统一使用 umi-request，在 request.ts 中配置拦截器（自动带 token）
- 表格/表单：优先使用 ProTable / ProForm，减少重复代码
- 金额展示：所有金额字段使用统一的 formatAmount(value, currency) 工具函数
- 路由：按域划分菜单，与后端模块对应（/trade, /purchase, /finance, /warehouse）
