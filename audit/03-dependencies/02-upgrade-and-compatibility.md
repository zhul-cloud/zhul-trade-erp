# 依赖升级与兼容性路线

核查时间：2026-11-01 UTC。本文是待整改方案，未实施、未宣称新版本兼容。安全公告的历史最低修复版本仅关闭对应漏洞，不能自动作为当前推荐目标；完整版本证据见 [清单](03-version-inventory.md)，安全条目见 [安全问题](01-security-findings.md)。

## DEP-007 / P1 / Boot 3.2.5 基线滞后，应将 BOM 与生态组合升级

当前 Java 17 + Boot 3.2.5 管理 Framework 6.1.6、Security 6.2.4、Tomcat 10.1.20、Netty 4.1.109.Final、Jackson 2.15.4、Flyway 9.22.3。多项核心组件的修复缺口会持续出现；逐一覆盖 jar 可能破坏官方验证组合。2026-11 核查的官方 Boot 项目页列 4.1.2，不能把“3.2.5 升到 3.2 最后一个 patch”当成长期方案，也不能照搬 2025 的“3.5 是最新受支持版本”建议。

官方生命周期 API 本轮实读：3.2.x OSS 支持结束 2024-12-31、商业支持结束 2025-12-31；3.5.x OSS 支持结束 2026-06-30、商业支持至 2032-06-30；4.1.x OSS 支持至 2027-07-31，目前 OSS patch 4.1.2。来源：[Spring 官方生命周期 API](https://api.spring.io/projects/spring-boot/generations)。

建议分两步交付：先在现架构内修补 DEP-001/002 的生产风险并形成回归基线；再建立 Boot 4.1 当前稳定 patch 的兼容验证分支，按 3.3/3.4/3.5 的逐代变更审阅后迁移 4.x。若因业务窗口只能停在 3.x，明确该停留是过渡，并核实供应商安全支持资格及补丁可获得性。未取得正式支持不等于 Maven Central 存在一个版本就仍有持续安全维护。

升级验收应覆盖：Security filter/matcher、异步 dispatcher、JWT、验证错误；Jackson 序列化尤其金额/时区；MyBatis 多租户 SQL 注入和分页；Flyway MySQL 模块与既有迁移校验；Redis/Lettuce；邮件；API 文档。不能直接在生产数据上验证 schema 迁移。本轮不生成数据库变更。

兼容联动：

| 当前组合 | 升级要点 | 验收要求 |
| --- | --- | --- |
| Knife4j 4.4.0 → springdoc 2.3.0 | springdoc 对 Boot 主/次版本有兼容矩阵；Boot 4 使用 springdoc 3.x，不能只升 Boot 保留旧文档启动器 | 应用启动、`/v3/api-docs`、接口/鉴权展示、异常处理器解析；生产暴露策略另见后端/安全审计 |
| MyBatis-Plus 3.5.6 / JSqlParser 4.9 | 官方 3.5.9+ 将 SQL parser 插件拆成可选模块；Boot 4 另有 boot4 starter，自 3.5.13 起提供 | 明确引入 parser 模块，回归租户过滤、分页、逻辑删除与复杂 JOIN，不能只检查编译 |
| Jackson 2.15.4 | Boot 4 默认 Jackson 3 及包/API 转变，按迁移指南选择迁移或临时兼容模块 | DTO、日期、BigDecimal、AI JSON schema/工具参数与第三方库 |
| Flyway core/mysql 9.22.3 | 维持模块版本一致；Boot 4 starter 体系变化须审阅 | 空库初始化与已有库迁移校验、失败回滚策略，不实连生产 |
| Lombok 1.18.30 / MapStruct 1.5.5.Final | 构建期工具与 JDK 绑定；当前 JDK17 不要求随 Boot 升到更高 JDK | 注解生成、映射字段完整性；如升 JDK 再选择支持的新版本 |

官方来源：[Boot 当前版本](https://spring.io/projects/spring-boot/)、[Boot 支持策略](https://github.com/spring-projects/spring-boot/wiki/Supported-Versions)、[Boot 4 迁移指南](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)、[springdoc 兼容说明](https://springdoc.org/)、[MyBatis-Plus 安装与插件拆分](https://baomidou.com/en/getting-started/install/)。

## DEP-008 / P2 / Node >=20 声明不满足锁定工具链

`package.json.engines.node` 与前端 AGENTS 均写 Node >=20，但 lock 的 commitlint 21 要求 >=22.12.0、lint-staged 17.0.2 要求 >=22.22.1、react-doctor 0.2.1 要求 >=22。因此在符合项目声明的 Node20 下安装/提交工具可能警告或失败；Docker 前端 node24 满足这些条件，本机本轮为 node26，不构成 Node20 验证。

建议将开发、CI、Docker 标准统一到已验证的 Node24 稳定 patch，并用可追踪的版本文件/`engines`/文档表达；或将工具降到明确支持 Node20 的组合后验证，避免只改文档。`@types/node@25.9.1` 超前于 Docker24，可能使类型检查接受不存在的运行 API；按实际 Node24 对齐类型版本。版本约束证据来自项目 lock 的包 `engines`，不是凭工具大版本猜测。

验收：干净环境 `npm ci`，lint/tsc/Jest/antd lint，husky/commitlint/lint-staged 和生产 build；明确是否启用 engine-strict，避免只在 Node26 的本机“能跑”即认定可复现。

## DEP-009 / P2 / Umi 升级必须和 @utoo/pack 本地补丁同步验收

当前 `@umijs/max/lint@4.6.51`，`@umijs/bundler-utoopack` 精确带 `@utoo/pack@1.4.3`，`patches/@utoo+pack+1.4.3.patch` 由 postinstall 应用，修复 worker 错池的间歇构建失败。任何 lock 更新、覆盖 pack 或跨版本升级都有补丁漂移风险。`npm audit fixAvailable:false` 也可能只是当前父包约束无法自动修复，并非上游永远无修复。

建议 Umi 家族同批升级，先核对上游 worker 修复是否已实际进入所选 pack 发布版，再移除/重制补丁；不要只因 PR 已合并就删补丁。验收至少包含 Linux Docker 与 Windows 开发环境、从干净 npm 缓存安装时补丁可应用、连续多次生产构建、Less/PostCSS/Tailwind 组合与资源加载。Ant Design Pro 模板不是本项目升级基线；UI 库兼容以 antd6/ProComponents3/React19 的实际 peer 为准。

本轮 npm 官方 latest 元数据：`@umijs/max 4.7.29`、`@utoo/pack 1.5.36`、`axios 1.21.0`、`dompurify 3.4.17`。这些是候选调研入口，未验证组合兼容，不能强行单独 override。来源：[Umi registry](https://registry.npmjs.org/@umijs/max/latest)、[pack registry](https://registry.npmjs.org/@utoo/pack/latest)、[axios registry](https://registry.npmjs.org/axios/latest)、[DOMPurify registry](https://registry.npmjs.org/dompurify/latest)。

特别关注直接 `@ant-design/pro-components@3.1.12-0` 仍为预发布语义，而 Umi 内部保留 2.x 副本；不能仅看根版本认为已统一。官方 latest tag 本轮仍为 2.8.10，与本项目采用的 3.x 预发布通道不同，不建议照 latest 标签直接降级。来源：[ProComponents registry](https://registry.npmjs.org/@ant-design/pro-components/latest)。react/react-dom 均锁 19.2.5，现有直接 UI peer 范围未发现根版本冲突；这不代表端到端行为已经验证。

## DEP-010 / P2 / Python 顶层精确 pin 仍不足以重现传递依赖与二进制

requirements 顶层六包都 pin，但 httpx/httpx2、pydantic、anyio、jsonschema 子依赖和 CLI 二进制仍通过依赖范围解析，没有带 hash 的完整锁。Docker `python:3.12-slim`、Node22 CLI 下载 stage、pip 版本也浮动；同一 Git 版本重建可得到不同运行组合。

2026-11-01 对 PyPI 官方元数据只读核查：

| 包 | 当前 pin | 官方 current metadata | 处理建议（非漏洞结论） |
| --- | --- | --- | --- |
| anthropic | 1.14.0 | 2.3.0；Python >=3.10 | 已跨主版本，先读迁移说明，回归 streaming/tool use/schema/超时重试；不可为追最新直接替换 |
| openai | 3.28.0 | 3.42.0；Python >=3.10 | 在 3.x 内验证更新，核查 httpx2/anyio 与 SDK 异常结构 |
| openai-codex | 0.163.0 | 0.163.0；Python >=3.10 | 当前顶层与 metadata一致；其 cli-bin 精确0.163.0，SDK内部生成 API仍需锁定 |
| jsonschema | 4.26.0 | 4.26.0；Python >=3.10 | 顶层无需因“有新版”升级；锁定 referencing/rpds 等传递版本 |
| PyMuPDF | 1.27.2.2 | 2.0；Python >=3.11 | 主版本及依赖面变化较大，PDF页渲染路径与镜像体积/许可证另行验证，不自动建议追最新 |
| Pillow | 13.0.0 | 13.0.0；Python >=3.11 | 顶层与 metadata一致；Docker3.12符合声明，仍需镜像架构 wheel 和图像资源边界验证 |

建议形成 Python3.12+目标Linux架构的完整解析锁/hash并在隔离构建核验，运行现有 mock 单元测试，补充只读解析测试；不调用真实 AI 服务。Claude Code 2.1.311 的复制路径 `bin/claude.exe` 和 Codex cli-bin 安装符号链接均是容器构建契约，应验证目标 linux/amd64 或 arm64 上可执行及 `--version`，不要从 Windows 本机能启动推断容器兼容。

PyPI 一手来源：[anthropic](https://pypi.org/pypi/anthropic/json)、[openai](https://pypi.org/pypi/openai/json)、[openai-codex](https://pypi.org/pypi/openai-codex/json)、[jsonschema](https://pypi.org/pypi/jsonschema/json)、[PyMuPDF](https://pypi.org/pypi/PyMuPDF/json)、[Pillow](https://pypi.org/pypi/Pillow/json)。本轮未安装或对 Python 全树执行漏洞扫描，不能宣称不存在已知漏洞。

## DEP-011 / P2 / 镜像与供应链构建输入未完整固定

Docker 所有 FROM 均为可变 tag；mysql:8.0、redis:7 同样不能给出唯一补丁版本。后端 apt-get 安装 LibreOffice/ffmpeg 等未固定版本，Node CLI 安装缺少独立 lock/integrity；Actions 只用主版本 tag，未固定 SHA。现有 package-lock 的 npm integrity 是可用基础，不能据此推断 OS/CLI 也已固定。

建议记录已验收镜像 digest、OS 包清单和 SBOM；以计划维护的 digest 更新替代永远固定旧镜像。逐项审查 nginx1.28、MySQL8.0、Redis7、JDK17/UbuntuJammy 的维护窗口和可取得安全补丁，避免只凭主版本升降。Actions 固定经过核实的完整 commit SHA并保留版本注释；建立依赖更新/镜像扫描门禁与复核流程，自动变更先走测试。这里是可复现性与漏洞识别盲区，不是已经证实上述运行服务中存在某一 CVE。

## 分类与完成标准

安全项优先级以真实入口和权限影响排序；纯“版本较旧”不创建漏洞。Hutool/JJWT/OSS SDK/TwelveMonkeys 等直接生产依赖也应纳入后续完整 Java SCA，本轮不凭版本年龄编造 CVE；Netty/Jackson/MySQL/Flyway 等需以本次实测依赖树与目标 BOM 统一核对。构建插件/测试库有漏洞仍可能影响 CI 与开发机器，但需要相应不可信输入或网络暴露条件。

整改完成须提供新 lock/BOM/镜像的可追踪版本、漏洞范围重新匹配结果、业务回归证据与仍保留的条件性风险说明。没有下载/执行过的新组合不能在台账里标“已兼容”。
