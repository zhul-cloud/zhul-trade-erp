# Tasks

## 1. SDK 元数据兼容

- [x] 1.1 用真实 Anthropic 1.14.0 + httpx2 MockTransport/SSE 复现完整解析及回调旧错误，证明 final message 无 _request_id，记录红测试证据。
- [x] 1.2 从 stream.request_id 获取可选标识，修正 FakeClaude 形态；有/无 header 的完整解析、翻译与成功回调回归变绿，业务错误仍失败。

## 2. 输入与整体回归

- [x] 2.1 使用内存有效 PNG/PDF 与正文/已提取表格文本验证真实 SDK 请求体协议，核对后端签名链接和提取链路，记录格式支持边界。
- [x] 2.2 补充 SpreadsheetText 的 XLS/XLSX/CSV 独立单元测试，运行指定测试证明提取文本，不修改生产代码或运行数据库集成。
- [x] 2.3 运行 Linux 全部编排 unittest、目标 OpenSpec strict 和 git diff --check，记录完整结果与云端验证边界。

## Workflow follow-up

- 经主代理验收后提交正规修复 PR 到最新 release，不自行合并或部署。
- 若主代理安排单次真实云模型合成验证，区分实测与离线协议证据。

## Verification evidence

- 红测试（server 未改时）：真实镜像 Anthropic 1.14.0 + httpx2.MockTransport SSE，`test_claude_stream.py` 最初 5 项，3 failures / 2 errors。完整 call_parse、call_translate 在 server.py 日志行抛 `AttributeError: 'ParsedBetaMessage' object has no attribute '_request_id'`；process_job 实际发送 failed 回调。独立 SDK 类型断言确认最终对象为 ParsedBetaMessage 且无该属性，stream.request_id 从响应头取得。
- 绿测试：同一镜像只读挂载工作树，`--network none --read-only --tmpfs /tmp:rw,nosuid,nodev,exec,size=128m`，`python -B -m unittest discover -s /app/ai-orchestrator -q`：53 tests，8.800s，OK，exit 0，无跳过。真实 SDK 6 项覆盖有/无 header、最终消息无私有属性、完整解析/翻译/成功回调、有效 PNG/PDF 输入、已提取表格文本、pause_turn 续传与业务错误。
- Java：本机 `mvn -o -Dtest=SpreadsheetTextTest test`，仅执行新增 SpreadsheetTextTest：3 tests，0 failures/errors/skipped，BUILD SUCCESS，37.538s。XLS 与 XLSX 使用内存 Workbook 写入 @TempDir，CSV 保留中文/型号/数量原文，无 Spring/数据库集成。运行前不存在的 target 已验证位于本 worktree 并精确清理；源测试保留。
- 输入链路证据：CustomerInquiryServiceImpl.parseInput 对 xls/xlsx/csv 调 SpreadsheetText.extract 拼入 rawContent；其余图片/PDF 签发 60 分钟链接。Python process_job 下载为字节，call_parse 保留正文，Claude SDK 出站为 image/document/text，media_type 与 base64 还原内容验证一致。纯文本也走完整 call_parse 成功；API 不直接接收 Excel 二进制。
- 目标 OpenSpec strict 与 git diff --check 均通过。server.py LF SHA256 为 `e10f798647768c37a8c280ce833648d6a0a8b38f7d0a79b9161d72db2975f8ed`。
- 离线边界：上述 MockTransport 和 Java 单测未读取真实 API Key 或调用云模型；不能单独据此宣称云端格式验收。后续受控真实验证见下项，未实调订阅账号。

- 受控真实 API（主代理转述诊断代理实测）：同一冻结 LF SHA256 的 server.py 经真实 Anthropic 1.14.0 完整 `_call_claude` 路径，`claude-opus-5-5`，一次合成正文 + PNG + PDF 请求，4000 输出 token 上限，不启用搜索，不重试。HTTP 200，end_turn，9.56 秒，input 3561/output 522 tokens；三种输入中的型号与数量 1/2/3 pcs 均命中，按原业务 Schema 验证通过且 Schema 未污染。真实 ParsedBetaMessage 无 `_request_id`，stream.request_id 日志正常。
- 真实验证边界：诊断代理在服务器内存中读取既有 Key 以完成该次调用，未导出或落盘凭据；没有业务回调、业务任务重试、数据库写入或生产服务修改。本修复尚未部署，纯文本/PNG/PDF 云端合成实测与 XLS/XLSX/CSV 本地提取验证分别成立；不等同云模型直接接收 Excel 文件。
