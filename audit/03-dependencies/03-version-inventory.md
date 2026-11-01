# 依赖版本清单与审查范围

核查时间：2026-11-01 UTC。基线：09da3dc3c790c8c1b729cad8d195a66944d7546e。仅读清单、现有本地测试产物与公开官方元数据；未改依赖、未安装、未运行服务、未读取真实环境配置。

## 前端全部直接依赖

package-lock v3，共 2278 个包节点（不含根）。下表为清单声明与 lock 实际版本，不能将 ^ 下限当成已解析版本。dev 是 npm 安装分类，Umi/axios 等仍可进入最终浏览器产物，须结合引用和构建分析判断。

| 安装分类 | 包 | 声明 | lock 版本 | Node 要求 |
| --- | --- | --- | --- | --- |
| dependencies | @ant-design/icons | ^6.2.3 | 6.2.3 | >=8 |
| dependencies | @ant-design/plots | ^2.6.8 | 2.6.8 |  |
| dependencies | @ant-design/pro-components | ^3.1.12-0 | 3.1.12-0 |  |
| dependencies | @ant-design/x | ^2.7.0 | 2.7.0 |  |
| dependencies | @ant-design/x-markdown | ^2.7.0 | 2.7.0 |  |
| dependencies | @ant-design/x-sdk | ^2.7.0 | 2.7.0 |  |
| dependencies | @rc-component/util | ^1.11.0 | 1.11.0 |  |
| dependencies | @tanstack/react-query | ^5.100.6 | 5.100.9 |  |
| dependencies | antd | ^6.4.3 | 6.4.3 |  |
| dependencies | antd-style | ^4.1.0 | 4.1.0 |  |
| dependencies | clsx | ^2.1.1 | 2.1.1 | >=6 |
| dependencies | d3 | ^7.9.0 | 7.9.0 | >=12 |
| dependencies | dayjs | ^1.11.20 | 1.11.20 |  |
| dependencies | highlight.js | ^11.11.1 | 11.11.1 | >=12.0.0 |
| dependencies | react | ^19.2.5 | 19.2.5 | >=0.10.0 |
| dependencies | react-dom | ^19.2.5 | 19.2.5 |  |
| dependencies | topojson-client | ^3.1.0 | 3.1.0 |  |
| devDependencies | @ant-design/cli | ^6.4.3 | 6.4.3 | >=20.0.0 |
| devDependencies | @biomejs/biome | ^2.4.13 | 2.4.14 | >=14.21.3 |
| devDependencies | @commitlint/cli | ^21.0.0 | 21.0.0 | >=22.12.0 |
| devDependencies | @commitlint/config-conventional | ^21.0.0 | 21.0.0 | >=22.12.0 |
| devDependencies | @tailwindcss/postcss | ^4.3.0 | 4.3.0 |  |
| devDependencies | @testing-library/dom | ^10.4.1 | 10.4.1 | >=18 |
| devDependencies | @testing-library/react | ^16.3.2 | 16.3.2 | >=18 |
| devDependencies | @types/d3 | ^7.4.3 | 7.4.3 |  |
| devDependencies | @types/express | ^5.0.6 | 5.0.6 |  |
| devDependencies | @types/jest | ^30.0.0 | 30.0.0 |  |
| devDependencies | @types/node | ^25.9.1 | 25.9.1 |  |
| devDependencies | @types/react | ^19.2.14 | 19.2.14 |  |
| devDependencies | @types/react-dom | ^19.2.3 | 19.2.3 |  |
| devDependencies | @types/react-helmet | ^6.1.11 | 6.1.11 |  |
| devDependencies | @types/topojson-client | ^3.1.5 | 3.1.5 |  |
| devDependencies | @types/topojson-specification | ^1.0.5 | 1.0.5 |  |
| devDependencies | @umijs/lint | ^4.6.51 | 4.6.51 |  |
| devDependencies | @umijs/max | ^4.6.51 | 4.6.51 |  |
| devDependencies | cross-env | ^10.1.0 | 10.1.0 | >=20 |
| devDependencies | express | ^5.2.1 | 5.2.1 | >= 18 |
| devDependencies | geojson | ^0.5.0 | 0.5.0 | >= 0.10 |
| devDependencies | husky | ^9.1.7 | 9.1.7 | >=18 |
| devDependencies | jest | ^30.4.1 | 30.4.1 | ^18.14.0 || ^20.0.0 || ^22.0.0 || >=24.0.0 |
| devDependencies | jest-environment-jsdom | ^30.4.1 | 30.4.1 | ^18.14.0 || ^20.0.0 || ^22.0.0 || >=24.0.0 |
| devDependencies | lint-staged | ^17.0.2 | 17.0.2 | >=22.22.1 |
| devDependencies | mockjs | ^1.1.0 | 1.1.0 |  |
| devDependencies | patch-package | ^8.0.1 | 8.0.1 | >=14 |
| devDependencies | react-doctor | ^0.2.1 | 0.2.1 | >=22 |
| devDependencies | swagger-ui-dist | ^5.32.5 | 5.32.5 |  |
| devDependencies | tailwindcss | ^4.2.4 | 4.3.0 |  |
| devDependencies | ts-node | ^10.9.2 | 10.9.2 |  |
| devDependencies | typescript | ^6.0.3 | 6.0.3 | >=14.17 |

## 后端直接依赖与已存在解析证据

以下直接依赖取自 pom.xml；managed 值由本地 Spring Boot 3.2.5 BOM 与现有 surefire classpath 交叉核对。测试产物时间为 2026-10-31，非本轮新解析，也不等同生产镜像内的 SBOM。

| 坐标 | 版本 | 分类 |
| --- | --- | --- |
| com.aliyun.oss:aliyun-sdk-oss | 3.17.4 | compile / 生产类路径 |
| com.twelvemonkeys.imageio:imageio-webp | 3.12.0 | compile / 生产类路径 |
| com.twelvemonkeys.imageio:imageio-jpeg | 3.12.0 | compile / 生产类路径 |
| org.springframework.boot:spring-boot-starter-web | Boot 3.2.5 BOM 管理（见下方已证实传递版本） | compile / 生产类路径 |
| org.springframework.boot:spring-boot-starter-mail | Boot 3.2.5 BOM 管理（见下方已证实传递版本） | compile / 生产类路径 |
| org.springframework.boot:spring-boot-starter-security | Boot 3.2.5 BOM 管理（见下方已证实传递版本） | compile / 生产类路径 |
| org.springframework.boot:spring-boot-starter-validation | Boot 3.2.5 BOM 管理（见下方已证实传递版本） | compile / 生产类路径 |
| org.springframework.boot:spring-boot-starter-data-redis | Boot 3.2.5 BOM 管理（见下方已证实传递版本） | compile / 生产类路径 |
| org.springframework.boot:spring-boot-starter-aop | Boot 3.2.5 BOM 管理（见下方已证实传递版本） | compile / 生产类路径 |
| com.mysql:mysql-connector-j | Boot 3.2.5 BOM 管理（见下方已证实传递版本） | runtime |
| org.flywaydb:flyway-core | Boot 3.2.5 BOM 管理（见下方已证实传递版本） | compile / 生产类路径 |
| org.flywaydb:flyway-mysql | Boot 3.2.5 BOM 管理（见下方已证实传递版本） | compile / 生产类路径 |
| com.baomidou:mybatis-plus-spring-boot3-starter | 3.5.6 | compile / 生产类路径 |
| io.jsonwebtoken:jjwt-api | 0.12.5 | compile / 生产类路径 |
| io.jsonwebtoken:jjwt-impl | 0.12.5 | runtime |
| io.jsonwebtoken:jjwt-jackson | 0.12.5 | runtime |
| com.github.xiaoymin:knife4j-openapi3-jakarta-spring-boot-starter | 4.4.0 | compile / 生产类路径 |
| cn.hutool:hutool-all | 5.8.26 | compile / 生产类路径 |
| org.apache.poi:poi-ooxml | 5.2.5 | compile / 生产类路径 |
| org.apache.pdfbox:pdfbox | 3.0.3 | compile / 生产类路径 |
| org.mapstruct:mapstruct | 1.5.5.Final | compile / 生产类路径 |
| org.mapstruct:mapstruct-processor | 1.5.5.Final | provided |
| org.projectlombok:lombok | 1.18.30 | compile / 生产类路径 |
| org.springframework.boot:spring-boot-starter-test | Boot 3.2.5 BOM 管理（见下方已证实传递版本） | test |
| org.springframework.security:spring-security-test | Boot 3.2.5 BOM 管理（见下方已证实传递版本） | test |

已证实关键传递版本：Spring Framework 6.1.6、Security 6.2.4、Tomcat 10.1.20、Netty 4.1.109.Final、Jackson 2.15.4、MySQL Connector/J 8.3.0、Flyway 9.22.3、springdoc 2.3.0、JSqlParser 4.9、commons-compress 1.25.0、commons-io 2.15.0、SnakeYAML 2.2、httpclient 4.5.13。来源：target/surefire-reports/TEST-com.zhul.erp.common.exception.GlobalExceptionHandlerMvcTest.xml 的 java.class.path，以及本地 .m2/repository/org/springframework/boot/spring-boot-dependencies/3.2.5/spring-boot-dependencies-3.2.5.pom。

redisson.version=3.27.2 仅是 POM 未使用属性，不是已引入 Redisson，不能误列成生产漏洞。MapStruct processor 为 provided/构建期，Lombok 被 boot 打包插件排除，JaCoCo 0.8.12 为测试构建插件。

## Python、CLI、容器与工具链

| 项目 | 仓库声明 | 分类与核查 |
| --- | --- | --- |
| anthropic | 1.14.0 | AI 服务生产直接依赖；仅顶层 pin，无完整传递锁与哈希 |
| openai | 3.28.0 | AI 服务生产直接依赖；仅顶层 pin，无完整传递锁与哈希 |
| openai-codex | 0.163.0 | AI 服务生产直接依赖；仅顶层 pin，无完整传递锁与哈希 |
| jsonschema | 4.26.0 | AI 服务生产直接依赖；仅顶层 pin，无完整传递锁与哈希 |
| PyMuPDF | 1.27.2.2 | AI 服务生产直接依赖；仅顶层 pin，无完整传递锁与哈希 |
| Pillow | 13.0.0 | AI 服务生产直接依赖；仅顶层 pin，无完整传递锁与哈希 |
| 前端构建 | node:24-bookworm-slim | 构建阶段 |
| 前端运行 | nginx:1.28-alpine | 运行阶段 |
| 后端构建 | maven:3.9-eclipse-temurin-17 | 构建阶段 |
| 后端运行 | eclipse-temurin:17-jre-jammy | 运行阶段；apt 安装 LibreOffice/ffmpeg/fonts/curl |
| Claude CLI构建 | node:22-bookworm-slim | 仅下载 CLI |
| Claude CLI | @anthropic-ai/claude-code@2.1.311 | AI 服务生产二进制，跨 stage 拷贝 |
| AI运行 | python:3.12-slim | 生产阶段 |
| 数据库 | mysql:8.0 | Compose 运行服务，未确认实际 patch/digest |
| 缓存 | redis:7 | Compose 运行服务，未确认实际 patch/digest |
| 本机只读工具版本 | Node 26.13.0 / npm 11.20.0 / Python 3.13.16 / Temurin 17.0.20.1 / Maven 3.10.0 | 与 Docker 基线不同，非生产版本证据 |

Actions：checkout@v7、setup-buildx-action@v4、login-action@v4、build-push-action@v7；仅主版本 tag，未 pin commit。未查询生产机器、实际容器 digest、OS 包数据库或镜像 CVE 扫描结果，因此不能依据浮动 tag 宣称 nginx/MySQL/Redis/JDK 已命中特定漏洞。

本轮未执行 Maven dependency:tree 下载、Python 安装/pip-audit、容器 build/pull/scan、实际攻击或 AI 远程调用。前端 npm audit 已通过官方 API 成功，只保留精简根依赖公告索引，不保存完整扫描日志；详见 01/02 文档。

## npm 根公告精简索引（DEP-003/004/005 的待复核清单）

2026-11-01 从 npm 官方 audit API 查询既有 lock；仅列 via 中有独立公告的包，省略纯影响传播父节点。等级为 npm 聚合等级，并非本项目可利用性结论。以下尚未逐项做代码可达性复核；含有 dev 的包也可能被 Umi 编译进浏览器，prod 也不表示其所有 API 均被使用。范围为公告受影响范围；具体修复版本须再查看链接及父链约束，不凭 fixAvailable 自动批改。

| 根包 | lock 版本 / 安装分类 | npm 等级 | 受影响范围与官方公告索引 |
| --- | --- | --- | --- |
| @babel/core | 7.23.6 (dev), 7.29.0 (dev) | low | [GHSA-4x5r-pxfx-6jf8](https://github.com/advisories/GHSA-4x5r-pxfx-6jf8): <=7.29.0 |
| @babel/runtime | 7.23.6 (dev) | moderate | [GHSA-968p-4wvh-cqc8](https://github.com/advisories/GHSA-968p-4wvh-cqc8): <7.26.10 |
| @hono/node-server | 1.19.14 (dev) | moderate | [GHSA-frvp-7c67-39w9](https://github.com/advisories/GHSA-frvp-7c67-39w9): <1.19.15<br>[GHSA-rmxm-3fg6-px4f](https://github.com/advisories/GHSA-rmxm-3fg6-px4f): >=1.19.10 <2.1.3 |
| axios | 0.27.2 (dev) | high | [GHSA-wf5p-g6vw-rhxx](https://github.com/advisories/GHSA-wf5p-g6vw-rhxx): >=0.8.1 <0.28.0<br>[GHSA-jr5f-v2jv-69x6](https://github.com/advisories/GHSA-jr5f-v2jv-69x6): <0.30.0<br>[GHSA-3p68-rc4w-qgx5](https://github.com/advisories/GHSA-3p68-rc4w-qgx5): <0.31.0<br>[GHSA-w9j2-pvgh-6h63](https://github.com/advisories/GHSA-w9j2-pvgh-6h63): <=0.31.0<br>[GHSA-pmwg-cvhr-8vh7](https://github.com/advisories/GHSA-pmwg-cvhr-8vh7): <=0.31.0<br>[GHSA-xhjh-pmcv-23jw](https://github.com/advisories/GHSA-xhjh-pmcv-23jw): <=0.31.0<br>[GHSA-m7pr-hjqh-92cm](https://github.com/advisories/GHSA-m7pr-hjqh-92cm): <=0.31.0<br>[GHSA-5c9x-8gcm-mpgx](https://github.com/advisories/GHSA-5c9x-8gcm-mpgx): <=0.31.0<br>[GHSA-vf2m-468p-8v99](https://github.com/advisories/GHSA-vf2m-468p-8v99): <=0.31.0<br>[GHSA-pf86-5x62-jrwf](https://github.com/advisories/GHSA-pf86-5x62-jrwf): <=0.31.0<br>[GHSA-6chq-wfr3-2hj9](https://github.com/advisories/GHSA-6chq-wfr3-2hj9): <=0.31.0<br>[GHSA-xx6v-rp6x-q39c](https://github.com/advisories/GHSA-xx6v-rp6x-q39c): <=0.31.0<br>[GHSA-43fc-jf86-j433](https://github.com/advisories/GHSA-43fc-jf86-j433): <=0.30.2<br>[GHSA-fvcv-3m26-pcqx](https://github.com/advisories/GHSA-fvcv-3m26-pcqx): <0.31.0<br>[GHSA-62hf-57xw-28j9](https://github.com/advisories/GHSA-62hf-57xw-28j9): <=0.31.0<br>[GHSA-hfxv-24rg-xrqf](https://github.com/advisories/GHSA-hfxv-24rg-xrqf): <=0.31.1<br>[GHSA-p92q-9vqr-4j8v](https://github.com/advisories/GHSA-p92q-9vqr-4j8v): <=0.31.1<br>[GHSA-j5f8-grm9-p9fc](https://github.com/advisories/GHSA-j5f8-grm9-p9fc): <=0.31.1<br>[GHSA-3g43-6gmg-66jw](https://github.com/advisories/GHSA-3g43-6gmg-66jw): >=0.19.0 <0.31.1<br>[GHSA-898c-q2cr-xwhg](https://github.com/advisories/GHSA-898c-q2cr-xwhg): <=0.31.1<br>[GHSA-pjwm-pj3p-43mv](https://github.com/advisories/GHSA-pjwm-pj3p-43mv): <=0.31.1<br>[GHSA-mmx7-hfxf-jppx](https://github.com/advisories/GHSA-mmx7-hfxf-jppx): <0.33.0<br>[GHSA-7q8q-rj6j-mhjq](https://github.com/advisories/GHSA-7q8q-rj6j-mhjq): >=0.8.0 <0.33.0<br>[GHSA-9fr6-4gfg-395g](https://github.com/advisories/GHSA-9fr6-4gfg-395g): >=0.27.2 <0.34.0 |
| baseline-browser-mapping | 2.10.27 (dev) | moderate | [GHSA-w5vr-8v7q-w6rv](https://github.com/advisories/GHSA-w5vr-8v7q-w6rv): >=2.0.0 <2.11.0 |
| body-parser | 1.20.5 (dev), 2.2.2 (dev) | high | [GHSA-v422-hmwv-36x6](https://github.com/advisories/GHSA-v422-hmwv-36x6): >=2.0.0 <2.3.0<br>[GHSA-v422-hmwv-36x6](https://github.com/advisories/GHSA-v422-hmwv-36x6): <1.20.6<br>[GHSA-27r3-jxmh-p828](https://github.com/advisories/GHSA-27r3-jxmh-p828): <1.20.9<br>[GHSA-27r3-jxmh-p828](https://github.com/advisories/GHSA-27r3-jxmh-p828): >=2.0.0 <2.4.0 |
| brace-expansion | 2.1.0 (dev), 1.1.14 (dev) | high | [GHSA-3jxr-9vmj-r5cp](https://github.com/advisories/GHSA-3jxr-9vmj-r5cp): >=2.0.0 <2.1.2<br>[GHSA-3jxr-9vmj-r5cp](https://github.com/advisories/GHSA-3jxr-9vmj-r5cp): <1.1.16<br>[GHSA-mh99-v99m-4gvg](https://github.com/advisories/GHSA-mh99-v99m-4gvg): <1.1.17<br>[GHSA-mh99-v99m-4gvg](https://github.com/advisories/GHSA-mh99-v99m-4gvg): >=2.0.0 <2.1.3<br>[GHSA-rgw5-rvv9-x895](https://github.com/advisories/GHSA-rgw5-rvv9-x895): >=2.0.0 <2.1.4<br>[GHSA-rgw5-rvv9-x895](https://github.com/advisories/GHSA-rgw5-rvv9-x895): <1.1.18<br>[GHSA-q2hr-2g5m-vwhr](https://github.com/advisories/GHSA-q2hr-2g5m-vwhr): <1.1.21<br>[GHSA-q2hr-2g5m-vwhr](https://github.com/advisories/GHSA-q2hr-2g5m-vwhr): >=2.0.0 <2.1.7<br>[GHSA-qhr7-859c-m2p7](https://github.com/advisories/GHSA-qhr7-859c-m2p7): <1.1.20<br>[GHSA-qhr7-859c-m2p7](https://github.com/advisories/GHSA-qhr7-859c-m2p7): >=2.0.0 <2.1.6<br>[GHSA-6j4f-fj2g-mc7p](https://github.com/advisories/GHSA-6j4f-fj2g-mc7p): <1.1.19<br>[GHSA-6j4f-fj2g-mc7p](https://github.com/advisories/GHSA-6j4f-fj2g-mc7p): >=2.0.0 <2.1.5 |
| browserslist | 4.28.2 (dev) | high | [GHSA-c83g-rgw3-j3cx](https://github.com/advisories/GHSA-c83g-rgw3-j3cx): <=4.28.6<br>[GHSA-73wf-gq98-2v4g](https://github.com/advisories/GHSA-73wf-gq98-2v4g): <=4.28.6 |
| colord | 2.9.3 (dev) | moderate | [GHSA-2wm5-q62r-hmrv](https://github.com/advisories/GHSA-2wm5-q62r-hmrv): <2.9.4 |
| compression | 1.8.1 (dev) | high | [GHSA-vc2v-76pw-4v95](https://github.com/advisories/GHSA-vc2v-76pw-4v95): <1.8.2 |
| decode-uri-component | 0.2.2 (dev) | high | [GHSA-vcc3-ghjq-m6fr](https://github.com/advisories/GHSA-vcc3-ghjq-m6fr): <=0.4.2<br>[GHSA-qr28-fpjm-wq7f](https://github.com/advisories/GHSA-qr28-fpjm-wq7f): <=0.5.0 |
| deepmerge | 4.3.1 (dev) | low | [GHSA-gcqx-w48m-w2h4](https://github.com/advisories/GHSA-gcqx-w48m-w2h4): >=4.2.0 <=4.3.1 |
| dompurify | 3.4.2 (prod) | high | [GHSA-hpcv-96wg-7vj8](https://github.com/advisories/GHSA-hpcv-96wg-7vj8): <=3.4.5<br>[GHSA-r47g-fvhr-h676](https://github.com/advisories/GHSA-r47g-fvhr-h676): <=3.4.5<br>[GHSA-rp9w-3fw7-7cwq](https://github.com/advisories/GHSA-rp9w-3fw7-7cwq): <=3.4.6<br>[GHSA-cmwh-pvxp-8882](https://github.com/advisories/GHSA-cmwh-pvxp-8882): <=3.4.10<br>[GHSA-vxr8-fq34-vvx9](https://github.com/advisories/GHSA-vxr8-fq34-vvx9): <3.4.9<br>[GHSA-gvmj-g25r-r7wr](https://github.com/advisories/GHSA-gvmj-g25r-r7wr): >=3.0.0 <=3.4.7<br>[GHSA-x4vx-rjvf-j5p4](https://github.com/advisories/GHSA-x4vx-rjvf-j5p4): <=3.4.6<br>[GHSA-76mc-f452-cxcm](https://github.com/advisories/GHSA-76mc-f452-cxcm): <3.4.7<br>[GHSA-55q2-fjhq-7xh7](https://github.com/advisories/GHSA-55q2-fjhq-7xh7): <=3.4.12<br>[GHSA-c2j3-45gr-mqc4](https://github.com/advisories/GHSA-c2j3-45gr-mqc4): <=3.4.11<br>[GHSA-6688-9rhm-gjv2](https://github.com/advisories/GHSA-6688-9rhm-gjv2): <=3.4.15<br>[GHSA-mrv2-3pqr-j5jr](https://github.com/advisories/GHSA-mrv2-3pqr-j5jr): <=3.4.16<br>[GHSA-c6gr-qvv4-5v3f](https://github.com/advisories/GHSA-c6gr-qvv4-5v3f): <=3.4.16 |
| elliptic | 6.6.1 (dev) | low | [GHSA-848j-6mx2-7j84](https://github.com/advisories/GHSA-848j-6mx2-7j84): <=6.6.1 |
| esbuild | 0.18.20 (dev), 0.21.4 (dev) | moderate | [GHSA-67mh-4wv8-2f99](https://github.com/advisories/GHSA-67mh-4wv8-2f99): <=0.24.2 |
| fast-uri | 3.1.2 (dev) | high | [GHSA-v2hh-gcrm-f6hx](https://github.com/advisories/GHSA-v2hh-gcrm-f6hx): >=3.0.0 <=3.1.3<br>[GHSA-7p8r-x3mc-p8w7](https://github.com/advisories/GHSA-7p8r-x3mc-p8w7): >=3.0.0 <3.1.5<br>[GHSA-f65p-4m7j-42xc](https://github.com/advisories/GHSA-f65p-4m7j-42xc): >=3.0.0 <3.1.6<br>[GHSA-fph4-wmhf-6fwf](https://github.com/advisories/GHSA-fph4-wmhf-6fwf): >=3.1.2 <3.1.6<br>[GHSA-jqff-g426-hqxp](https://github.com/advisories/GHSA-jqff-g426-hqxp): >=3.0.0 <3.1.6<br>[GHSA-4c8g-83qw-93j6](https://github.com/advisories/GHSA-4c8g-83qw-93j6): >=3.0.0 <3.1.3<br>[GHSA-qw65-cvwx-89v3](https://github.com/advisories/GHSA-qw65-cvwx-89v3): >=3.0.0 <3.1.7<br>[GHSA-hrr3-gc8f-f4qj](https://github.com/advisories/GHSA-hrr3-gc8f-f4qj): >=3.0.0 <3.1.8<br>[GHSA-w74x-v6f9-j3xg](https://github.com/advisories/GHSA-w74x-v6f9-j3xg): >=3.0.0 <3.1.9<br>[GHSA-xhq7-xmqj-769h](https://github.com/advisories/GHSA-xhq7-xmqj-769h): >=3.0.0 <3.1.9<br>[GHSA-333x-w4r6-6crp](https://github.com/advisories/GHSA-333x-w4r6-6crp): >=3.0.0 <3.1.9 |
| follow-redirects | 1.16.0 (dev) | high | [GHSA-q3jh-wrc6-jgq8](https://github.com/advisories/GHSA-q3jh-wrc6-jgq8): >=1.16.0 <1.16.1<br>[GHSA-8r9p-f939-6h3c](https://github.com/advisories/GHSA-8r9p-f939-6h3c): >=1.16.0 <1.16.1<br>[GHSA-mrwp-f3q8-3g9p](https://github.com/advisories/GHSA-mrwp-f3q8-3g9p): >=1.16.0 <=1.16.1 |
| form-data | 4.0.5 (dev) | high | [GHSA-hmw2-7cc7-3qxx](https://github.com/advisories/GHSA-hmw2-7cc7-3qxx): >=4.0.0 <4.0.6 |
| glob | 10.5.0 (dev), 7.2.3 (dev) | moderate | [GHSA-3g3m-2fjr-6pw4](https://github.com/advisories/GHSA-3g3m-2fjr-6pw4): >=9.0.0 <=14.0.1 |
| hono | 4.12.18 (dev) | high | [GHSA-xrhx-7g5j-rcj5](https://github.com/advisories/GHSA-xrhx-7g5j-rcj5): <4.12.21<br>[GHSA-3hrh-pfw6-9m5x](https://github.com/advisories/GHSA-3hrh-pfw6-9m5x): <4.12.21<br>[GHSA-f577-qrjj-4474](https://github.com/advisories/GHSA-f577-qrjj-4474): <4.12.21<br>[GHSA-2gcr-mfcq-wcc3](https://github.com/advisories/GHSA-2gcr-mfcq-wcc3): <4.12.21<br>[GHSA-rv63-4mwf-qqc2](https://github.com/advisories/GHSA-rv63-4mwf-qqc2): <4.12.25<br>[GHSA-wgpf-jwqj-8h8p](https://github.com/advisories/GHSA-wgpf-jwqj-8h8p): <4.12.25<br>[GHSA-88fw-hqm2-52qc](https://github.com/advisories/GHSA-88fw-hqm2-52qc): <4.12.25<br>[GHSA-wwfh-h76j-fc44](https://github.com/advisories/GHSA-wwfh-h76j-fc44): <4.12.25<br>[GHSA-j6c9-x7qj-28xf](https://github.com/advisories/GHSA-j6c9-x7qj-28xf): <4.12.25<br>[GHSA-xgm2-5f3f-mvvc](https://github.com/advisories/GHSA-xgm2-5f3f-mvvc): >=4.3.3 <4.12.27<br>[GHSA-hvrm-45r6-mjfj](https://github.com/advisories/GHSA-hvrm-45r6-mjfj): >=4.11.8 <4.12.27<br>[GHSA-w62v-xxxg-mg59](https://github.com/advisories/GHSA-w62v-xxxg-mg59): >=4.0.0 <4.12.27<br>[GHSA-8j4g-w8fx-2239](https://github.com/advisories/GHSA-8j4g-w8fx-2239): <4.12.34<br>[GHSA-f23p-vx2j-j53r](https://github.com/advisories/GHSA-f23p-vx2j-j53r): >=3.8.0 <4.12.34<br>[GHSA-79qm-7rj5-m7r9](https://github.com/advisories/GHSA-79qm-7rj5-m7r9): >=4.7.0 <4.12.34<br>[GHSA-54fx-42gc-7vw4](https://github.com/advisories/GHSA-54fx-42gc-7vw4): >=4.12.0 <4.12.34<br>[GHSA-gqvv-2mrq-wpjv](https://github.com/advisories/GHSA-gqvv-2mrq-wpjv): <4.13.5<br>[GHSA-g6gw-c38x-mqfc](https://github.com/advisories/GHSA-g6gw-c38x-mqfc): <4.13.5<br>[GHSA-crvj-82cr-hjcx](https://github.com/advisories/GHSA-crvj-82cr-hjcx): <4.13.5<br>[GHSA-hxh3-vqpv-xpqv](https://github.com/advisories/GHSA-hxh3-vqpv-xpqv): <4.13.7<br>[GHSA-5r4p-p66f-jhc7](https://github.com/advisories/GHSA-5r4p-p66f-jhc7): >=4.12.4 <4.13.11 |
| immer | 8.0.4 (dev) | critical | [GHSA-c36v-fmgq-m8hx](https://github.com/advisories/GHSA-c36v-fmgq-m8hx): >=7.0.0 <9.0.6<br>[GHSA-33f9-j839-rf8h](https://github.com/advisories/GHSA-33f9-j839-rf8h): >=7.0.0 <9.0.6 |
| immutable | 4.3.8 (dev) | high | [GHSA-xvcm-6775-5m9r](https://github.com/advisories/GHSA-xvcm-6775-5m9r): >=4.0.0-beta.1 <4.3.9<br>[GHSA-v56q-mh7h-f735](https://github.com/advisories/GHSA-v56q-mh7h-f735): >=4.0.0-rc.1 <4.3.9 |
| js-yaml | 3.14.2 (dev), 4.1.1 (dev) | high | [GHSA-h67p-54hq-rp68](https://github.com/advisories/GHSA-h67p-54hq-rp68): <3.15.0<br>[GHSA-h67p-54hq-rp68](https://github.com/advisories/GHSA-h67p-54hq-rp68): >=4.0.0 <=4.1.1<br>[GHSA-52cp-r559-cp3m](https://github.com/advisories/GHSA-52cp-r559-cp3m): >=4.0.0 <4.3.0<br>[GHSA-52cp-r559-cp3m](https://github.com/advisories/GHSA-52cp-r559-cp3m): >=3.0.0 <3.15.0<br>[GHSA-5p4m-2wfm-xmqj](https://github.com/advisories/GHSA-5p4m-2wfm-xmqj): >=3.0.0 <3.15.1<br>[GHSA-5p4m-2wfm-xmqj](https://github.com/advisories/GHSA-5p4m-2wfm-xmqj): >=4.0.0 <4.3.1<br>[GHSA-2883-xcg3-v3hh](https://github.com/advisories/GHSA-2883-xcg3-v3hh): >=3.0.0 <3.15.2<br>[GHSA-2883-xcg3-v3hh](https://github.com/advisories/GHSA-2883-xcg3-v3hh): >=4.0.0 <4.3.2 |
| katex | 0.16.45 (prod) | low | [GHSA-238p-pmpm-9mq7](https://github.com/advisories/GHSA-238p-pmpm-9mq7): >=0.11.0 <0.18.2 |
| mermaid | 11.15.0 (prod) | moderate | [GHSA-c4c3-pg64-4m4v](https://github.com/advisories/GHSA-c4c3-pg64-4m4v): >=11.0.0-alpha.1 <11.16.1<br>[GHSA-6x64-9x62-f2gx](https://github.com/advisories/GHSA-6x64-9x62-f2gx): >=11.0.0-alpha.1 <11.16.1<br>[GHSA-3rrr-jr9j-h3q3](https://github.com/advisories/GHSA-3rrr-jr9j-h3q3): >=11.5.0 <11.16.1<br>[GHSA-2v8p-3f2j-5mp7](https://github.com/advisories/GHSA-2v8p-3f2j-5mp7): >=11.0.0-alpha.1 <11.16.1<br>[GHSA-rhh3-jpg6-66xh](https://github.com/advisories/GHSA-rhh3-jpg6-66xh): >=11.6.0 <11.16.1 |
| minimatch | 9.0.9 (dev), 3.1.5 (dev) | moderate | [GHSA-g3hv-75wg-m249](https://github.com/advisories/GHSA-g3hv-75wg-m249): <11.0.1 |
| mockjs | 1.1.0 (dev) | high | [GHSA-mh8j-9jvh-gjf6](https://github.com/advisories/GHSA-mh8j-9jvh-gjf6): <=1.1.0 |
| moment | 2.30.1 (dev) | moderate | [GHSA-4p3w-j4w9-5jqw](https://github.com/advisories/GHSA-4p3w-j4w9-5jqw): >=2.29.2 <2.31.0 |
| nanoid | 3.3.12 (dev) | high | [GHSA-28wg-ghj8-5hjv](https://github.com/advisories/GHSA-28wg-ghj8-5hjv): <3.3.16<br>[GHSA-2v37-7h3g-55p8](https://github.com/advisories/GHSA-2v37-7h3g-55p8): <3.3.18 |
| path-to-regexp | 8.2.0 (dev), 1.7.0 (dev) | high | [GHSA-9wv6-86v2-598j](https://github.com/advisories/GHSA-9wv6-86v2-598j): >=0.2.0 <1.9.0<br>[GHSA-j3q9-mxjg-w52f](https://github.com/advisories/GHSA-j3q9-mxjg-w52f): >=8.0.0 <8.4.0<br>[GHSA-27v5-c462-wpq7](https://github.com/advisories/GHSA-27v5-c462-wpq7): >=8.0.0 <8.4.0 |
| pbkdf2 | 3.1.5 (dev) | moderate | [GHSA-477h-4r7f-fvrx](https://github.com/advisories/GHSA-477h-4r7f-fvrx): <=3.1.6 |
| piscina | 4.9.2 (dev) | critical | [GHSA-x9g3-xrwr-cwfg](https://github.com/advisories/GHSA-x9g3-xrwr-cwfg): <=4.9.2<br>[GHSA-67c8-pqhq-4rmx](https://github.com/advisories/GHSA-67c8-pqhq-4rmx): <4.9.4 |
| postcss | 8.5.14 (dev) | high | [GHSA-fxqj-rqcc-2cmp](https://github.com/advisories/GHSA-fxqj-rqcc-2cmp): <=8.5.22<br>[GHSA-r28c-9q8g-f849](https://github.com/advisories/GHSA-r28c-9q8g-f849): <=8.5.17 |
| postcss-selector-parser | 7.1.1 (dev), 6.1.2 (dev) | moderate | [GHSA-w9m9-85wc-3x92](https://github.com/advisories/GHSA-w9m9-85wc-3x92): >=7.1.0 <7.1.3<br>[GHSA-w9m9-85wc-3x92](https://github.com/advisories/GHSA-w9m9-85wc-3x92): >=6.1.0 <6.1.3<br>[GHSA-rj75-hqrm-r3gf](https://github.com/advisories/GHSA-rj75-hqrm-r3gf): <6.1.5<br>[GHSA-rj75-hqrm-r3gf](https://github.com/advisories/GHSA-rj75-hqrm-r3gf): >=7.0.0 <7.1.6 |
| proxy-addr | 2.0.7 (dev) | critical | [GHSA-jqcg-44mw-7w3h](https://github.com/advisories/GHSA-jqcg-44mw-7w3h): >=1.1.0 <2.0.8 |
| qs | 6.14.2 (dev), 6.15.1 (dev) | moderate | [GHSA-q8mj-m7cp-5q26](https://github.com/advisories/GHSA-q8mj-m7cp-5q26): >=6.11.1 <=6.15.1<br>[GHSA-x5fp-wj9c-mxmx](https://github.com/advisories/GHSA-x5fp-wj9c-mxmx): >=6.14.2 <=6.15.3<br>[GHSA-4mjr-xmp4-gh2g](https://github.com/advisories/GHSA-4mjr-xmp4-gh2g): >=2.2.5 <6.16.0<br>[GHSA-6pq8-jhm2-2vcw](https://github.com/advisories/GHSA-6pq8-jhm2-2vcw): >=6.7.0 <=6.16.0 |
| react-router | 6.3.0 (dev) | moderate | [GHSA-9jcx-v3wj-wh4m](https://github.com/advisories/GHSA-9jcx-v3wj-wh4m): >=6.0.0 <6.30.2<br>[GHSA-wrjc-x8rr-h8h6](https://github.com/advisories/GHSA-wrjc-x8rr-h8h6): >=6.0.0 <7.18.0 |
| send | 0.17.1 (dev) | low | [GHSA-m6fv-jmcg-4jfg](https://github.com/advisories/GHSA-m6fv-jmcg-4jfg): <0.19.0 |
| source-map-js | 1.2.1 (dev) | high | [GHSA-68fv-2mgg-jv7q](https://github.com/advisories/GHSA-68fv-2mgg-jv7q): >=1.0.0 <1.2.2 |
| svgo | 2.8.2 (dev) | high | [GHSA-2p49-hgcm-8545](https://github.com/advisories/GHSA-2p49-hgcm-8545): >=1.0.0 <2.8.3<br>[GHSA-4vpr-x523-8j87](https://github.com/advisories/GHSA-4vpr-x523-8j87): >=1.0.0 <2.8.4<br>[GHSA-w27v-7q3p-w38r](https://github.com/advisories/GHSA-w27v-7q3p-w38r): >=1.0.0 <2.8.4 |
| vite | 4.5.2 (dev) | high | [GHSA-8jhw-289h-jh2g](https://github.com/advisories/GHSA-8jhw-289h-jh2g): >=4.0.0 <=4.5.2<br>[GHSA-64vr-g452-qvp3](https://github.com/advisories/GHSA-64vr-g452-qvp3): >=4.0.0 <4.5.4<br>[GHSA-9cwx-2883-4wfx](https://github.com/advisories/GHSA-9cwx-2883-4wfx): >=4.0.0 <=4.5.3<br>[GHSA-vg6x-rcgg-rjx6](https://github.com/advisories/GHSA-vg6x-rcgg-rjx6): <=4.5.5<br>[GHSA-x574-m823-4x7w](https://github.com/advisories/GHSA-x574-m823-4x7w): <4.5.10<br>[GHSA-356w-63v5-8wf4](https://github.com/advisories/GHSA-356w-63v5-8wf4): <4.5.13<br>[GHSA-859w-5945-r5v3](https://github.com/advisories/GHSA-859w-5945-r5v3): <=4.5.13<br>[GHSA-xcj6-pq6g-qj4x](https://github.com/advisories/GHSA-xcj6-pq6g-qj4x): <4.5.12<br>[GHSA-g4jq-h2w9-997c](https://github.com/advisories/GHSA-g4jq-h2w9-997c): <=5.4.19<br>[GHSA-jqfw-vq24-v9c3](https://github.com/advisories/GHSA-jqfw-vq24-v9c3): <=5.4.19<br>[GHSA-4r4m-qw57-chr8](https://github.com/advisories/GHSA-4r4m-qw57-chr8): <4.5.11<br>[GHSA-4w7w-66w2-5vf9](https://github.com/advisories/GHSA-4w7w-66w2-5vf9): <=6.4.1<br>[GHSA-c27g-q93r-2cwf](https://github.com/advisories/GHSA-c27g-q93r-2cwf): <=5.4.8<br>[GHSA-v6wh-96g9-6wx3](https://github.com/advisories/GHSA-v6wh-96g9-6wx3): <=6.4.2<br>[GHSA-698v-4r3x-3vcv](https://github.com/advisories/GHSA-698v-4r3x-3vcv): <=6.4.3<br>[GHSA-r825-w8mm-5pxw](https://github.com/advisories/GHSA-r825-w8mm-5pxw): <=6.4.3<br>[GHSA-587h-7wmj-h5pm](https://github.com/advisories/GHSA-587h-7wmj-h5pm): <=6.4.3<br>[GHSA-9jrq-w75r-8gcw](https://github.com/advisories/GHSA-9jrq-w75r-8gcw): <=6.4.3<br>[GHSA-rq7h-c2jc-7f22](https://github.com/advisories/GHSA-rq7h-c2jc-7f22): <=6.4.3<br>[GHSA-fx2h-pf6j-xcff](https://github.com/advisories/GHSA-fx2h-pf6j-xcff): <=6.4.2 |
| ws | 8.20.0 (dev) | high | [GHSA-58qx-3vcg-4xpx](https://github.com/advisories/GHSA-58qx-3vcg-4xpx): >=8.0.0 <8.20.1<br>[GHSA-96hv-2xvq-fx4p](https://github.com/advisories/GHSA-96hv-2xvq-fx4p): >=8.0.0 <8.21.0 |
| zod | 3.25.76 (dev) | high | [GHSA-mrhx-c7xc-m3w5](https://github.com/advisories/GHSA-mrhx-c7xc-m3w5): <4.6.6 |
