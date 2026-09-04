#!/usr/bin/env python3
"""
真正调用 AI 的编排服务实现——用 Claude Code CLI（`claude -p`，非交互模式）执行
本目录 skills/ 下的 inquiry-parser + order-splitter 两个 skill，替代
scripts/ai-orchestrator-stub 里那个返回固定假数据的占位脚本。

对应 openspec/changes/add-inquiry-management/design.md 决策3的通用契约：
  POST /skills/{skill_id}/run
    body: {"input": {...}, "callback_url": "http://.../api/v1/ai-tasks/{id}/callback"}
    -> 202 {"job_id": "..."}
  处理完成后（可能是几十秒到几分钟）:
  POST {callback_url}
    header: X-AI-Callback-Token: <与 zhul-erp-backend 配置一致的共享密钥>
    body: {"status": "success"|"failed", "output": {...}|null, "error": null|string}

只依赖 Python 标准库和本机已登录的 `claude` CLI（订阅版账号即可，不需要单独的
ANTHROPIC_API_KEY——这是走 Claude Code 的 OAuth 登录态，不是 `--bare` 模式）。

## 真实调用会产生费用

每次处理都会消耗你当前登录账号的真实用量，不是免费的。用
`--max-budget-usd` 给单次调用设了上限（见 AI_ORCHESTRATOR_MAX_BUDGET_USD），
超过就会失败而不是无限烧钱，但没有办法完全避免产生费用。

## 已验证过的技术细节（写这个脚本之前实测过，不是猜的）

- `--json-schema` 配合 `--output-format json` 可以直接拿到符合我们后端字段约定
  （驼峰命名、confidence 用整数）的 `structured_output`，不需要再写一层格式转换。
- `--json-schema` 模式不会阻止真实调用 WebSearch——一开始看 `usage.server_tool_use`
  以为没搜索，后来发现搜索请求数记在 `modelUsage.<model>.webSearchRequests`
  里（Claude Code 内部会用一个更便宜的模型执行搜索这类子任务），不是顶层那个
  字段，实测每个型号确实各触发了一次搜索。
- `--allowedTools WebSearch` + `--permission-prompts none`：精确只放行联网搜索，
  其余工具（改文件、跑命令等）一律自动拒绝而不是卡住等待交互确认，不需要用
  `--dangerously-skip-permissions` 这种放开一切的模式。
"""
import json
import os
import re
import subprocess
import threading
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(os.environ.get("AI_STUB_PORT", "8899"))
CALLBACK_TOKEN = os.environ.get("AI_TASK_CALLBACK_TOKEN", "zhul-erp-ai-callback-secret")
CLAUDE_BIN = os.environ.get("CLAUDE_BIN", "claude")
MAX_BUDGET_USD = os.environ.get("AI_ORCHESTRATOR_MAX_BUDGET_USD", "5.00")
# claude 子进程超时（秒）——真实联网搜索验证一条含多个型号的询盘可能是分钟级，
# 这里给足余量；对应地 zhul-erp-backend 的 AiTaskTimeoutWatchdog 超时阈值
# 也已经调大，避免这边还没跑完就被那边判定超时。
CLAUDE_TIMEOUT_SECONDS = int(os.environ.get("AI_ORCHESTRATOR_CLAUDE_TIMEOUT_SECONDS", "900"))

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SKILL_DIR = os.path.join(SCRIPT_DIR, "skills")

OUTPUT_SCHEMA = {
    "type": "object",
    "properties": {
        "groups": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "brand": {"type": "string"},
                    "category": {"type": "string"},
                    "inquiryTemplate": {"type": "string"},
                    "emailTemplateCn": {"type": "string"},
                    "emailTemplateEn": {"type": "string"},
                    "items": {
                        "type": "array",
                        "items": {
                            "type": "object",
                            "properties": {
                                "originalModel": {"type": "string"},
                                "confirmedModel": {"type": "string"},
                                "confidence": {"type": "integer"},
                                "correctionNote": {"type": "string"},
                                "description": {"type": "string"},
                                "quantity": {"type": "integer"},
                                "unit": {"type": "string"},
                                "remark": {"type": "string"},
                            },
                            "required": ["originalModel", "confirmedModel", "confidence", "quantity", "unit"],
                        },
                    },
                },
                "required": ["brand", "category", "items"],
            },
        }
    },
    "required": ["groups"],
}


def _read_skill(name: str) -> str:
    with open(os.path.join(SKILL_DIR, name, "SKILL.md"), encoding="utf-8") as f:
        return f.read()


def build_system_prompt() -> str:
    parser = _read_skill("inquiry-parser")
    splitter = _read_skill("order-splitter")
    return f"""你要依次严格执行下面两个 skill 的完整流程处理这条询盘内容：先执行 inquiry-parser 做型号提取和联网搜索验证，再把结果交给 order-splitter 按品牌+品类分组并生成话术模版。

【强制要求，优先级高于一切】对于提取到的每一个型号，你必须先真正调用一次 WebSearch 工具去搜索验证，即使你认为自己已经认识这个型号、非常确定它是真实存在的，也必须先搜索、拿到搜索结果后再判定 confidence，不允许跳过搜索直接凭训练知识作答。

【inquiry-parser skill 原文】
{parser}

【order-splitter skill 原文】
{splitter}

【输出格式覆盖规则——优先于上面两个 skill 原有的输出格式说明，必须严格遵守】
最终只输出一个 JSON 对象，字段名必须完全一致（驼峰命名），对应关系：
- model -> confirmedModel
- note -> remark
- confidence 原来是中文字符串，现在必须转换成整数：确认=1，已纠正=2，待核实=3，未识别=4
- email_cn -> emailTemplateCn，email_en -> emailTemplateEn，inquiry_template -> inquiryTemplate（字段名不变）
- 不要输出 master、sub_inquiry_id、item_count、delivery 这些字段
"""


def call_claude(raw_content: str) -> dict:
    system_prompt = build_system_prompt()
    cmd = [
        CLAUDE_BIN,
        "-p",
        raw_content,
        "--append-system-prompt",
        system_prompt,
        "--output-format",
        "json",
        "--json-schema",
        json.dumps(OUTPUT_SCHEMA),
        "--allowedTools",
        "WebSearch",
        "--permission-prompts",
        "none",
        "--max-budget-usd",
        MAX_BUDGET_USD,
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=CLAUDE_TIMEOUT_SECONDS)
    if result.returncode != 0:
        raise RuntimeError(f"claude 进程退出码 {result.returncode}: {result.stderr[-2000:]}")

    envelope = json.loads(result.stdout)
    if envelope.get("is_error"):
        raise RuntimeError(f"claude 返回错误: {envelope.get('result')}")

    structured = envelope.get("structured_output")
    if not structured or "groups" not in structured:
        raise RuntimeError("claude 未返回符合 schema 的结构化输出")

    cost = envelope.get("total_cost_usd")
    print(f"[ai-orchestrator] claude 调用完成，费用约 ${cost}，耗时 {envelope.get('duration_ms')}ms")
    return structured


def send_callback(callback_url: str, status: str, output=None, error=None):
    body = json.dumps({"status": status, "output": output, "error": error}).encode("utf-8")
    req = urllib.request.Request(
        callback_url,
        data=body,
        method="POST",
        headers={"Content-Type": "application/json", "X-AI-Callback-Token": CALLBACK_TOKEN},
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f"[ai-orchestrator] callback -> {callback_url} status={resp.status}")
    except Exception as e:
        print(f"[ai-orchestrator] callback FAILED -> {callback_url}: {e}")


def process_job(payload: dict, callback_url: str):
    try:
        input_data = payload.get("input") or {}
        raw_content = (input_data.get("rawContent") or "").strip()
        if not raw_content:
            # 图片/Excel 输入目前没有真实的文件存储和下载能力（见前端实现说明），
            # rawAttachmentUrl 只是占位字符串，这里没法真的去读取——只处理有
            # 文本内容的情况，没有文本内容时明确失败而不是假装处理。
            send_callback(callback_url, "failed", error="rawContent 为空，本地编排服务暂不支持图片/Excel附件解析")
            return
        output = call_claude(raw_content)
        send_callback(callback_url, "success", output=output)
    except subprocess.TimeoutExpired:
        send_callback(callback_url, "failed", error=f"claude 处理超过 {CLAUDE_TIMEOUT_SECONDS} 秒未完成")
    except Exception as e:
        print(f"[ai-orchestrator] job failed: {e}")
        send_callback(callback_url, "failed", error=str(e)[:500])


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        match = re.fullmatch(r"/skills/([^/]+)/run", self.path)
        if not match:
            self.send_response(404)
            self.end_headers()
            return
        skill_id = match.group(1)

        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            payload = {}

        callback_url = payload.get("callback_url")
        job_id = str(uuid.uuid4())
        print(f"[ai-orchestrator] received skill={skill_id}, will callback {callback_url} (job_id={job_id})")

        self.send_response(202)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps({"job_id": job_id}).encode("utf-8"))

        if skill_id != "inquiry-parse-and-split":
            if callback_url:
                send_callback(callback_url, "failed", error=f"本编排服务尚未实现 skill_id={skill_id}")
            return

        if callback_url:
            threading.Thread(target=process_job, args=(payload, callback_url), daemon=True).start()

    def log_message(self, format, *args):
        print(f"[ai-orchestrator] {self.address_string()} - {format % args}")


if __name__ == "__main__":
    # 无缓冲输出——重定向到文件/nohup时 Python 默认是整块缓冲，日志会迟迟不落盘，
    # 排查问题时容易误以为脚本卡住或什么都没做。
    import sys
    sys.stdout.reconfigure(line_buffering=True)
    sys.stderr.reconfigure(line_buffering=True)

    server = HTTPServer(("0.0.0.0", PORT), Handler)
    print(f"[ai-orchestrator] listening on :{PORT}（真实 Claude Code 后端，每次调用会产生真实费用）")
    server.serve_forever()
