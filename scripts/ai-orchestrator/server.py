#!/usr/bin/env python3
"""
真正调用 AI 的编排服务实现——通过 Claude（Anthropic API）或 OpenAI（Responses API）的官方 Python SDK
执行本目录 skills/ 下的 inquiry-parser + order-splitter 两个 skill，替代
scripts/ai-orchestrator-stub 里那个返回固定假数据的占位脚本。服务商由 AI_ORCHESTRATOR_PROVIDER 选择。

对应 openspec/changes/add-inquiry-management/design.md 决策3的通用契约：
  POST /skills/{skill_id}/run
    body: {"input": {...}, "callback_url": "http://.../api/v1/ai-tasks/{id}/callback"}
    -> 202 {"job_id": "..."}
  处理完成后（可能是几十秒到几分钟）:
  POST {callback_url}
    header: X-AI-Callback-Token: <与 zhul-erp-backend 配置一致的共享密钥>
    body: {"status": "success"|"failed", "output": {...}|null, "error": null|string}

依赖：Python 3.10+ 与 `anthropic`、`openai` SDK（见 requirements.txt）；鉴权用环境变量
AI_ORCHESTRATOR_API_KEY（填当前服务商的 Key），不需要安装 Claude Code CLI。

## 真实调用会产生费用

每次处理都按 API 用量计费。API 没有「单次最多花多少钱」的开关，这里用
联网搜索次数上限（AI_ORCHESTRATOR_MAX_WEB_SEARCHES）和输出长度上限间接控制，
每次调用完成后在日志里打印 token 用量与搜索次数，便于核对费用。

## 实现要点

- 结构化输出：Claude 用 output_config.format、OpenAI 用 text.format 指定 JSON Schema，返回的 JSON
  直接符合后端字段约定（驼峰命名、confidence 用整数），不再需要一层格式转换。OpenAI 严格模式要求字段
  全部必填，发送前把非必填字段改成可空，收到后删掉值为 null 的字段，回调内容与 Claude 一致。
- 联网搜索：两家都用服务商托管的 web_search 工具，在服务商服务器上执行，本服务不实现搜索逻辑。
- 附件：图片、PDF 由本服务读取本地文件后随请求发送，因此需要能读到后端的私有上传目录
  （Docker 部署时两个容器挂载同一个目录）。
- Claude 长时间的搜索轮次可能以 pause_turn 暂停，原样续传即可继续。
- 拒答兜底：Claude 启用服务端 fallbacks="default"，模型因安全策略拒答时由服务端改用推荐的备用模型重试。
  Haiku 没有服务端兜底，用 Haiku 时不带这个参数；OpenAI 没有对应功能，拒答直接回调失败。
- 模型：询盘解析用 AI_ORCHESTRATOR_PARSE_MODEL；型号描述翻译、联系人名音译任务简单、不联网搜索，
  用 AI_ORCHESTRATOR_TRANSLATE_MODEL。留空时按服务商取 DEFAULT_MODELS 里的推荐模型。
  思考强度分别由 AI_ORCHESTRATOR_PARSE_EFFORT、AI_ORCHESTRATOR_TRANSLATE_EFFORT 设置，默认 medium。
"""
import base64
import copy
import json
import os
import re
import sys
import threading
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer

import anthropic
import openai

PORT = int(os.environ.get("AI_STUB_PORT", "8899"))
CALLBACK_TOKEN = os.environ.get("AI_TASK_CALLBACK_TOKEN", "zhul-erp-ai-callback-secret")
# 每个服务商的推荐模型：询盘解析要识图、联网核实型号，用能力强的；翻译简单，用便宜的
DEFAULT_MODELS = {
    "claude": {"parse": "claude-opus-5-5", "translate": "claude-haiku-5-5"},
    "openai": {"parse": "gpt-6.1-sol", "translate": "gpt-6-luna"},
}
EFFORTS = ("low", "medium", "high", "xhigh", "max")
PROVIDER = (os.environ.get("AI_ORCHESTRATOR_PROVIDER") or "claude").strip().lower()
API_KEY = (os.environ.get("AI_ORCHESTRATOR_API_KEY") or "").strip()
_defaults = DEFAULT_MODELS.get(PROVIDER, DEFAULT_MODELS["claude"])
PARSE_MODEL = os.environ.get("AI_ORCHESTRATOR_PARSE_MODEL") or _defaults["parse"]
TRANSLATE_MODEL = os.environ.get("AI_ORCHESTRATOR_TRANSLATE_MODEL") or _defaults["translate"]
PARSE_EFFORT = (os.environ.get("AI_ORCHESTRATOR_PARSE_EFFORT") or "medium").strip().lower()
TRANSLATE_EFFORT = (os.environ.get("AI_ORCHESTRATOR_TRANSLATE_EFFORT") or "medium").strip().lower()
MAX_WEB_SEARCHES = int(os.environ.get("AI_ORCHESTRATOR_MAX_WEB_SEARCHES") or "20")
# 单次 API 调用超时（秒）——联网搜索验证一条含多个型号的询盘可能是分钟级，
# 这里给足余量；对应地 zhul-erp-backend 的 AiTaskTimeoutWatchdog 超时阈值
# 也已经调大，避免这边还没跑完就被那边判定超时。
TIMEOUT_SECONDS = int(os.environ.get("AI_ORCHESTRATOR_TIMEOUT_SECONDS") or "900")
# pause_turn 最多续传几次，防止异常情况下无限循环
MAX_CONTINUATIONS = 5
FALLBACK_BETA = "server-side-fallback-2026-07-01"
IMAGE_TYPES = {".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".gif": "image/gif", ".webp": "image/webp"}

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
                                "categoryCode": {"type": "string"},
                                "originalModel": {"type": "string"},
                                "confirmedModel": {"type": "string"},
                                "confidence": {"type": "integer"},
                                "correctionNote": {"type": "string"},
                                "description": {"type": "string"},
                                "descriptionEn": {"type": "string"},
                                "quantity": {"type": "integer"},
                                "unit": {"type": "string"},
                                "remark": {"type": "string"},
                                "lifecycle": {"type": "integer"},
                                "replacementModel": {"type": "string"},
                                "difficulty": {"type": "integer"},
                                "inquiryScript": {"type": "string"},
                                "searchKeywords": {"type": "array", "items": {"type": "string"}},
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


def _category_listing(categories: list) -> str:
    """后端带来的品类清单：一级在前、其细分缩进列在下面，每行「编码｜中文名｜英文名」"""
    lines = []
    for c in categories:
        indent = "  - " if c.get("parentCode") else ""
        lines.append(f"{indent}{c.get('code', '')}｜{c.get('nameZh') or ''}｜{c.get('name') or ''}")
    return "\n".join(lines)


def build_system_prompt(categories=None) -> str:
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

【每个型号额外输出的字段——联网搜索验证时一并判断】
- description：中文描述（给内部采购询价看），沿用 skill 原有写法
- descriptionEn：英文描述（给海外客户看，会出现在报价单、PI 上）：英文品名 + 关键规格，品牌用英文名（如西门子写 Siemens），型号、规格参数和单位原样保留，不要出现中文，不超过 300 个字符；例如「Siemens 3VA2 MCCB, 400A 4P, 55kA/415V, ETU560 LSIG trip unit」；型号未识别时留空字符串
- lifecycle 生命周期（整数）：官网或代理商在售=1（在产）；官网确认停产或已无库存=2（停产）；查不到或不确定=3（待查）
- replacementModel：停产且官方给出替代型号时填写替代型号，否则留空字符串；替代型号需确认规格兼容，不能随意等同
- difficulty 采购难度（整数）：常见在产型号、国内平台容易买到=1（简单）；冷门、停产有替代或货源少=2（中等）；停产无替代、稀缺或需海外渠道=3（困难）
- inquiryScript：发给国内供应商的询价话术，格式固定为「帮我查一下 {{品牌中文名或英文名}} 的 {{确认型号}}，要 {{数量}} {{单位}}，报不含税价，有货的话货期也说一下，谢谢。」；停产且有替代型号时写成「{{型号}}（已停产）或替代型号 {{替代型号}}，各 {{数量}} {{单位}}，优先按替代型号询价；若原型号有现货亦可，报不含税价和货期，谢谢。」
- searchKeywords：货源搜索关键词，2 到 5 个，按使用顺序：① 精确型号；② 品牌英文 + 型号；③ 品牌中文 + 中文品名（如「欧姆龙 光电传感器」）；④ 纯规格中文品名；停产有替代时最后加「替代型号」。只给关键词，不要给链接
""" + _category_rules(categories or [])


def _category_rules(categories: list) -> str:
    if not categories:
        return ""
    return f"""
【品类——覆盖 inquiry-parser 里的「category 合法枚举值」，以下面的品类清单为准】
- categoryCode：每个型号必填，只能填下面清单里的编码。优先选细分品类（缩进的行）；联网搜索后仍判断不了细分品类时选它的一级品类（不缩进的行）；都不符合时选 other
- 分组的 category 填该组型号所属一级品类的中文名；按「品牌 + 一级品类」分组，同一品牌同一一级品类下不同细分品类的型号放在同一组
- 话术模版里提到品类时用一级品类中文名

品类清单（编码｜中文名｜英文名）：
{_category_listing(categories)}
"""


def _strict(schema):
    """结构化输出要求每个 object 都声明 additionalProperties: false"""
    if isinstance(schema, dict):
        if schema.get("type") == "object":
            schema.setdefault("additionalProperties", False)
        for value in schema.values():
            _strict(value)
    elif isinstance(schema, list):
        for value in schema:
            _strict(value)
    return schema


def _read_attachment(ref) -> tuple:
    """附件内容与扩展名：ref 为本地路径（旧版后端，共享磁盘）或 {"url", "ext"}（后端签发的临时链接）"""
    if isinstance(ref, dict):
        with urllib.request.urlopen(ref["url"], timeout=120) as resp:
            return resp.read(), "." + ref.get("ext", "").lower().lstrip(".")
    with open(ref, "rb") as f:
        return f.read(), os.path.splitext(ref)[1].lower()


def _attachment_data(ref) -> tuple:
    """附件读成 (base64, 扩展名)；ref 也可以是已读好的 (内容, 扩展名)"""
    raw, ext = ref if isinstance(ref, tuple) else _read_attachment(ref)
    if ext != ".pdf" and ext not in IMAGE_TYPES:
        raise RuntimeError(f"不支持的附件类型 {ext}，只支持图片和 PDF")
    return base64.standard_b64encode(raw).decode("ascii"), ext


def _attachment_block(ref) -> dict:
    """Claude 的图片、PDF 内容块"""
    data, ext = _attachment_data(ref)
    if ext == ".pdf":
        return {"type": "document", "source": {"type": "base64", "media_type": "application/pdf", "data": data}}
    return {"type": "image", "source": {"type": "base64", "media_type": IMAGE_TYPES[ext], "data": data}}


def _openai_attachment_block(ref) -> dict:
    """OpenAI 的图片、PDF 输入块"""
    data, ext = _attachment_data(ref)
    if ext == ".pdf":
        return {"type": "input_file", "filename": "attachment.pdf", "file_data": f"data:application/pdf;base64,{data}"}
    return {"type": "input_image", "image_url": f"data:{IMAGE_TYPES[ext]};base64,{data}"}


def call_api(system_prompt: str, text: str, attachments: list, schema: dict, *,
             model: str, effort: str, web_search: bool = False) -> dict:
    """按 AI_ORCHESTRATOR_PROVIDER 调用对应服务商，返回符合 schema 的 JSON"""
    if not API_KEY:
        raise RuntimeError("未配置 AI_ORCHESTRATOR_API_KEY，无法调用 AI")
    call = _call_openai if PROVIDER == "openai" else _call_claude
    return call(system_prompt, text, attachments, schema, model, effort, web_search)


def _call_claude(system_prompt, text, attachments, schema, model, effort, web_search) -> dict:
    """调用 Anthropic API；pause_turn 时原样续传"""
    client = anthropic.Anthropic(api_key=API_KEY, timeout=TIMEOUT_SECONDS)
    content = [_attachment_block(a) for a in attachments] + [{"type": "text", "text": text}]
    messages = [{"role": "user", "content": content}]
    params = {
        "model": model,
        "max_tokens": 64000,
        "system": system_prompt,
        "output_config": {"effort": effort, "format": {"type": "json_schema", "schema": _strict(schema)}},
    }
    # Haiku 没有服务端拒答兜底，带上 fallbacks 也不会生效，按官方说明不传
    if not model.startswith("claude-haiku"):
        params["betas"] = [FALLBACK_BETA]
        params["fallbacks"] = "default"
    if web_search:
        params["tools"] = [{"type": "web_search_20260209", "name": "web_search", "max_uses": MAX_WEB_SEARCHES}]
    for _ in range(MAX_CONTINUATIONS + 1):
        with client.beta.messages.stream(messages=messages, **params) as stream:
            response = stream.get_final_message()
        if response.stop_reason != "pause_turn":
            break
        messages = [messages[0], {"role": "assistant", "content": response.content}]
    else:
        raise RuntimeError(f"联网搜索续传超过 {MAX_CONTINUATIONS} 次仍未完成")

    if response.stop_reason == "refusal":
        raise RuntimeError("模型拒绝处理这条内容")
    if response.stop_reason == "max_tokens":
        raise RuntimeError("输出超过长度上限，内容可能过多，请拆分后重试")
    texts = [b.text for b in response.content if b.type == "text"]
    if not texts:
        raise RuntimeError("AI 未返回结果")
    usage = response.usage
    searches = getattr(getattr(usage, "server_tool_use", None), "web_search_requests", 0) or 0
    print(f"[ai-orchestrator] API 调用完成 model={response.model}，输入 {usage.input_tokens} tokens，"
          f"输出 {usage.output_tokens} tokens，联网搜索 {searches} 次，request_id={response._request_id}")
    return json.loads(texts[-1])


def _openai_schema(schema):
    """OpenAI 严格模式：每个 object 的字段都要列为必填，原来非必填的字段改成可空"""
    schema = copy.deepcopy(schema)

    def walk(node):
        if not isinstance(node, dict):
            return
        if node.get("type") == "array":
            walk(node.get("items"))
        if node.get("type") == "object" and "properties" in node:
            required = set(node.get("required", []))
            for name, prop in node["properties"].items():
                walk(prop)
                if name not in required:
                    prop["type"] = [prop["type"], "null"]
            node["required"] = list(node["properties"])
            node["additionalProperties"] = False

    walk(schema)
    return schema


def _drop_nulls(value):
    """删掉值为 null 的字段，未给出的可选字段与 Claude 一样不出现"""
    if isinstance(value, dict):
        return {k: _drop_nulls(v) for k, v in value.items() if v is not None}
    if isinstance(value, list):
        return [_drop_nulls(v) for v in value]
    return value


def _call_openai(system_prompt, text, attachments, schema, model, effort, web_search) -> dict:
    """调用 OpenAI Responses API"""
    client = openai.OpenAI(api_key=API_KEY, timeout=TIMEOUT_SECONDS)
    content = [_openai_attachment_block(a) for a in attachments] + [{"type": "input_text", "text": text}]
    params = {
        "model": model,
        "instructions": system_prompt,
        "input": [{"role": "user", "content": content}],
        "reasoning": {"effort": effort},
        "max_output_tokens": 64000,
        "text": {"format": {"type": "json_schema", "name": "result", "schema": _openai_schema(schema), "strict": True}},
    }
    if web_search:
        # max_tool_calls 统计全部托管工具调用（搜索、打开网页、页内查找），不只是搜索
        params["tools"] = [{"type": "web_search"}]
        params["max_tool_calls"] = MAX_WEB_SEARCHES
    response = client.responses.create(**params)

    if response.status == "incomplete":
        reason = getattr(response.incomplete_details, "reason", None)
        if reason == "max_output_tokens":
            raise RuntimeError("输出超过长度上限，内容可能过多，请拆分后重试")
        if reason == "content_filter":
            raise RuntimeError("模型拒绝处理这条内容")
        raise RuntimeError(f"AI 未完成处理（{reason}）")
    for item in response.output:
        if item.type == "message" and any(part.type == "refusal" for part in item.content):
            raise RuntimeError("模型拒绝处理这条内容")
    if not response.output_text:
        raise RuntimeError("AI 未返回结果")
    usage = response.usage
    searches = sum(1 for item in response.output if item.type == "web_search_call")
    print(f"[ai-orchestrator] API 调用完成 model={response.model}，输入 {usage.input_tokens} tokens，"
          f"输出 {usage.output_tokens} tokens，联网搜索 {searches} 次，response_id={response.id}")
    return _drop_nulls(json.loads(response.output_text))


def call_parse(raw_content: str, file_paths=None, categories=None) -> dict:
    """raw_content 为正文（Excel/CSV 附件已由后端抽成文本拼在里面）；file_paths 为图片、PDF 附件（已读好的 (内容, 扩展名)，或临时链接、本地路径）；
    categories 为后端带来的品类清单（没有时沿用 skill 里的品类枚举）。"""
    file_paths = file_paths or []
    if file_paths:
        text = (
            (f"询盘正文：\n{raw_content}\n\n" if raw_content else "这条询盘没有正文。\n")
            + "上面附有询盘附件（图片或 PDF）。请先识别附件中的型号、数量等询盘信息，与正文合并当成询盘原文，再按 skill 流程继续处理。"
        )
    else:
        text = raw_content
    structured = call_api(build_system_prompt(categories), text, file_paths, OUTPUT_SCHEMA,
                          model=PARSE_MODEL, effort=PARSE_EFFORT, web_search=True)
    if "groups" not in structured:
        raise RuntimeError("AI 未返回符合 schema 的结构化输出")
    return structured


TRANSLATE_SKILL = "translate-item-descriptions"

TRANSLATE_SCHEMA = {
    "type": "object",
    "properties": {
        "items": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {"key": {"type": "string"}, "text": {"type": "string"}},
                "required": ["key", "text"],
            },
        }
    },
    "required": ["items"],
}

TRANSLATE_PROMPT = """你是工控自动化产品的外贸翻译。把下面每一条中文型号描述翻译成给海外客户看的英文描述：
- 英文品名 + 关键规格，品牌用英文名（如西门子写 Siemens，三菱写 Mitsubishi，欧姆龙写 Omron）
- 型号、规格参数、单位原样保留，不要改写或补全
- 不要出现中文，每条不超过 300 个字符
- key 原样返回，每条输入都要有对应输出
只输出 JSON。"""


NAME_SKILL = "translate-contact-names"

NAME_PROMPT = """你是外贸公司的翻译。把下面每一条客户联系人名称（多为英文、俄文等外文人名）音译成常用的中文译名：
- 常见英文名用通行译法（如 John 写约翰，Michael 写迈克尔），姓和名之间用「·」分隔
- 已经是中文的原样返回；看不出是人名的（如公司名、缩写）也按发音音译
- 只输出中文译名，不加称谓和解释，每条不超过 30 个字符
- key 原样返回，每条输入都要有对应输出
只输出 JSON。"""


def call_translate(items: list, prompt: str = TRANSLATE_PROMPT) -> dict:
    """通过 API 翻译型号描述或联系人名称，不启用搜索工具。"""
    text = json.dumps({"items": items}, ensure_ascii=False)
    structured = call_api(prompt, text, [], TRANSLATE_SCHEMA, model=TRANSLATE_MODEL, effort=TRANSLATE_EFFORT)
    if "items" not in structured:
        raise RuntimeError("AI 未返回符合 schema 的翻译结果")
    print(f"[ai-orchestrator] 翻译完成 {len(items)} 条")
    return structured


def process_translate(payload: dict, callback_url: str, prompt: str = TRANSLATE_PROMPT):
    try:
        items = (payload.get("input") or {}).get("items") or []
        if not items:
            send_callback(callback_url, "failed", error="没有需要翻译的内容")
            return
        send_callback(callback_url, "success", output=call_translate(items, prompt))
    except (anthropic.APITimeoutError, openai.APITimeoutError):
        send_callback(callback_url, "failed", error=f"AI 处理超过 {TIMEOUT_SECONDS} 秒未完成")
    except Exception as e:
        print(f"[ai-orchestrator] translate failed: {e}")
        send_callback(callback_url, "failed", error=str(e)[:500])


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
        # Excel/CSV 附件已由后端抽成文本拼进 rawContent；图片、PDF 通过 attachments 传后端签发的临时链接
        # （[{"url", "ext"}]，本服务下载后发给模型）。旧版后端传本地路径 attachmentPaths / rawAttachmentPath，这里一并兼容。
        refs = [a for a in (input_data.get("attachments") or []) if a and a.get("url")]
        paths = [p.strip() for p in (input_data.get("attachmentPaths") or []) if p and p.strip()]
        legacy = (input_data.get("rawAttachmentPath") or "").strip()
        if legacy and legacy not in paths:
            paths.append(legacy)
        if not raw_content and not refs and not paths:
            send_callback(callback_url, "failed", error="询盘既没有正文也没有可读的附件，无法解析")
            return
        missing = [p for p in paths if not os.path.isfile(p)]
        if missing:
            send_callback(callback_url, "failed", error=f"附件文件不存在: {missing[0]}")
            return
        try:
            loaded = [_read_attachment(r) for r in refs + paths]
        except Exception as e:  # noqa: BLE001 - 链接过期或网络问题都按附件读取失败回报
            send_callback(callback_url, "failed", error=f"附件下载失败: {str(e)[:200]}")
            return
        output = call_parse(raw_content, loaded, input_data.get("categories") or [])
        send_callback(callback_url, "success", output=output)
    except (anthropic.APITimeoutError, openai.APITimeoutError):
        send_callback(callback_url, "failed", error=f"AI 处理超过 {TIMEOUT_SECONDS} 秒未完成")
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

        if skill_id in (TRANSLATE_SKILL, NAME_SKILL):
            prompt = NAME_PROMPT if skill_id == NAME_SKILL else TRANSLATE_PROMPT
            if callback_url:
                threading.Thread(target=process_translate, args=(payload, callback_url, prompt), daemon=True).start()
            return

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
    sys.stdout.reconfigure(line_buffering=True)
    sys.stderr.reconfigure(line_buffering=True)

    # 配置写错时直接退出，容器反复重启，docker ps 和日志能马上看到原因
    errors = []
    if PROVIDER not in DEFAULT_MODELS:
        errors.append(f"AI_ORCHESTRATOR_PROVIDER={PROVIDER} 不支持，可选：{'、'.join(DEFAULT_MODELS)}")
    for name, value in (("AI_ORCHESTRATOR_PARSE_EFFORT", PARSE_EFFORT), ("AI_ORCHESTRATOR_TRANSLATE_EFFORT", TRANSLATE_EFFORT)):
        if value not in EFFORTS:
            errors.append(f"{name}={value} 不支持，可选：{'、'.join(EFFORTS)}")
    if errors:
        for e in errors:
            print(f"[ai-orchestrator] 配置错误：{e}", file=sys.stderr)
        sys.exit(1)

    server = HTTPServer(("0.0.0.0", PORT), Handler)
    print(f"[ai-orchestrator] listening on :{PORT}（服务商 {PROVIDER}，每次调用按用量计费）")
    print(f"[ai-orchestrator] 询盘解析 model={PARSE_MODEL} effort={PARSE_EFFORT}；"
          f"翻译 model={TRANSLATE_MODEL} effort={TRANSLATE_EFFORT}")
    if not API_KEY:
        print("[ai-orchestrator] 警告：未配置 AI_ORCHESTRATOR_API_KEY，AI 解析与翻译任务会直接回调失败")
    server.serve_forever()
