# 依赖安全待整改

核查时间：2026-11-01 UTC；代码基线：09da3dc3c790c8c1b729cad8d195a66944d7546e。本文只记录整改建议，不实施升级。P1 为优先排期的生产依赖问题，P2 为条件性、开发链或可维护性问题；包版本落入公告范围不等于已证明本系统可被利用。

## 结论与取证范围

应先处理后端文档解析和 Web 容器，再处理前端真实进入浏览器的请求/Markdown 依赖，最后按实际使用路径消减 Umi 开发链告警。不能只升级直接依赖或运行 `npm audit fix --force`。

官方 npm API 对现有 lockfile 的只读扫描成功：`npm audit --package-lock-only --json --ignore-scripts`，共 107 个受影响包记录，critical 3、high 32、moderate 58、low 14。这是 npm 的包级影响传播计数，包含 Umi/Jest 上层节点，不是 107 个独立 CVE，也不是 107 个生产漏洞。锁文件 2278 个依赖节点中 prod 351、dev 1927。初次沙箱 DNS 失败后，正常申请网络读取成功；未 install、未 audit fix、未更改 package/lock。机器不等于生产容器，本轮未进行攻击复现。

## DEP-001 / P1 / POI 5.2.5 读取上传 OOXML，命中已确认公告

- **事实与影响**：`zhul-erp-backend/pom.xml` 直接生产依赖 `org.apache.poi:poi-ooxml:5.2.5`；本地既有 surefire classpath 也包含此版本。CVE-2025-31672 影响 `<5.4.0`，重复 ZIP entry 可能使不同程序读取同一文档的不同内容。不要将该公告扩大描述为确定 RCE。
- **项目触及**：[SpreadsheetText.java:36](../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/inquiry/support/SpreadsheetText.java#L36) 的 `WorkbookFactory.create(in)`、[SourcingExcelServiceImpl.java:365](../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/inquiry/sourcing/service/impl/SourcingExcelServiceImpl.java#L365) 的 `new XSSFWorkbook(in)`，以及 [DocumentTemplateServiceImpl.java:234/242](../../zhul-erp-backend/src/main/java/com/zhul/erp/modules/document/service/impl/DocumentTemplateServiceImpl.java#L234) 解析模板；不只是生成 Excel。上传/导入权限可降低入口范围，不能替代解析修复。
- **整改**：最低 5.4.0，推荐在 POI 5.x 当前稳定 patch 中择定版本，并核查其传递 `commons-compress/xmlbeans/commons-io`。同步核对导入格式、模板公式与格式、报价单生成和物流/财务导出；增加重复 entry 拒绝及正常文档回归。文件大小、页/行数量和解析进程资源边界仍需独立治理。
- **一手证据**：[Apache POI 公告](https://poi.apache.org/index.html)、[官方文档解析隔离建议](https://poi.apache.org/security.html)。

## DEP-002 / P1 / Tomcat 10.1.20 的 multipart 解析存在已公开 DoS 修复缺口

- **事实**：Spring Boot 3.2.5 引入生产 `tomcat-embed-core:10.1.20`，本地既有测试类路径证实。CVE-2025-48988 / CVE-2025-48976 影响 `10.1.0-M1..10.1.41`，最低修复 10.1.42，涉及 multipart part 数量及 part header 的内存放大。
- **触及与限制**：系统有 MultipartFile 上传，`application.yml` 配置单文件/请求均 100MB；这不等于 part 数及其 header 已有充分限制。真实可用性影响还取决于网关限制、认证前后解析时机和容器堆大小，未发送恶意请求验证。此类问题不依赖 HTTP/2。
- **整改**：通过 Boot BOM 升级统一 Tomcat 全家版本；10.1.42 只是上述两个公告的修复下限，2026-11 核查时官方安全页已有 10.1.61，不能把升到 10.1.42 视为清除全部历史漏洞。临时覆盖时统一 core/el/websocket 并保留移除计划。回归多附件、批量导入、超限返回、并发上传、流式/异步下载；以当前 Tomcat 支持的 `maxPartCount/maxPartHeaderSize` 设置合理限额。
- **不能直接认定的相邻漏洞**：HTTP/2 CVE-2024-34750 还需该协议实际启用；CVE-2025-24813 的 RCE 还依赖可写 default servlet、partial PUT、会话持久化等条件。本轮未发现这些配置，不报告为已证实远程执行。
- **一手证据**：[Tomcat 10 官方安全页](https://tomcat.apache.org/security-10.html)。

## DEP-003 / P1 / axios 0.27.2 被业务代码使用，却只由 dev 链间接提供

- **事实**：lock 中唯一 axios 为 0.27.2，经 `@umijs/plugins` 的 `^0.27.2` 引入，标 `dev:true`；`src/requestErrorConfig.ts:5` 直接 import，约 197 行用于刷新 token 后重放请求。这属于会进入浏览器的代码，不能按 npm dev 分类自动排除。
- **公告与下限**：GHSA-wf5p-g6vw-rhxx 影响 `>=0.8.1 <0.28.0`（跨域 XSRF）；GHSA-jr5f-v2jv-69x6 / CVE-2025-27152 影响 `<0.30.0`（绝对 URL 绕过 baseURL）；更新的 GHSA-9fr6-4gfg-395g 影响 `>=0.27.2 <0.34.0`，0.x 修复 0.34.0、1.x 修复 1.20.0（原型污染后继承 method 的 gadget）。因此只升 0.30.0 不足以覆盖本轮公告。
- **可达性**：当前未证明攻击者可令业务重放接受任意绝对 URL、建立原型污染源或泄漏凭据；NO_PROXY、Node HTTP adapter 等公告与静态浏览器部署也不能直接类比 SSRF。应优先追踪 URL/header/config 来源并用构建产物核对实际 axios 代码。
- **整改与兼容**：明确业务 axios 直接依赖或统一受控请求层，结合 Umi 上游升级处理内置版本，择定覆盖当前全部适用公告的 patch；不盲目跨代 override。验证正常请求、401 并发刷新、失败重放、取消、上传进度、下载二进制及外域 URL 拒绝。多个 axios 副本分别确认。
- **一手证据**：[XSRF 公告](https://github.com/axios/axios/security/advisories/GHSA-wf5p-g6vw-rhxx)、[绝对 URL 公告](https://github.com/axios/axios/security/advisories/GHSA-jr5f-v2jv-69x6)、[2026 method gadget 公告](https://github.com/axios/axios/security/advisories/GHSA-9fr6-4gfg-395g)。

## DEP-004 / P2 / DOMPurify 3.4.2 位于生产 Markdown/Mermaid 链，需按实际模式验证

- **事实**：`@ant-design/x-markdown@2.7.0` 和 `mermaid@11.15.0` 均引用 prod `dompurify@3.4.2`；npm 报多项公告。GHSA-mrv2-3pqr-j5jr 影响 `<=3.4.16`，上游修复 3.4.17，前提是 `IN_PLACE` 模式下 hook 移除父/兄弟 DOM；其他模式不能据此一概认定 XSS。
- **触及与不确定性**：业务展示 AI/上传文档提取的 Markdown，存在不可信内容场景；本轮未完整验证 Markdown 库对 DOMPurify 的参数和 hook 路径，也未运行浏览器 payload。npm 对此项记 high，上游该公告页面标 low，不能以数据库最高值直接当业务优先级。
- **整改**：在父包允许范围内升级 DOMPurify 至覆盖当前适用公告的版本，3.4.17 只是已核公告下限；审查 sanitize 配置/HTML 和 Mermaid 安全设置。验证常规/流式 Markdown、SVG、公式、链接、图片、不同浏览器下的隔离及 HTML 输出，不以字符串过滤测试替代真实渲染。
- **一手证据**：[DOMPurify 上游公告](https://github.com/cure53/DOMPurify/security/advisories/GHSA-mrv2-3pqr-j5jr)。

## DEP-005 / P2 / Umi 开发与构建链累计漏洞，按启用能力整改

`@umijs/max@4.6.51` 带入多种 bundler、Express、Jest 与检查工具，当前配置明确用 utoopack；npm 把父包全标受影响不代表 Vite/Mako 全部运行。以下为应排查的分组，不应宣称静态 nginx 生产镜像暴露了 Node 开发服务器。

| 已锁版本/路径 | 已核公告及修复下限 | 实际条件/整改方向 |
| --- | --- | --- |
| vite 4.5.2，`@umijs/bundler-vite` | GHSA-x574-m823-4x7w：`<=4.5.9`，最低 4.5.10 | 启用 Vite 且 dev server 对网络开放才触发该项；更新公告范围更广，不要停在此历史下限。优先升级 Umi 父链或移除未用能力。 |
| esbuild 0.21.4 / 0.18.20 | GHSA-67mh-4wv8-2f99：`<=0.24.2`，最低 0.25.0 | 需要 esbuild serve 能力；普通 transform/build 不等于该 CORS 漏洞。 |
| immer 8.0.4，dva-immer | GHSA-c36v-fmgq-m8hx / GHSA-33f9-j839-rf8h：`>=7 <9.0.6`，最低 9.0.6 | npm critical；核查 dva 是否启用及外部数据是否进入相关转换，不能只按 dev 标记排除浏览器链。 |
| piscina 4.9.2，`@umijs/mako` | GHSA-x9g3-xrwr-cwfg：`<=4.9.2`，最低 4.9.3；另一项 GHSA-67c8-pqhq-4rmx 要求 >=4.9.4 | 需独立原型污染源和调用方 options 形态；当前使用 utoopack，Mako 是否可达待核实。npm critical、已读上游首项标 high。 |
| proxy-addr 2.0.7，Express | GHSA-jqcg-44mw-7w3h：`>=1.1 <2.0.8`，最低 2.0.8 | npm critical；需错误的 IPv4-mapped IPv6 trust subnet 配置。未证实 Umi dev/preview 设了该错误信任网段。 |
| body-parser 1.20.5 / 2.2.2；compression 1.8.1；ws 8.20.0 | 完整版本范围与公告链接见 03 清单附录 | 网络可达的 dev/preview 上传、压缩与 ws 服务；在 Node 开发链升级/限制监听地址并验证 HMR。 |

一手证据：[Vite](https://github.com/vitejs/vite/security/advisories/GHSA-x574-m823-4x7w)、[esbuild](https://github.com/evanw/esbuild/security/advisories/GHSA-67mh-4wv8-2f99)、[Immer GitHub 公告库](https://github.com/advisories/GHSA-c36v-fmgq-m8hx)、[Piscina](https://github.com/piscinajs/piscina/security/advisories/GHSA-x9g3-xrwr-cwfg)、[proxy-addr](https://github.com/jshttp/proxy-addr/security/advisories/GHSA-jqcg-44mw-7w3h)。本轮不接受 npm 提议将 Jest 30 降为 29.7.0 当作已验证安全修复。

## DEP-006 / P2 / 后端传递依赖与 Spring 条件型公告需随 BOM 更新核验

- **commons-compress 1.25.0**：POI 传递生产依赖。CVE-2024-25710 影响 1.3..1.25.0（DUMP），CVE-2024-26308 影响 1.21..<1.26.0（Pack200），均最低 1.26.0。本项目主要 OOXML/ZIP，未找到 DUMP/Pack200 读取调用，不将版本命中写为已可利用；随 POI 升级消除。来源：[Apache Commons Compress](https://commons.apache.org/proper/commons-compress/security.html)。
- **Security crypto 6.2.4**：版本落入 CVE-2025-22228 的 6.2.0..6.2.9，历史最低 6.2.10 为企业版；`SecurityConfig` 提供 BCryptPasswordEncoder，但实际 AuthServiceImpl 登录与重置走 Hutool BCrypt，未证实使用 Spring `matches` 的可达登录路径。统一密码策略与 UTF-8 长度边界验证，不宣称更换 Spring jar 自动修复 Hutool 行为。来源：[Spring 公告](https://spring.io/security/cve-2025-22228/)。
- **Framework 6.1.6**：CVE-2024-38816 要求 RouterFunctions 静态资源 + FileSystemResource，且 Tomcat / Spring Firewall 会拦该公告的路径。本项目用 WebMvcConfig 的资源 handler，未见 RouterFunctions，不能据此报任意文件读取；另需随 Boot 升级重新跑当前全部 Spring 公告匹配。来源：[Spring 公告](https://spring.io/security/cve-2024-38816/)。

## 已排除或未证实的常见误报

1. PDFBox 3.0.3：2026 的 CVE-2026-23907 / CVE-2026-33929 为 `examples/ExtractEmbeddedFiles` 路径穿越，分别 3.0.7 / 3.0.8 修复。项目直接用 core Loader/PDFRenderer，未引入 examples 或复制该提取例子，不能报本系统任意文件写入。仍建议常规升级 3.0.x 并独立约束 PDF 解析资源。[官方模块范围](https://pdfbox.apache.org/security.html)。
2. Commons IO 2.15.0 不落入 CVE-2024-47554 的 `<2.14.0` 范围。[官方公告](https://commons.apache.org/proper/commons-io/security.html)。
3. CVE-2024-38828 官方范围为 Spring 5.3，不适用于本项目 Framework 6.1.6。[官方公告](https://spring.io/security/cve-2024-38828/)。
4. React 19.2.5 不因“React 19”这一大版本名就认定 React Server Components 漏洞；lock 中未发现 react-server-dom-*，当前 Umi SPA/静态 nginx 部署也未证实 RSC 服务入口。

## 后续验收

保留每项升级前后解析树及最终镜像 SBOM 的小型证据；重新查询官方公告、比对受影响范围并说明仍不可达的条件。后端跑离线单元/集成测试及上传导入回归，前端跑 lint/tsc/Jest/antd lint 与生产构建，最后用真实 UI 走登录刷新、导入、Markdown/文档预览。只有扫描清零不能替代业务验证；只有业务通过也不能替代依赖范围核对。
