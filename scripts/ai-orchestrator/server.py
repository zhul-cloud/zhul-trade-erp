#!/usr/bin/env python3
"""
真正调用 AI 的编排服务实现——支持 API 及部署者个人订阅（官方 Codex SDK / Claude Code CLI）。
API 通过 Claude（Anthropic API）或 OpenAI（Responses API）的官方 Python SDK
执行本目录 skills/ 下的 inquiry-parser + order-splitter 两个 skill，替代
scripts/ai-orchestrator-stub 里那个返回固定假数据的占位脚本。

服务商、Key、模型、思考强度、搜索上限都由后端随每个任务传入（请求体 ai 字段，来自租户在
「业务设置 → AI 设置」中维护的配置，见 openspec ai-platform/tenant-ai-settings），本服务不读取这些环境变量。
所有接口校验请求头 X-AI-Callback-Token 与 AI_TASK_CALLBACK_TOKEN 一致。

对应 openspec/changes/add-inquiry-management/design.md 决策3的通用契约：
  POST /skills/{skill_id}/run
    body: {"input": {...}, "callback_url": "http://.../api/v1/ai-tasks/{id}/callback", "ai": {...}}
    -> 202 {"job_id": "..."}
  处理完成后（可能是几十秒到几分钟）:
  POST {callback_url}
    header: X-AI-Callback-Token: <与 zhul-erp-backend 配置一致的共享密钥>
    body: {"status": "success"|"failed", "output": {...}|null, "error": null|string}

另有同步接口：POST /test（测试连接）、POST /models（模型下拉列表）、GET /status（订阅登录状态）。

API 方式用任务携带的 Key（只在本次请求的内存中使用，日志与回调不出现）。订阅方式只供平台租户，
通过专用容器的官方 CLI 登录，依赖及登录说明见 README.md 与部署文档，不复制宿主机个人凭据。

## 真实调用会产生费用

每次处理都按 API 用量计费。API 没有「单次最多花多少钱」的开关，这里用
联网搜索次数上限（任务携带的 max_web_searches）和输出长度上限间接控制，
每次调用完成后在日志里打印 token 用量与搜索次数，便于核对费用。

## 实现要点

- 结构化输出：Claude 用 output_config.format、OpenAI 用 text.format 指定 JSON Schema，返回的 JSON
  符合后端字段约定（驼峰命名、confidence 用整数）。两家 API 发送前均使用全字段必填、原可选字段可空的
  Schema 副本，收到后删掉值为 null 的字段；Claude API 另按原业务 Schema 校验，订阅模式沿用原流程。
- 联网搜索：两家都用服务商托管的 web_search 工具，在服务商服务器上执行，本服务不实现搜索逻辑。
- 附件：图片、PDF 由本服务读取本地文件后随请求发送，因此需要能读到后端的私有上传目录
  （Docker 部署时两个容器挂载同一个目录）。
- Claude 长时间的搜索轮次可能以 pause_turn 暂停，原样续传即可继续。
- 拒答兜底：Claude 启用服务端 fallbacks="default"，模型因安全策略拒答时由服务端改用推荐的备用模型重试。
  Haiku 没有服务端兜底，用 Haiku 时不带这个参数；OpenAI 没有对应功能，拒答直接回调失败。
- 模型：询盘解析用任务携带的 parse_model；型号描述翻译、联系人名音译任务简单、不联网搜索，
  用 translate_model。留空时按服务商取 DEFAULT_MODELS 里的推荐模型；思考强度留空为 medium。
"""
import base64
import copy
import hmac
import json
import os
import re
import sys
import threading
import urllib.request
import uuid
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import anthropic
import jsonschema
import openai

from subscription import call_subscription, codex_models, login_status

PORT = int(os.environ.get("AI_STUB_PORT", "8899"))
CALLBACK_TOKEN = os.environ.get("AI_TASK_CALLBACK_TOKEN", "zhul-erp-ai-callback-secret")
# 每个服务商的推荐模型：询盘解析要识图、联网核实型号，用能力强的；翻译简单，用便宜的
DEFAULT_MODELS = {
    "claude": {"parse": "claude-opus-5-5", "translate": "claude-haiku-5-5"},
    "openai": {"parse": "gpt-6.1-sol", "translate": "gpt-6-luna"},
}
EFFORTS = ("low", "medium", "high", "xhigh", "max")
MODES = ("api", "subscription")
# OpenAI 模型列表里的非对话模型（按名称排除；漏掉的可手动输入）
NON_CHAT_MARKERS = ("embedding", "tts", "whisper", "dall-e", "image", "audio", "realtime", "transcribe", "moderation")
# 单次 API 调用超时（秒）——联网搜索验证一条含多个型号的询盘可能是分钟级，
# 这里给足余量；对应地 zhul-erp-backend 的 AiTaskTimeoutWatchdog 超时阈值
# 也已经调大，避免这边还没跑完就被那边判定超时。
TIMEOUT_SECONDS = int(os.environ.get("AI_ORCHESTRATOR_TIMEOUT_SECONDS") or "900")
# 测试连接、获取模型列表这类同步请求的超时（秒），须短于后端的 30 秒
QUICK_TIMEOUT_SECONDS = 20
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


def mask_key(key: str) -> str:
    """脱敏：保留前 7 位与后 4 位，中间固定 16 个点；不足 12 位时整串换成同样长度的点，与后端一致"""
    if not key:
        return ""
    return "•" * 27 if len(key) < 12 else f"{key[:7]}{'•' * 16}{key[-4:]}"


def scrub(text: str, key: str) -> str:
    """错误信息里如果带了 Key，换成脱敏值"""
    return text.replace(key, mask_key(key)) if key and text else text


class ConfigError(ValueError):
    """任务携带的 AI 配置不合法：只让这个任务失败"""


@dataclass(frozen=True)
class AiConfig:
    mode: str
    provider: str
    api_key: str
    parse_model: str
    translate_model: str
    parse_effort: str
    translate_effort: str
    max_web_searches: int

    def __repr__(self):
        return (f"AiConfig(mode={self.mode}, provider={self.provider}, api_key={mask_key(self.api_key)}, "
                f"parse_model={self.parse_model}, translate_model={self.translate_model}, "
                f"parse_effort={self.parse_effort}, translate_effort={self.translate_effort}, "
                f"max_web_searches={self.max_web_searches})")

    __str__ = __repr__


def parse_ai_config(raw, require_key: bool = True) -> AiConfig:
    """解析请求体 ai 字段并补全默认值；require_key=False 用于测试连接、模型列表（缺 Key 时另行提示）"""
    if not isinstance(raw, dict):
        raise ConfigError("请求缺少 AI 配置（请在「业务设置 → AI 设置」中填写）")

    def text(name):
        return str(raw.get(name) or "").strip()

    mode = text("mode").lower() or "api"
    provider = text("provider").lower() or "claude"
    if mode not in MODES:
        raise ConfigError(f"运行方式 {mode} 不支持，可选：{'、'.join(MODES)}")
    if provider not in DEFAULT_MODELS:
        raise ConfigError(f"服务商 {provider} 不支持，可选：{'、'.join(DEFAULT_MODELS)}")
    efforts = {}
    for name in ("parse_effort", "translate_effort"):
        efforts[name] = text(name).lower() or "medium"
        if efforts[name] not in EFFORTS:
            raise ConfigError(f"思考强度 {efforts[name]} 不支持，可选：{'、'.join(EFFORTS)}")
    value = raw.get("max_web_searches")
    try:
        searches = 20 if value is None or value == "" else int(value)
    except (TypeError, ValueError):
        raise ConfigError("最大搜索次数必须是 1 到 100 的整数") from None
    if not 1 <= searches <= 100:
        raise ConfigError("最大搜索次数必须是 1 到 100 的整数")
    api_key = text("api_key")
    if require_key and mode == "api" and not api_key:
        raise ConfigError("未填写 API Key（请在「业务设置 → AI 设置」中填写）")
    defaults = DEFAULT_MODELS[provider]
    return AiConfig(mode=mode, provider=provider, api_key=api_key,
                    parse_model=text("parse_model") or defaults["parse"],
                    translate_model=text("translate_model") or defaults["translate"],
                    parse_effort=efforts["parse_effort"], translate_effort=efforts["translate_effort"],
                    max_web_searches=searches)


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


def call_api(cfg: AiConfig, system_prompt: str, text: str, attachments: list, schema: dict, *,
             model: str, effort: str, web_search: bool = False) -> dict:
    """按任务携带的配置调用对应服务商，返回符合 schema 的 JSON"""
    if cfg.mode == "subscription":
        wire_schema = _required_nullable_schema(schema) if cfg.provider == "openai" else schema
        result = call_subscription(cfg.provider, system_prompt, text, attachments, wire_schema, model=model,
                                   effort=effort, web_search=web_search, max_web_searches=cfg.max_web_searches,
                                   timeout=TIMEOUT_SECONDS)
        return _drop_nulls(result)
    call = _call_openai if cfg.provider == "openai" else _call_claude
    return call(cfg, system_prompt, text, attachments, schema, model, effort, web_search)


def _call_claude(cfg, system_prompt, text, attachments, schema, model, effort, web_search) -> dict:
    """调用 Anthropic API；pause_turn 时原样续传"""
    client = anthropic.Anthropic(api_key=cfg.api_key, timeout=TIMEOUT_SECONDS)
    content = [_attachment_block(a) for a in attachments] + [{"type": "text", "text": text}]
    messages = [{"role": "user", "content": content}]
    params = {
        "model": model,
        "max_tokens": 64000,
        "system": system_prompt,
        "output_config": {"effort": effort, "format": {
            "type": "json_schema", "schema": _required_nullable_schema(schema)}},
    }
    # Haiku 没有服务端拒答兜底，带上 fallbacks 也不会生效，按官方说明不传
    if not model.startswith("claude-haiku"):
        params["betas"] = [FALLBACK_BETA]
        params["fallbacks"] = "default"
    if web_search:
        params["tools"] = [{"type": "web_search_20260209", "name": "web_search", "max_uses": cfg.max_web_searches}]
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
    result = _drop_nulls(json.loads(texts[-1]))
    try:
        jsonschema.validate(result, schema)
    except jsonschema.ValidationError as exc:
        raise RuntimeError("AI 返回结果不符合业务 Schema") from exc
    return result


def _required_nullable_schema(schema):
    """API 严格输出：使用副本将每个 object 的字段列为必填，原非必填字段改为可空"""
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


def _call_openai(cfg, system_prompt, text, attachments, schema, model, effort, web_search) -> dict:
    """调用 OpenAI Responses API"""
    client = openai.OpenAI(api_key=cfg.api_key, timeout=TIMEOUT_SECONDS)
    content = [_openai_attachment_block(a) for a in attachments] + [{"type": "input_text", "text": text}]
    params = {
        "model": model,
        "instructions": system_prompt,
        "input": [{"role": "user", "content": content}],
        "reasoning": {"effort": effort},
        "max_output_tokens": 64000,
        "text": {"format": {"type": "json_schema", "name": "result",
                            "schema": _required_nullable_schema(schema), "strict": True}},
    }
    if web_search:
        # max_tool_calls 统计全部托管工具调用（搜索、打开网页、页内查找），不只是搜索
        params["tools"] = [{"type": "web_search"}]
        params["max_tool_calls"] = cfg.max_web_searches
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


def call_parse(cfg: AiConfig, raw_content: str, file_paths=None, categories=None) -> dict:
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
    structured = call_api(cfg, build_system_prompt(categories), text, file_paths, OUTPUT_SCHEMA,
                          model=cfg.parse_model, effort=cfg.parse_effort, web_search=True)
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


def call_translate(cfg: AiConfig, items: list, prompt: str = TRANSLATE_PROMPT) -> dict:
    """通过 API 翻译型号描述或联系人名称，不启用搜索工具。"""
    text = json.dumps({"items": items}, ensure_ascii=False)
    structured = call_api(cfg, prompt, text, [], TRANSLATE_SCHEMA, model=cfg.translate_model,
                          effort=cfg.translate_effort)
    if "items" not in structured:
        raise RuntimeError("AI 未返回符合 schema 的翻译结果")
    print(f"[ai-orchestrator] 翻译完成 {len(items)} 条")
    return structured


def _api_key_of(payload: dict) -> str:
    raw = payload.get("ai")
    return str(raw.get("api_key") or "") if isinstance(raw, dict) else ""


def process_translate(payload: dict, callback_url: str, prompt: str = TRANSLATE_PROMPT):
    key = _api_key_of(payload)
    try:
        cfg = parse_ai_config(payload.get("ai"))
        items = (payload.get("input") or {}).get("items") or []
        if not items:
            send_callback(callback_url, "failed", error="没有需要翻译的内容")
            return
        send_callback(callback_url, "success", output=call_translate(cfg, items, prompt))
    except ConfigError as e:
        send_callback(callback_url, "failed", error=str(e))
    except (anthropic.APITimeoutError, openai.APITimeoutError):
        send_callback(callback_url, "failed", error=f"AI 处理超过 {TIMEOUT_SECONDS} 秒未完成")
    except Exception as e:
        message = scrub(str(e), key)
        print(f"[ai-orchestrator] translate failed: {message}")
        send_callback(callback_url, "failed", error=message[:500])


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
    key = _api_key_of(payload)
    try:
        cfg = parse_ai_config(payload.get("ai"))
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
        output = call_parse(cfg, raw_content, loaded, input_data.get("categories") or [])
        send_callback(callback_url, "success", output=output)
    except ConfigError as e:
        send_callback(callback_url, "failed", error=str(e))
    except (anthropic.APITimeoutError, openai.APITimeoutError):
        send_callback(callback_url, "failed", error=f"AI 处理超过 {TIMEOUT_SECONDS} 秒未完成")
    except Exception as e:
        message = scrub(str(e), key)
        print(f"[ai-orchestrator] job failed: {message}")
        send_callback(callback_url, "failed", error=message[:500])


def _provider_error(e: Exception, key: str) -> str:
    """服务商异常转成给用户看的原因，不含 Key"""
    if isinstance(e, (anthropic.AuthenticationError, openai.AuthenticationError)):
        return "Key 无效或已失效"
    if isinstance(e, (anthropic.PermissionDeniedError, openai.PermissionDeniedError)):
        return "Key 没有访问权限"
    if isinstance(e, (anthropic.NotFoundError, openai.NotFoundError)):
        return "模型不存在或当前 Key 无权使用"
    if isinstance(e, (anthropic.APITimeoutError, openai.APITimeoutError)):
        return "连接服务商超时"
    if isinstance(e, (anthropic.APIConnectionError, openai.APIConnectionError)):
        return "无法连接服务商"
    return scrub(str(e), key)[:200]


def _retrieve_model(cfg: AiConfig, model: str):
    """查询单个模型信息（不产生调用费用）"""
    if cfg.provider == "openai":
        openai.OpenAI(api_key=cfg.api_key, timeout=QUICK_TIMEOUT_SECONDS).models.retrieve(model)
    else:
        anthropic.Anthropic(api_key=cfg.api_key, timeout=QUICK_TIMEOUT_SECONDS).models.retrieve(model)


def test_connection(raw) -> dict:
    """测试连接：API 方式查询 Key 与两个模型，订阅方式检查对应 CLI 是否登录"""
    try:
        cfg = parse_ai_config(raw, require_key=False)
    except ConfigError as e:
        return {"ok": False, "items": [{"name": "配置", "ok": False, "message": str(e)}]}
    if cfg.mode == "subscription":
        name = "Codex 登录" if cfg.provider == "openai" else "Claude 登录"
        logged_in = login_status(QUICK_TIMEOUT_SECONDS)["codex" if cfg.provider == "openai" else "claude"]
        return {"ok": logged_in, "items": [{"name": name, "ok": logged_in,
                                            "message": "已登录" if logged_in else "未登录，请在容器内执行登录命令"}]}
    if not cfg.api_key:
        return {"ok": False, "items": [{"name": "API Key", "ok": False, "message": "未填写 API Key"}]}
    items = []
    models = [("解析模型", cfg.parse_model), ("翻译模型", cfg.translate_model)]
    for i, (label, model) in enumerate(models):
        try:
            _retrieve_model(cfg, model)
            if i == 0:
                items.append({"name": "API Key", "ok": True, "message": "有效"})
            items.append({"name": f"{label} {model}", "ok": True, "message": "可用"})
        except (anthropic.AuthenticationError, openai.AuthenticationError,
                anthropic.PermissionDeniedError, openai.PermissionDeniedError) as e:
            items.append({"name": "API Key", "ok": False, "message": _provider_error(e, cfg.api_key)})
            break
        except (anthropic.APIError, openai.APIError) as e:
            if i == 0:
                items.append({"name": "API Key", "ok": True, "message": "有效"})
            items.append({"name": f"{label} {model}", "ok": False, "message": _provider_error(e, cfg.api_key)})
    return {"ok": all(item["ok"] for item in items), "items": items}


def _default_model_list(provider: str) -> list:
    defaults = DEFAULT_MODELS[provider]
    return [{"id": defaults["parse"], "default": False}, {"id": defaults["translate"], "default": False}]


def list_models(raw) -> dict:
    """模型下拉列表：API 方式按 Key 读取服务商的模型，Codex 订阅读取账号可用模型，其他情况只给默认模型"""
    try:
        cfg = parse_ai_config(raw, require_key=False)
    except ConfigError as e:
        return {"models": [], "source": "defaults", "error": str(e)}
    fallback = _default_model_list(cfg.provider)
    try:
        if cfg.mode == "subscription":
            if cfg.provider != "openai":
                return {"models": fallback, "source": "defaults", "error": None}
            return {"models": codex_models(QUICK_TIMEOUT_SECONDS), "source": "provider", "error": None}
        if not cfg.api_key:
            return {"models": fallback, "source": "defaults", "error": None}
        if cfg.provider == "openai":
            ids = [m.id for m in openai.OpenAI(api_key=cfg.api_key, timeout=QUICK_TIMEOUT_SECONDS).models.list()]
            ids = sorted(i for i in ids if not any(marker in i for marker in NON_CHAT_MARKERS))
        else:
            ids = [m.id for m in anthropic.Anthropic(api_key=cfg.api_key, timeout=QUICK_TIMEOUT_SECONDS).models.list()]
        return {"models": [{"id": i, "default": False} for i in ids], "source": "provider", "error": None}
    except (anthropic.APIError, openai.APIError) as e:
        return {"models": fallback, "source": "defaults", "error": _provider_error(e, cfg.api_key)}
    except RuntimeError as e:
        return {"models": fallback, "source": "defaults", "error": str(e)}


class Handler(BaseHTTPRequestHandler):
    def _authorized(self) -> bool:
        """所有接口校验共享令牌；不一致返回 401"""
        token = self.headers.get("X-AI-Callback-Token") or ""
        if hmac.compare_digest(token.encode("utf-8"), CALLBACK_TOKEN.encode("utf-8")):
            return True
        self._send_json(401, {"error": "unauthorized"})
        return False

    def _send_json(self, status: int, body: dict):
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def _read_json(self) -> dict:
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            return {}
        return payload if isinstance(payload, dict) else {}

    def do_GET(self):
        if not self._authorized():
            return
        if self.path != "/status":
            self._send_json(404, {"error": "not found"})
            return
        status = login_status(QUICK_TIMEOUT_SECONDS)
        self._send_json(200, {"codex": {"logged_in": status["codex"]}, "claude": {"logged_in": status["claude"]}})

    def do_POST(self):
        if not self._authorized():
            return
        if self.path in ("/test", "/models"):
            ai = self._read_json().get("ai")
            self._send_json(200, test_connection(ai) if self.path == "/test" else list_models(ai))
            return
        match = re.fullmatch(r"/skills/([^/]+)/run", self.path)
        if not match:
            self._send_json(404, {"error": "not found"})
            return
        skill_id = match.group(1)
        payload = self._read_json()

        callback_url = payload.get("callback_url")
        job_id = str(uuid.uuid4())
        print(f"[ai-orchestrator] received skill={skill_id}, will callback {callback_url} (job_id={job_id})")

        self._send_json(202, {"job_id": job_id})

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

    # 部署项写错时直接退出，容器反复重启，docker ps 和日志能马上看到原因；
    # 服务商、Key、模型等业务配置随任务传入，在「业务设置 → AI 设置」中维护
    if TIMEOUT_SECONDS < 1:
        print("[ai-orchestrator] 配置错误：AI_ORCHESTRATOR_TIMEOUT_SECONDS 必须为正整数", file=sys.stderr)
        sys.exit(1)

    # 测试连接、订阅状态是同步请求，用多线程服务避免阻塞任务提交
    server = ThreadingHTTPServer(("0.0.0.0", PORT), Handler)
    print(f"[ai-orchestrator] listening on :{PORT}（服务商、Key、模型由每个任务携带的租户配置决定）")
    if not os.environ.get("AI_TASK_CALLBACK_TOKEN"):
        print("[ai-orchestrator] 警告：未配置 AI_TASK_CALLBACK_TOKEN，正在使用默认令牌，生产环境必须设置")
    server.serve_forever()
