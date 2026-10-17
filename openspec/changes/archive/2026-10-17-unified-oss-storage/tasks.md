## 1. 依赖与配置

- [x] 1.1 在 pom.xml 中加入阿里云 OSS Java SDK 与 TwelveMonkeys（imageio-webp、imageio-jpeg）。新依赖要先联网执行一次 `mvn dependency:resolve`，之后才能用 `mvn -o` 离线编译。验证：`mvn -o compile` 通过
- [x] 1.2 新增存储配置类（`STORAGE_TYPE`、`OSS_*`、签名链接有效期、转码线程池参数），并在 `application.yml` 与 `.env.example` 中加入配置项和说明。选择 `oss` 但缺少必填项时启动失败，并指出缺少哪一项。验证：单元测试覆盖「缺私有存储桶」与「本地方式不需要 OSS 配置」两种情况
- [ ] 1.3 （待部署时验证：Dockerfile 已加入 ffmpeg，本机没有 Docker）后端 Dockerfile 加装 ffmpeg。验证：构建镜像后在容器内执行 `ffmpeg -version`

## 2. 存储服务

- [x] 2.1 实现存储目录规则（环节、租户、年月、随机名；品牌和型号的规范化），与规格「存储目录」表一一对应。验证：单元测试覆盖每个环节，以及品牌、型号中的大小写、空格和斜杠
- [x] 2.2 定义 `FileStorage` 接口，实现 `LocalFileStorage`：私有和公开目录、本地签名链接（HMAC、过期时间、Range、原文件名）。验证：单元测试覆盖签名正确、签名被篡改、链接过期、Range 请求
- [x] 2.3 实现 `OssFileStorage`：上传、读取流、签名链接（带 `response-content-disposition`）、公开地址（支持自定义域名）、查询是否存在与大小、删除。验证：针对接口的单元测试（模拟 OSS 客户端），另附一份可选的真实 OSS 联调测试，用环境变量开启
- [x] 2.4 新增公共上传校验器（扩展名白名单、文件头、大小上限、拒绝 svg），把现有各模块分散的校验收拢过来，提示文案保持不变。验证：现有各模块的「伪装文件」「超过大小」测试全部通过
- [x] 2.5 新增本地签名链接的取文件接口 `/api/v1/files/local/{key}`（不需要登录，校验签名），并在安全配置中放行这一路径。验证：集成测试覆盖签名有效时返回文件、签名无效时返回 403

## 3. 图片压缩与视频转码

- [x] 3.1 实现图片压缩：按方向信息转正、长边不超过 2048 像素、JPG 和 WEBP 输出为质量 0.85 的 JPG、PNG 无损、去掉元数据、压缩后不变大。验证：单元测试使用带拍摄位置和方向信息的样例照片、大尺寸 PNG 截图、已高度压缩的小图，逐条核对规格「上传压缩」的三个场景
- [x] 3.2 迁移：`biz_attachment` 与 `product_media` 增加 `processing_status`（0-无需处理、1-转码中、2-已完成、3-失败），并按编码规范写好字段注释。验证：Flyway 迁移成功
- [ ] 3.3 （待部署时验证：代码与测试已完成，本机没有 ffmpeg，两个真实转码测试被跳过）实现视频转码队列（线程池 `media-transcode-pool`，并发 1）：下载原片 → 调用 ffmpeg → 上传 → 在事务中更新记录 → 删除原片；失败时保留原片；启动时把仍在转码中的记录重新入队；没有安装 ffmpeg 时直接记为失败。验证：在装有 ffmpeg 的环境中，集成测试把一个 MOV 小样片转成 MP4；模拟转码失败时原片仍可读取

## 4. 各模块接入统一存储

- [x] 4.1 迁移：给 `customer_inquiry_attachment`、`opportunity_attachment`、`supplier_attachment`、`purchase_order_attachment`、`document_template_version`、`sourcing_import` 增加 `storage` 字段（默认 `LOCAL`）。验证：Flyway 迁移成功，已有数据读取正常
- [x] 4.2 业务附件 `biz_attachment`：`AttachmentStore` 并入 `FileStorage`，上传时走压缩或转码，目录按所属单据类型对应到环节（`SHOOT` 按品牌和型号）。验证：`AttachmentService` 相关测试通过，新增「实拍素材按型号归档」「水单放财务目录」两个场景的测试
- [x] 4.3 客户询盘附件与 AI 解析：改用 `FileStorage`；AI 解析通过读取流获取文件内容，不再依赖本地路径。验证：客户询盘相关测试通过，AI 解析在桩模式下能读到附件
- [x] 4.4 商机附件（包括转询盘时复制附件）、供应商附件、采购合同附件改用 `FileStorage`。验证：各模块附件上传和下载测试通过，复制附件后两条记录各自能读取
- [x] 4.5 单据模板：改用 `FileStorage`；转换前把模板下载到 LibreOffice 的临时工作目录，用完删除。验证：单据模板预览和导出测试通过
- [x] 4.6 询价导入文件、收款水单（`file_keys` 数组逐项带 `storage`）改用 `FileStorage`。验证：询价导入和收款单水单的相关测试通过，旧数据（没有 `storage` 属性）仍能读取
- [x] 4.7 商品图片与视频、站点外观图片改为写公开存储，保存公开地址；商品视频走转码。验证：商品图片与视频的现有测试通过，上传后返回的地址可以直接访问
- [x] 4.8 所有下载和预览接口改为返回 `{url, fileName, expiresAt}`，权限校验不变；删除不再使用的本地路径接口和旧的存储类（`PurchaseAttachmentStorage`、`SupplierAttachmentStorage` 等），全局搜索确认没有残留的 `resolveOwned` 调用。验证：契约测试覆盖有权限时返回链接、无权限时拒绝、链接过期后需要重新获取

## 5. 前端

- [x] 5.1 新增统一工具 `useFileUrl` / `openFile`：取链接后用于预览、播放或下载，链接过期时重新获取。验证：`npx tsc --noEmit -p .` 通过
- [x] 5.2 按模块替换约 21 处 blob 下载：客户询盘、商机、供应商、采购、仓库（发货、入库、实拍）、财务凭证、询价回价、单据模板；视频改为 `<video src>` 直接播放；显示转码中和失败状态。验证：Biome、tsc、`npx antd lint ./src` 通过（以现有警告数为基线）；全局搜索确认附件相关代码不再使用 `responseType: 'blob'`（打包下载 Excel 等非附件用途除外）

## 6. 历史文件迁移与清理

- [x] 6.1 实现迁移命令 `zhul.storage.migrate=dry-run|run`：逐表分批处理，压缩图片、视频入转码队列，校验 OSS 上的大小后更新记录，单条失败记入报告，可以重复执行；`product_media.file_url` 和站点外观配置里的地址改写为公开地址。验证：集成测试在本地准备各表样例文件，使用模拟的 OSS 实现，预演时不改数据；执行后所有记录为 OSS；重跑时跳过已迁移的记录
- [x] 6.2 实现清理命令 `zhul.storage.cleanup=dry-run|run`：只删除已核对过的本地文件，没有记录引用的文件只列入报告。验证：集成测试覆盖 OSS 上缺失的文件保留在本地并进入报告
- [x] 6.3 删除公开目录里遗留的 `uploads/customer-inquiry`（数据库没有引用），并确认 `.gitignore` 和部署挂载不受影响。验证：目录不存在，客户询盘附件读取正常

## 7. 文档与整体验证

- [x] 7.1 部署文档新增「对象存储」一篇：创建存储桶、RAM 子账号最小权限策略、跨域规则示例、切换步骤、迁移与清理命令、回退方式；`.env.example` 写明每个配置项。验证：按文档在一个测试存储桶上完整走一遍
- [x] 7.2 后端全量测试，并从日志的汇总行确认结果，不看 `-q` 的输出：`mvn -o test > scratchpad/mvn.log`，然后检索 `Tests run: … Failures`；前端执行 Biome、tsc、`npx antd lint ./src`
- [x] 7.3 重启开发环境（确认 Flyway 执行到新的迁移版本），在浏览器中逐个模块上传、预览、下载：图片已压缩、视频可以直接播放且能拖动、水单清晰、无权限账号拿不到链接
- [x] 7.4 用测试存储桶把开发环境切到 OSS，执行预演、迁移、清理，抽查各模块的文件都能正常打开
