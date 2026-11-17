#!/usr/bin/env python3
"""
真正调用 AI 的编排服务实现——支持 API 及部署者个人订阅（官方 Codex SDK / Claude Code CLI）。
API 通过 Claude（Anthropic API）或 OpenAI（Responses API）的官方 Python SDK
执行本目录 skills/ 下的 inquiry-parser + order-splitter 两个 skill，替代
scripts/ai-orchestrator-stub 里那个返回固定假数据的占位脚本。

服务商、Key、模型、思考强度、搜索上限都由后端随每个任务传入（请求体 ai 字段，来自租户在
「业务设置 → AI 设置」中维护的配置，见 openspec ai-platform/tenant-ai-settings），本服务不读取这些环境变量。
所有接口校验请求头 X-AI-Callback-Token 与 AI_TASK_CALLBACK_TOKEN 一致。

对应 openspec/changes/archive/2026-10-03-add-inquiry-management/design.md 决策3的通用契约：
  POST /skills/{skill_id}/run
    body: {"input": {...}, "callback_url": "http://.../api/v1/ai-tasks/{id}/callback", "ai": {...}}
    -> 202 {"job_id": "..."}
  处理完成后（可能是几十秒到几分钟）:
  POST {callback_url}
    header: X-AI-Callback-Token: <与 zhul-erp-backend 配置一致的共享密钥>
    body: {"status": "success"|"failed", "output": {...}|null, "error": null|string}

另有同步接口：POST /test（测试连接）、POST /models（模型下拉列表）、GET /status（订阅登录状态）。

API 方式用任务携带的 Key（只在本次请求的内存中使用，日志与回调不出现）。订阅方式供平台租户与获平台授权的公司（授权由后端在创建任务前检查），
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
import dataclasses
import hmac
import json
import os
import re
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from datetime import date
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import anthropic
import jsonschema
import openai

import large_parse as lp
from subscription import SearchLimitError, SubscriptionCallError, call_subscription, codex_models, login_status

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
# 大附件分批解析时整个任务的期限；单次模型调用仍按 TIMEOUT_SECONDS（OpenSpec inquiry-large-attachments D11）
MAX_SECONDS = int(os.environ.get("AI_ORCHESTRATOR_MAX_SECONDS") or "10200")
QUICK_TIMEOUT_SECONDS = 20
# pause_turn 最多续传几次，防止异常情况下无限循环
MAX_CONTINUATIONS = 5
# 被拒绝的请求最多读这么多请求体再回应；更大的不读，直接关闭连接
MAX_REJECT_DRAIN_BYTES = 1024 * 1024
# 模型请求遇到可恢复的错误（连不上、限流、服务商 5xx）时的重试间隔；SDK 自带的重试不知道整任务期限，已关掉
API_RETRY_DELAYS = (2, 5)
# 结果回调投递失败（连不上、超时、后端 5xx）时的重投间隔，合计约 1 分钟（audit AI-002）
CALLBACK_RETRY_DELAYS = (5, 15, 45)
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
                                "orderCode": {"type": "string"},
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
                                "nameplates": {"type": "array", "items": {"type": "integer"}},
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


# 一个任务在一个线程里跑完：用线程变量累加这次任务内所有 API 调用的用量，回调时带回（不含 Key 与请求内容）
_usage = threading.local()


class JobTimeout(Exception):
    """整个任务超过 AI_ORCHESTRATOR_TIMEOUT_SECONDS（audit AI-005）"""


_JOB = threading.local()


def start_job(seconds=None):
    """任务开始计时：下载附件、排队、每轮模型请求和续传共用这一个期限，
    保证编排服务在后端超时扫描（默认 20 分钟）判失败之前就停下（audit AI-005）。
    大附件分批解析改用 MAX_SECONDS，期间定期报告进度，后端按最近一次进度计时"""
    _JOB.deadline = time.monotonic() + (seconds or TIMEOUT_SECONDS)


def _call_deadline_propagator(job_deadline):
    """给工作线程设置本次调用的期限：不超过整个任务的期限，单次调用最长 TIMEOUT_SECONDS"""
    def propagate():
        _JOB.deadline = min(job_deadline, time.monotonic() + TIMEOUT_SECONDS)
        _usage.value = None
    return propagate


def remaining() -> float:
    """本任务还剩多少秒；已到期时抛 JobTimeout，不再发起下一步请求"""
    deadline = getattr(_JOB, "deadline", None)
    if deadline is None:
        return float(TIMEOUT_SECONDS)
    left = deadline - time.monotonic()
    if left <= 0:
        raise JobTimeout()
    return left


def _retryable(e: Exception) -> bool:
    """连不上、限流、服务商 5xx 可以重试；超时、鉴权、参数错误不重试"""
    if isinstance(e, (anthropic.APITimeoutError, openai.APITimeoutError)):
        return False
    if isinstance(e, (anthropic.APIConnectionError, openai.APIConnectionError)):
        return True
    if isinstance(e, (anthropic.APIStatusError, openai.APIStatusError)):
        return e.status_code in (408, 409, 429) or e.status_code >= 500
    return False


def _with_retries(request):
    """request(timeout) 发起一次模型请求；在本任务剩余时间内重试可恢复的错误"""
    for delay in API_RETRY_DELAYS + (None,):
        try:
            return request(remaining())
        except (anthropic.APIError, openai.APIError) as e:
            if delay is None or not _retryable(e) or remaining() <= delay:
                raise
            print(f"[ai-orchestrator] 模型请求失败，{delay} 秒后重试：{type(e).__name__}")
            time.sleep(delay)


def _record_usage(model: str, input_tokens, output_tokens, searches):
    u = getattr(_usage, "value", None) or {"model": "", "inputTokens": 0, "outputTokens": 0, "webSearches": 0}
    u["model"] = model or u["model"]
    u["inputTokens"] += int(input_tokens or 0)
    u["outputTokens"] += int(output_tokens or 0)
    u["webSearches"] += int(searches or 0)
    _usage.value = u


def _take_usage():
    """取出并清空当前线程累计的用量；订阅方式拿不到用量时为 None"""
    u = getattr(_usage, "value", None)
    _usage.value = None
    return u


def mask_key(key: str) -> str:
    """脱敏：保留前 7 位与后 4 位，中间固定 16 个点；不足 12 位时整串换成同样长度的点，与后端一致"""
    if not key:
        return ""
    return "•" * 27 if len(key) < 12 else f"{key[:7]}{'•' * 16}{key[-4:]}"


def scrub(text: str, keys) -> str:
    """错误信息里如果带了 Key（一个或多个），换成脱敏值"""
    for key in ([keys] if isinstance(keys, str) else keys or []):
        text = text.replace(key, mask_key(key)) if key and text else text
    return text


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


# ---------------------------------------------------------------- 连接方式自动切换（openspec ai-connection-failover）

#: 单次调用超时时，任务剩余时间至少这么多才换下一个方式，否则换了也来不及
FAILOVER_MIN_REMAINING = 120
#: 切换原因分类码 → 给人看的说明（不含服务商返回的原文，避免带出 Key 或账号信息）
FAILOVER_REASONS = {"auth": "认证失败", "quota": "额度用完", "rate_limit": "请求过多", "provider_error": "服务商故障",
                    "timeout": "调用超时", "not_logged_in": "订阅未登录", "usage_limit": "达到用量上限"}


class AllConnectionsFailed(RuntimeError):
    """列表中每个连接方式都因连接方式本身的问题失败"""


def connection_name(cfg: AiConfig) -> str:
    return f"{cfg.mode}:{cfg.provider}"


def _connection_label(name: str) -> str:
    mode, _, provider = name.partition(":")
    return f"{'Claude' if provider == 'claude' else 'OpenAI'} {'API Key' if mode == 'api' else '平台订阅'}"


class AiChain:
    """任务携带的有序连接方式：依次使用，失败的方式在本任务内不再尝试（并行批次共用，加锁）；
    记录切换（实时报告后端）与最后成功的方式（结果回调带回）"""

    def __init__(self, configs, callback_url=None):
        self.configs = list(configs)
        self.callback_url = callback_url
        self.failovers = []
        self.used = None
        self._failed = set()
        self._lock = threading.Lock()

    def candidates(self):
        with self._lock:
            return [c for c in self.configs if connection_name(c) not in self._failed]

    def fail(self, cfg: AiConfig, reason: str):
        name = connection_name(cfg)
        with self._lock:
            if name in self._failed:
                return
            self._failed.add(name)
            event = {"connection": name, "reason": reason, "at": int(time.time())}
            self.failovers.append(event)
        if self.callback_url:
            send_failover(self.callback_url, event)

    def succeed(self, cfg: AiConfig):
        with self._lock:
            self.used = connection_name(cfg)

    def summary(self) -> str:
        with self._lock:
            parts = [f"{_connection_label(f['connection'])}{FAILOVER_REASONS.get(f['reason'], '不可用')}"
                     for f in self.failovers]
        return "AI 连接方式都不可用：" + ("、".join(parts) if parts else "没有可用的连接方式")


_CHAINS = {}
_CHAINS_LOCK = threading.Lock()


def parse_chain(payload: dict, callback_url: str = None) -> AiChain:
    """任务携带的连接方式列表：新版后端放在 connections（有序），旧版只有 ai 一项"""
    raws = payload.get("connections")
    if not isinstance(raws, list) or not raws:
        raws = [payload.get("ai")]
    chain = AiChain([parse_ai_config(r) for r in raws], callback_url)
    if callback_url:
        with _CHAINS_LOCK:
            _CHAINS[callback_url] = chain
    return chain


def _keys_of(cfg) -> list:
    """一个连接方式或整个列表里的 Key，用于日志脱敏"""
    return [c.api_key for c in (cfg.configs if isinstance(cfg, AiChain) else [cfg]) if c.api_key]


def _forget_chain(callback_url: str):
    with _CHAINS_LOCK:
        return _CHAINS.pop(callback_url, None)


def _status_code(e: Exception):
    return getattr(e, "status_code", None)


def _error_text(e: Exception) -> str:
    body = getattr(e, "body", None)
    return (str(body) if body else "") + " " + str(e)


def failover_reason(e: Exception):
    """失败是不是连接方式本身的问题：是则返回原因分类码（换下一个方式），否则 None（按原规则处理，不切换）。
    _with_retries 已在同一方式上重试过连接错误、限流与 5xx，这里拿到的是重试后仍失败的结果"""
    if isinstance(e, (JobTimeout, SearchLimitError, ConfigError, AllConnectionsFailed)):
        return None
    if isinstance(e, (anthropic.APITimeoutError, openai.APITimeoutError)):
        return "timeout" if remaining() >= FAILOVER_MIN_REMAINING else None
    if isinstance(e, (anthropic.APIConnectionError, openai.APIConnectionError)):
        return "provider_error"
    if isinstance(e, (anthropic.APIStatusError, openai.APIStatusError)):
        code = _status_code(e)
        text = _error_text(e).lower()
        if code in (401, 403):
            return "auth"
        if code == 402 or "insufficient_quota" in text or "credit balance" in text or "billing" in text:
            return "quota"
        if code == 429:
            return "rate_limit"
        if code in (408, 409) or (code is not None and code >= 500):
            return "provider_error"
        return None
    if isinstance(e, SubscriptionCallError):
        if e.code == "auth":
            return "not_logged_in"
        if e.code == "timeout":
            return "timeout" if remaining() >= FAILOVER_MIN_REMAINING else None
        if e.code == "runtime":
            return "usage_limit"
        return None
    return None


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
- order_code -> orderCode（订货号、物料号、料号，辅助识别；没有时为空字符串；多个用英文逗号分隔。型号与订货号的区分按 inquiry-parser「型号与订货号」执行：confirmedModel 只放型号，西门子这类订货号即型号的品牌除外）
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


def _read_attachment(ref: dict) -> tuple:
    """附件内容与扩展名：ref 为后端签发的临时链接 {"url", "ext"}。只接受 http(s)，不读本地文件（audit AI-003）。
    与大附件一样流式下载：每次读取受整任务期限约束，单个不超过 500MB（audit AI-005）"""
    with tempfile.TemporaryDirectory(prefix="zhul-read-") as work:
        path = lp.download(ref, work, remaining)
        with open(path, "rb") as fh:
            return fh.read(), "." + ref.get("ext", "").lower().lstrip(".")


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


def call_api(chain, system_prompt: str, text: str, attachments: list, schema: dict, *,
             role: str, web_search: bool = False, web_fetch: bool = False, min_web_calls: int = 0) -> dict:
    """按任务携带的连接方式列表依次调用（openspec ai-connection-failover「执行中自动切换」）：
    当前方式因连接方式本身的问题失败时，只重做这一次调用、换下一个方式，并记下切换；内容本身的错误原样抛出。
    role 为 parse / translate，每个方式用自己的模型与思考强度。
    web_fetch：联网时还允许打开网页（Claude API 加网页抓取工具；OpenAI 与订阅的联网本来就能打开网页）；
    min_web_calls：联网次数至少这么多（任务本身需要大量核实时用，公司设置更高时按公司设置）"""
    if isinstance(chain, AiConfig):
        chain = AiChain([chain])
    last_error = None
    for cfg in chain.candidates():
        model, effort = ((cfg.parse_model, cfg.parse_effort) if role == "parse"
                         else (cfg.translate_model, cfg.translate_effort))
        try:
            if min_web_calls > cfg.max_web_searches:
                cfg = dataclasses.replace(cfg, max_web_searches=min_web_calls)
            result = _call_once(cfg, system_prompt, text, attachments, schema, model=model, effort=effort,
                                web_search=web_search, web_fetch=web_fetch)
        except Exception as e:  # noqa: BLE001 只有归为「换方式」的才接住，其余原样抛出
            reason = failover_reason(e)
            if reason is None:
                raise
            print(f"[ai-orchestrator] 连接方式 {connection_name(cfg)} 失败（{reason}），改用下一个")
            chain.fail(cfg, reason)
            last_error = e
            continue
        chain.succeed(cfg)
        return result
    if last_error is not None and len(chain.configs) == 1:
        raise last_error  # 只有一个方式时没有可换的，保留原有的失败说明（如超时）
    raise AllConnectionsFailed(chain.summary())


def _call_once(cfg: AiConfig, system_prompt: str, text: str, attachments: list, schema: dict, *,
               model: str, effort: str, web_search: bool = False, web_fetch: bool = False) -> dict:
    """用一个连接方式调用一次，返回符合 schema 的 JSON"""
    if cfg.mode == "subscription":
        wire_schema = _required_nullable_schema(schema) if cfg.provider == "openai" else schema
        result = call_subscription(cfg.provider, system_prompt, text, attachments, wire_schema, model=model,
                                   effort=effort, web_search=web_search, max_web_searches=cfg.max_web_searches,
                                   timeout=max(1, int(remaining())))
        return _drop_nulls(result)
    if cfg.provider == "openai":
        return _call_openai(cfg, system_prompt, text, attachments, schema, model, effort, web_search)
    return _call_claude(cfg, system_prompt, text, attachments, schema, model, effort, web_search, web_fetch)


def _call_claude(cfg, system_prompt, text, attachments, schema, model, effort, web_search, web_fetch=False) -> dict:
    """调用 Anthropic API；pause_turn 时原样续传"""
    client = anthropic.Anthropic(api_key=cfg.api_key, timeout=TIMEOUT_SECONDS, max_retries=0)
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
        if web_fetch:
            # 打开搜索结果里的网页核实（只能打开对话中出现过的链接）；次数与搜索共用公司设置的上限
            params["tools"].append({"type": "web_fetch_20260209", "name": "web_fetch", "max_uses": cfg.max_web_searches})
    def one_round(timeout):
        # 流式输出时只要一直有数据就不会触发单请求超时，所以每收到一段都核对整任务期限
        with client.beta.messages.stream(messages=messages, timeout=timeout, **params) as stream:
            for _ in stream:
                remaining()
            return stream.get_final_message(), stream.request_id

    for _ in range(MAX_CONTINUATIONS + 1):
        response, request_id = _with_retries(one_round)
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
          f"输出 {usage.output_tokens} tokens，联网搜索 {searches} 次，request_id={request_id}")
    _record_usage(response.model, usage.input_tokens, usage.output_tokens, searches)
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
                    if "enum" in prop:
                        # enum 是独立约束，不加 null 时模型只能在枚举值里硬选一个（AI-012）
                        prop["enum"] = [*prop["enum"], None]
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
    client = openai.OpenAI(api_key=cfg.api_key, timeout=TIMEOUT_SECONDS, max_retries=0)
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
    response = _with_retries(lambda timeout: client.responses.create(timeout=timeout, **params))

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
    _record_usage(response.model, usage.input_tokens, usage.output_tokens, searches)
    return _drop_nulls(json.loads(response.output_text))


def _nameplate_hint(nos: list) -> str:
    """说明哪几个附件是铭牌图片，让模型在每个型号的 nameplates 里填对应的编号"""
    marks = [f"第 {i + 1} 个附件是铭牌图片 #{n}" for i, n in enumerate(nos) if n]
    if not marks:
        return ""
    return ("\n\n附件按上面出现的顺序计数，" + "；".join(marks) + "。"
            "铭牌图片是客户拍的型号铭牌，请从图上识别型号，并在对应型号的 nameplates 里填写它的编号（#n 的 n）。"
            "一张铭牌图片只填给一个型号；看不清或对不上任何型号的不填；不是铭牌图片的附件不要填。")


def call_parse(cfg: AiConfig, raw_content: str, file_paths=None, categories=None, nameplate_nos=None,
               web_search: bool = True) -> dict:
    """raw_content 为正文（Excel/CSV 附件已由后端抽成文本拼在里面）；file_paths 为图片、PDF 附件（已读好的 (内容, 扩展名)，或临时链接、本地路径）；
    categories 为后端带来的品类清单（没有时沿用 skill 里的品类枚举）；nameplate_nos 与 file_paths 一一对应，
    铭牌图片为编号（从 1 开始），其他附件为 None。"""
    file_paths = file_paths or []
    if file_paths:
        text = (
            (f"询盘正文：\n{raw_content}\n\n" if raw_content else "这条询盘没有正文。\n")
            + "上面附有询盘附件（图片或 PDF）。请先识别附件中的型号、数量等询盘信息，与正文合并当成询盘原文，再按 skill 流程继续处理。"
            + _nameplate_hint(nameplate_nos or [])
        )
    else:
        text = raw_content
    structured = call_api(cfg, build_system_prompt(categories), text, file_paths, OUTPUT_SCHEMA,
                          role="parse", web_search=web_search)
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


COMPANY_SKILL = "translate-company-names"

COMPANY_PROMPT = """你是外贸公司的翻译。把下面每一条客户公司英文名称翻译成中文公司名：
- 知名品牌和公司用通行中文名（如 Siemens 写西门子）；品牌、缩写、型号类字母（如 ABC、HTF）原样保留
- 其余部分意译或音译，公司后缀按惯例译：Limited / Ltd / LLC / GmbH / Pty Ltd 写有限公司，Co. 写公司，Inc. / Corp. 写公司或股份有限公司，Trading 写贸易，Industrial 写工业
- 不要加国家或城市前缀，原文没有的信息不要补
- 只输出中文名称，不加解释，每条不超过 60 个字符
- key 原样返回，每条输入都要有对应输出
只输出 JSON。"""


def call_translate(cfg: AiConfig, items: list, prompt: str = TRANSLATE_PROMPT) -> dict:
    """通过 API 翻译型号描述或联系人名称，不启用搜索工具。"""
    text = json.dumps({"items": items}, ensure_ascii=False)
    structured = call_api(cfg, prompt, text, [], TRANSLATE_SCHEMA, role="translate")
    if "items" not in structured:
        raise RuntimeError("AI 未返回符合 schema 的翻译结果")
    print(f"[ai-orchestrator] 翻译完成 {len(items)} 条")
    return structured


def _api_key_of(payload: dict) -> list:
    """任务携带的全部 Key（ai 与 connections），错误信息里出现时脱敏"""
    raws = [payload.get("ai")] + (payload.get("connections") if isinstance(payload.get("connections"), list) else [])
    return [str(r.get("api_key")) for r in raws if isinstance(r, dict) and r.get("api_key")]


def process_translate(payload: dict, callback_url: str, prompt: str = TRANSLATE_PROMPT):
    start_job()
    key = _api_key_of(payload)
    try:
        cfg = parse_chain(payload, callback_url)
        items = (payload.get("input") or {}).get("items") or []
        if not items:
            send_callback(callback_url, "failed", error="没有需要翻译的内容")
            return
        send_callback(callback_url, "success", output=call_translate(cfg, items, prompt))
    except ConfigError as e:
        send_callback(callback_url, "failed", error=str(e))
    except (JobTimeout, anthropic.APITimeoutError, openai.APITimeoutError):
        send_callback(callback_url, "failed", error=f"AI 处理超过 {TIMEOUT_SECONDS} 秒未完成")
    except Exception as e:
        message = scrub(str(e), key)
        print(f"[ai-orchestrator] translate failed: {message}")
        send_callback(callback_url, "failed", error=message[:500])


CANDIDATE_SKILL = "candidate-classify"

# 一次调用的条数：品类清单较长，分批调用控制单次输入和输出长度
CANDIDATE_BATCH = 50

CANDIDATE_SCHEMA = {
    "type": "object",
    "properties": {
        "items": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {"key": {"type": "string"}, "categoryId": {"type": "integer"}, "brand": {"type": "string"}},
                "required": ["key", "categoryId", "brand"],
            },
        }
    },
    "required": ["items"],
}

CANDIDATE_CATEGORY_PROMPT = """你是工控自动化产品的商品运营。根据每个商品候选的品牌、型号、名称、描述和来源品类文字，判断它属于下面品类清单里的哪个品类：
- 只能从清单里选，categoryId 填清单里的 ID；优先选细分品类，判断不出细分时选一级品类
- 实在判断不出时 categoryId 填 0，不要猜
- brand 一律填空字符串
- key 原样返回，每条输入都要有对应输出
只输出 JSON。

品类清单（ID：一级品类 / 细分品类）：
{categories}"""

CANDIDATE_BRAND_PROMPT = """你是工控自动化产品的商品运营。根据每个商品候选的品牌原文、型号、名称和描述，判断它的品牌（制造商）：
- brand 填品牌的通用英文名，如 Siemens、Mitsubishi、Omron、Allen-Bradley、Schneider Electric；不要带公司后缀（Co.、Ltd.、GmbH 等）
- 品牌原文是缩写、别称或拼写有误的，改成通用名称；原文为空的，按型号规则判断
- 实在判断不出时 brand 填空字符串，不要猜
- categoryId 一律填 0
- key 原样返回，每条输入都要有对应输出
只输出 JSON。"""


def _candidate_prompt(input_data: dict) -> str:
    if input_data.get("mode") == "brand":
        return CANDIDATE_BRAND_PROMPT
    lines = [f"{c.get('id')}：{c.get('name')}" for c in input_data.get("categories") or []]
    return CANDIDATE_CATEGORY_PROMPT.replace("{categories}", "\n".join(lines))


def call_candidate_classify(cfg: AiConfig, input_data: dict) -> dict:
    """按 50 条一批判断商品候选的品类或品牌，合并后一次返回；不联网搜索。"""
    items = input_data.get("items") or []
    prompt = _candidate_prompt(input_data)
    results = []
    for i in range(0, len(items), CANDIDATE_BATCH):
        batch = items[i:i + CANDIDATE_BATCH]
        structured = call_api(cfg, prompt, json.dumps({"items": batch}, ensure_ascii=False), [], CANDIDATE_SCHEMA,
                              role="parse")
        if "items" not in structured:
            raise RuntimeError("AI 未返回符合 schema 的结果")
        results.extend(structured["items"])
    print(f"[ai-orchestrator] 商品候选{('品牌' if input_data.get('mode') == 'brand' else '品类')}解析完成 {len(items)} 条")
    return {"items": results}


def process_candidate(payload: dict, callback_url: str):
    start_job()
    key = _api_key_of(payload)
    try:
        cfg = parse_chain(payload, callback_url)
        input_data = payload.get("input") or {}
        if not input_data.get("items"):
            send_callback(callback_url, "failed", error="没有需要解析的候选")
            return
        send_callback(callback_url, "success", output=call_candidate_classify(cfg, input_data))
    except ConfigError as e:
        send_callback(callback_url, "failed", error=str(e))
    except (JobTimeout, anthropic.APITimeoutError, openai.APITimeoutError):
        send_callback(callback_url, "failed", error=f"AI 处理超过 {TIMEOUT_SECONDS} 秒未完成")
    except Exception as e:
        message = scrub(str(e), key)
        print(f"[ai-orchestrator] candidate-classify failed: {message}")
        send_callback(callback_url, "failed", error=message[:500])


REFINE_SKILL = "product-category-refine"

REFINE_PROMPT = """你是工控自动化产品的商品运营。下面每个商品已经建档，但品类只到一级品类或在兜底「其他」里。
根据品牌、型号、商品名称、描述和当前品类，判断它属于下面细分品类清单里的哪一个：
- 只能从清单里选，categoryId 填清单里的 ID；清单只有细分品类
- 当前品类只作参考：型号明显属于别的一级品类时，按型号判断，不要迁就当前品类
- 实在判断不出、或清单里没有合适的细分品类时 categoryId 填 0，不要猜
- brand 一律填空字符串
- key 原样返回，每条输入都要有对应输出
只输出 JSON。

细分品类清单（ID：一级品类 / 细分品类）：
{categories}"""


def call_category_refine(cfg: AiConfig, input_data: dict) -> dict:
    """已建档商品细分到二级品类：复用商品候选的 50 条一批调用，只换提示词；不联网搜索。"""
    items = input_data.get("items") or []
    lines = [f"{c.get('id')}：{c.get('name')}" for c in input_data.get("categories") or []]
    prompt = REFINE_PROMPT.replace("{categories}", "\n".join(lines))
    results = []
    for i in range(0, len(items), CANDIDATE_BATCH):
        batch = items[i:i + CANDIDATE_BATCH]
        structured = call_api(cfg, prompt, json.dumps({"items": batch}, ensure_ascii=False), [], CANDIDATE_SCHEMA,
                              role="parse")
        if "items" not in structured:
            raise RuntimeError("AI 未返回符合 schema 的结果")
        results.extend(structured["items"])
    print(f"[ai-orchestrator] 商品品类细分完成 {len(items)} 条")
    return {"items": results}


def process_refine(payload: dict, callback_url: str):
    start_job()
    key = _api_key_of(payload)
    try:
        cfg = parse_chain(payload, callback_url)
        input_data = payload.get("input") or {}
        if not input_data.get("items"):
            send_callback(callback_url, "failed", error="没有需要细分的商品")
            return
        send_callback(callback_url, "success", output=call_category_refine(cfg, input_data))
    except ConfigError as e:
        send_callback(callback_url, "failed", error=str(e))
    except (JobTimeout, anthropic.APITimeoutError, openai.APITimeoutError):
        send_callback(callback_url, "failed", error=f"AI 处理超过 {TIMEOUT_SECONDS} 秒未完成")
    except Exception as e:
        message = scrub(str(e), key)
        print(f"[ai-orchestrator] product-category-refine failed: {message}")
        send_callback(callback_url, "failed", error=message[:500])


BACKGROUND_SKILL = "customer-background"

BACKGROUND_SCHEMA = {
    "type": "object",
    "properties": {
        "conclusion": {
            "type": "object",
            "properties": {
                "priority": {"type": "integer"},
                "score": {"type": "integer"},
                "mainConflict": {"type": "string"},
                "firstAction": {"type": "string"},
                "identity": {"type": "string"},
                "supply": {"type": "string"},
                "keyFinding": {"type": "string"},
                "obstacle": {"type": "string"},
            },
            "required": ["priority", "score", "mainConflict", "firstAction", "identity", "supply", "keyFinding",
                         "obstacle"],
        },
        "reportMarkdown": {"type": "string"},
        "suggestions": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "module": {"type": "integer"},
                    "field": {"type": "string"},
                    "value": {"type": "string"},
                    "evidence": {"type": "string"},
                    "sourceUrl": {"type": "string"},
                    "contact": {
                        "type": "object",
                        "properties": {
                            "name": {"type": "string"},
                            "email": {"type": "string"},
                            "title": {"type": "string"},
                        },
                        "required": ["name", "email", "title"],
                    },
                },
                "required": ["module", "field", "value", "evidence"],
            },
        },
    },
    "required": ["conclusion", "reportMarkdown", "suggestions"],
}

# 上传报告文字的长度上限，与后端一致
MAX_REPORT_CHARS = 60000


def build_background_text(input_data: dict) -> str:
    """把后端组装的输入写成给模型看的文字：模式、允许的字段，以及客户资料或上传的报告"""
    mode = input_data.get("mode")
    head = (f"mode={mode}\n\n允许的字段（fields）：\n"
            + json.dumps(input_data.get("fields") or [], ensure_ascii=False) + "\n\n")
    if mode == "extract":
        return head + "上传的背调报告文字如下（不要联网，只从中提取）：\n\n" + (input_data.get("reportText") or "")
    return (head + "系统里这个客户的资料：\n" + json.dumps(input_data.get("customer") or {}, ensure_ascii=False, indent=1)
            + "\n\n最近的询盘原文：\n" + json.dumps(input_data.get("inquiries") or [], ensure_ascii=False, indent=1))


def call_background(cfg: AiConfig, input_data: dict) -> dict:
    """research 联网背调；extract 只从上传的报告提取，不联网。用询盘解析的模型与思考强度"""
    research = input_data.get("mode") == "research"
    structured = call_api(cfg, _read_skill(BACKGROUND_SKILL), build_background_text(input_data), [],
                          BACKGROUND_SCHEMA, role="parse", web_search=research)
    if "conclusion" not in structured:
        raise RuntimeError("AI 未返回符合 schema 的背调结果")
    structured.setdefault("suggestions", [])
    if not research:
        structured["reportMarkdown"] = ""
    return structured


def process_background(payload: dict, callback_url: str):
    start_job()
    key = _api_key_of(payload)
    try:
        cfg = parse_chain(payload, callback_url)
        input_data = payload.get("input") or {}
        mode = input_data.get("mode")
        if mode not in ("research", "extract"):
            send_callback(callback_url, "failed", error=f"背调模式 {mode} 不支持")
            return
        if mode == "extract" and not (input_data.get("reportText") or "").strip():
            send_callback(callback_url, "failed", error="上传的报告没有文字")
            return
        if mode == "extract" and len(input_data["reportText"]) > MAX_REPORT_CHARS:
            input_data = {**input_data, "reportText": input_data["reportText"][:MAX_REPORT_CHARS]}
        send_callback(callback_url, "success", output=call_background(cfg, input_data))
    except ConfigError as e:
        send_callback(callback_url, "failed", error=str(e))
    except (JobTimeout, anthropic.APITimeoutError, openai.APITimeoutError):
        send_callback(callback_url, "failed", error=f"AI 处理超过 {TIMEOUT_SECONDS} 秒未完成")
    except Exception as e:
        message = scrub(str(e), key)
        print(f"[ai-orchestrator] background failed: {message}")
        send_callback(callback_url, "failed", error=message[:500])


PRODUCT_CONTENT_SKILL = "product-content"
# 一个商品的资料与营销内容要核实多个网页，联网次数至少这么多（公司设置更高时按公司设置）
PRODUCT_CONTENT_MIN_WEB_CALLS = int(os.environ.get("AI_PRODUCT_CONTENT_MIN_WEB_CALLS") or "40")
# 整个任务的期限（秒）：生成期间每隔 PRODUCT_CONTENT_HEARTBEAT 秒报告一次进度，后端按最近一次进度计时
PRODUCT_CONTENT_SECONDS = int(os.environ.get("AI_PRODUCT_CONTENT_SECONDS") or "1800")
PRODUCT_CONTENT_HEARTBEAT = 240
PRODUCT_CONTENT_SCHEMA = {
    "type": "object",
    "properties": {
        "factsMarkdown": {"type": "string"},
        "marketingMarkdown": {"type": "string"},
        "selfScore": {"type": "integer"},
        "summary": {"type": "string"},
    },
    "required": ["factsMarkdown", "marketingMarkdown", "selfScore", "summary"],
}
# 两份任务包合计的长度上限（字符），与后端一致
PRODUCT_CONTENT_MAX_INPUT = 200000


def product_content_prompt() -> str:
    """系统版运行说明 + 原文技能 + 原文输出格式（skills/product-content 下固定的一份）"""
    base = os.path.join(SKILL_DIR, PRODUCT_CONTENT_SKILL)
    with open(os.path.join(base, "upstream", "SKILL.md"), encoding="utf-8") as f:
        upstream = f.read()
    with open(os.path.join(base, "references", "output-format.md"), encoding="utf-8") as f:
        output_format = f.read()
    return (_read_skill(PRODUCT_CONTENT_SKILL).replace("{{UPSTREAM_SKILL}}", upstream)
            .replace("{{OUTPUT_FORMAT}}", output_format))


def build_product_content_text(input_data: dict) -> str:
    return ("下面是这个商品的两份任务包。请按运行约定核实后，输出完整的资料包与营销包。\n\n"
            "===== 资料包任务包（" + (input_data.get("factsFileName") or "facts.md") + "）=====\n"
            + (input_data.get("factsPack") or "") + "\n\n"
            "===== 营销包任务包（" + (input_data.get("marketingFileName") or "marketing.md") + "）=====\n"
            + (input_data.get("marketingPack") or ""))


def call_product_content(cfg, input_data: dict) -> dict:
    structured = call_api(cfg, product_content_prompt(), build_product_content_text(input_data), [],
                          PRODUCT_CONTENT_SCHEMA, role="parse", web_search=True, web_fetch=True,
                          min_web_calls=PRODUCT_CONTENT_MIN_WEB_CALLS)
    if not (structured.get("factsMarkdown") or "").strip() or not (structured.get("marketingMarkdown") or "").strip():
        raise RuntimeError("AI 没有输出完整的资料包和营销包")
    return structured


def process_product_content(payload: dict, callback_url: str):
    """商品资料与营销内容（openspec product-content-ai）：一个商品一个任务，耗时较长，期间定期报告进度"""
    start_job(PRODUCT_CONTENT_SECONDS)
    key = _api_key_of(payload)
    stop = threading.Event()

    def heartbeat():
        while not stop.wait(PRODUCT_CONTENT_HEARTBEAT):
            send_progress(callback_url, 0, 0, None)

    threading.Thread(target=heartbeat, daemon=True).start()
    try:
        cfg = parse_chain(payload, callback_url)
        input_data = payload.get("input") or {}
        if not (input_data.get("factsPack") or "").strip() or not (input_data.get("marketingPack") or "").strip():
            send_callback(callback_url, "failed", error="缺少资料包或营销包任务包")
            return
        if len(input_data["factsPack"]) + len(input_data["marketingPack"]) > PRODUCT_CONTENT_MAX_INPUT:
            send_callback(callback_url, "failed", error="任务包内容过长")
            return
        send_callback(callback_url, "success", output=call_product_content(cfg, input_data))
    except ConfigError as e:
        send_callback(callback_url, "failed", error=str(e))
    except (JobTimeout, anthropic.APITimeoutError, openai.APITimeoutError):
        send_callback(callback_url, "failed", error=f"AI 处理超过 {PRODUCT_CONTENT_SECONDS // 60} 分钟未完成")
    except Exception as e:
        message = scrub(str(e), key)
        print(f"[ai-orchestrator] product-content failed: {message}")
        send_callback(callback_url, "failed", error=message[:500])
    finally:
        stop.set()


FOLLOWUP_EXTRACT_SKILL = "followup-extract"
FOLLOWUP_REPLY_SKILL = "followup-reply"
# 粘贴原文只取最后这么多字交给 AI（与后端 FollowupConstants.RAW_MAX 一致）
FOLLOWUP_RAW_MAX = 20000
FOLLOWUP_SITUATIONS = ("feedback", "negotiating", "no_reply")
# 客户经营（board 5）的客户情况是单选（第三期 design D10），按这个先后只留一个；其他板块丢掉这三个值
FOLLOWUP_ACCOUNT_SITUATIONS = ("new_need", "cross_sell", "no_need")
FOLLOWUP_ACCOUNT_BOARD = 5
# 主动开发（board 1）的触达结果是单选（第四期 design D11）：回复了、采购信号、明确拒绝，另可只是没回复；采购信号另输出 signalText
FOLLOWUP_DEVELOP_SITUATIONS = ("buying_signal", "refused", "replied")
FOLLOWUP_DEVELOP_BOARD = 1
FOLLOWUP_SIGNAL_MAX = 200
# 订单履约、售后评价阶段的答案建议（第二期 design D13）：只在对应阶段输出，取值与后端 FollowupConstants 一致
FOLLOWUP_ISSUE_TYPES = ("少件", "破损", "型号不对", "其他")
FOLLOWUP_REVIEW_STATUSES = (1, 2, 3, 4, 6)
FOLLOWUP_STAGE_FIELDS = {
    "F6": ("etaDate",),
    "A1": ("receiptAnswer", "issueTypes", "etaDate"),
    "A2": ("useAnswer", "issueTypes"),
    "A5": ("satisfaction", "reviewStatus"),
    "A6": ("reviewStatus",),
}

FOLLOWUP_EXTRACT_SCHEMA = {
    "type": "object",
    "properties": {
        "summary": {"type": "string"},
        "situation": {"type": "array", "items": {"type": "string",
                                                  "enum": list(FOLLOWUP_SITUATIONS + FOLLOWUP_ACCOUNT_SITUATIONS
                                                               + FOLLOWUP_DEVELOP_SITUATIONS)}},
        "signalText": {"type": "string"},
        "blocker": {"type": "string"},
        "nextActions": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {"text": {"type": "string"}, "days": {"type": "integer"}},
                "required": ["text", "days"],
            },
        },
        "profileSuggestions": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {"field": {"type": "string"}, "value": {"type": "string"}, "evidence": {"type": "string"}},
                "required": ["field", "value", "evidence"],
            },
        },
        "receiptAnswer": {"type": "integer", "enum": [1, 2, 3]},
        "useAnswer": {"type": "integer", "enum": [1, 2, 3]},
        "issueTypes": {"type": "array", "items": {"type": "string", "enum": list(FOLLOWUP_ISSUE_TYPES)}},
        "satisfaction": {"type": "integer", "enum": [1, 2, 3]},
        "reviewStatus": {"type": "integer", "enum": list(FOLLOWUP_REVIEW_STATUSES)},
        "etaDate": {"type": "string"},
    },
    "required": ["summary", "situation", "blocker", "nextActions", "profileSuggestions"],
}

FOLLOWUP_REPLY_SCHEMA = {
    "type": "object",
    "properties": {"reply": {"type": "string"}, "intent": {"type": "string"}},
    "required": ["reply", "intent"],
}


def followup_extract_input(input_data: dict) -> dict:
    """原文只保留最后 FOLLOWUP_RAW_MAX 字（最新的消息在最后），并标出是否截断"""
    raw = (input_data.get("rawContent") or "").strip()
    truncated = len(raw) > FOLLOWUP_RAW_MAX
    return {**input_data, "rawContent": raw[-FOLLOWUP_RAW_MAX:] if truncated else raw, "truncated": truncated}


def sanitize_followup_extract(structured: dict, input_data: dict) -> dict:
    """只保留合法取值：客户情况只取三个固定值，卡点只取输入的卡点码，档案字段只取输入的可补字段，下一步最多 2 条"""
    blockers = {b.get("code") for b in input_data.get("blockers") or [] if isinstance(b, dict)}
    fields = {f.get("field") for f in input_data.get("profileFields") or [] if isinstance(f, dict)}
    raw_situation = structured.get("situation") or []
    board = (input_data.get("stage") or {}).get("board")
    if board == FOLLOWUP_ACCOUNT_BOARD:
        situation = [x for x in FOLLOWUP_ACCOUNT_SITUATIONS + ("no_reply",) if x in raw_situation][:1]
    elif board == FOLLOWUP_DEVELOP_BOARD:
        situation = [x for x in FOLLOWUP_DEVELOP_SITUATIONS + ("no_reply",) if x in raw_situation][:1]
    else:
        situation = [x for x in FOLLOWUP_SITUATIONS if x in raw_situation]
        if "no_reply" in situation and len(situation) > 1:
            situation = [x for x in situation if x != "no_reply"]
    blocker = structured.get("blocker") or ""
    actions = []
    for a in structured.get("nextActions") or []:
        text = str(a.get("text") or "").strip()
        if text:
            actions.append({"text": text[:200], "days": max(0, min(int(a.get("days") or 0), 60))})
    suggestions = []
    for sug in structured.get("profileSuggestions") or []:
        value = str(sug.get("value") or "").strip()
        if sug.get("field") in fields and value:
            suggestions.append({"field": sug["field"], "value": value[:500],
                                "evidence": str(sug.get("evidence") or "")[:200]})
    out = {
        "summary": str(structured.get("summary") or "").strip()[:1000],
        "situation": situation,
        "blocker": blocker if blocker in blockers else "",
        "nextActions": actions[:2],
        "profileSuggestions": suggestions,
        "truncated": bool(input_data.get("truncated")),
    }
    if situation == ["buying_signal"]:
        out["signalText"] = str(structured.get("signalText") or "").strip()[:FOLLOWUP_SIGNAL_MAX]
    out.update(stage_answers(structured, input_data))
    return out


def stage_answers(structured: dict, input_data: dict) -> dict:
    """订单履约、售后评价阶段的答案建议：只保留当前阶段的字段和合法取值"""
    stage = (input_data.get("stage") or {}).get("code") or ""
    out = {}
    for field in FOLLOWUP_STAGE_FIELDS.get(stage, ()):
        value = structured.get(field)
        if field == "issueTypes":
            picked = [x for x in FOLLOWUP_ISSUE_TYPES if x in (value or [])]
            if picked:
                out[field] = picked
        elif field == "etaDate":
            try:
                out[field] = date.fromisoformat(str(value or "").strip()).isoformat()
            except ValueError:
                pass
        elif field == "reviewStatus":
            if value in FOLLOWUP_REVIEW_STATUSES:
                out[field] = value
        elif value in (1, 2, 3):
            out[field] = value
    return out


def call_followup_extract(cfg: AiConfig, input_data: dict) -> dict:
    """整理一次跟进；不联网，用翻译的模型与思考强度（轻量、快）"""
    prepared = followup_extract_input(input_data)
    structured = call_api(cfg, _read_skill(FOLLOWUP_EXTRACT_SKILL), json.dumps(prepared, ensure_ascii=False, indent=1), [],
                          FOLLOWUP_EXTRACT_SCHEMA, role="translate")
    if "summary" not in structured:
        raise RuntimeError("AI 未返回符合 schema 的跟进整理结果")
    return sanitize_followup_extract(structured, prepared)


def call_followup_reply(cfg: AiConfig, input_data: dict) -> dict:
    """按客户上下文和卡点写一条英文跟进消息"""
    structured = call_api(cfg, _read_skill(FOLLOWUP_REPLY_SKILL), json.dumps(input_data, ensure_ascii=False, indent=1), [],
                          FOLLOWUP_REPLY_SCHEMA, role="translate")
    if not (structured.get("reply") or "").strip():
        raise RuntimeError("AI 未返回英文回复")
    return {"reply": structured["reply"].strip(), "intent": str(structured.get("intent") or "").strip()[:200]}


def process_followup(payload: dict, callback_url: str, skill_id: str):
    start_job()
    key = _api_key_of(payload)
    try:
        cfg = parse_chain(payload, callback_url)
        input_data = payload.get("input") or {}
        if skill_id == FOLLOWUP_EXTRACT_SKILL:
            if not (input_data.get("rawContent") or "").strip():
                send_callback(callback_url, "failed", error="没有需要整理的内容")
                return
            output = call_followup_extract(cfg, input_data)
        else:
            output = call_followup_reply(cfg, input_data)
        send_callback(callback_url, "success", output=output)
    except ConfigError as e:
        send_callback(callback_url, "failed", error=str(e))
    except (JobTimeout, anthropic.APITimeoutError, openai.APITimeoutError):
        send_callback(callback_url, "failed", error=f"AI 处理超过 {TIMEOUT_SECONDS} 秒未完成")
    except Exception as e:
        message = scrub(str(e), key)
        print(f"[ai-orchestrator] {skill_id} failed: {message}")
        send_callback(callback_url, "failed", error=message[:500])


QUOTE_PARSE_SKILL = "sourcing-quote-parse"
QUOTE_RAW_MAX = 20000
QUOTE_ROWS_MAX = 200
QUOTE_UNKNOWN_MAX = 50
QUOTE_CONFIDENCE = ("sure", "check")

QUOTE_PARSE_SCHEMA = {
    "type": "object",
    "properties": {
        "rows": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {
                    "itemId": {"type": "integer"},
                    "raw": {"type": "string"},
                    "unitPrice": {"type": "number"},
                    "taxIncluded": {"type": "boolean"},
                    "taxRate": {"type": "integer"},
                    "itemCondition": {"type": "integer"},
                    "leadTime": {"type": "integer"},
                    "note": {"type": "string"},
                    "confidence": {"type": "string", "enum": list(QUOTE_CONFIDENCE)},
                },
                "required": ["itemId", "raw", "taxIncluded", "note", "confidence"],
            },
        },
        "otherModels": {
            "type": "array",
            "items": {
                "type": "object",
                "properties": {"taskId": {"type": "integer"}, "model": {"type": "string"}},
                "required": ["taskId", "model"],
            },
        },
        "unknownModels": {"type": "array", "items": {"type": "string"}},
        "common": {"type": "string"},
    },
    "required": ["rows", "otherModels", "unknownModels", "common"],
}


def _codes(input_data: dict, key: str) -> set:
    return {d.get("code") for d in input_data.get(key) or [] if isinstance(d, dict)}


def sanitize_quote_parse(structured: dict, input_data: dict) -> dict:
    """明细 id 只留当前任务的，单价、字典值不合法的置空并改为「请核对」；其他任务的型号只留输入里的任务。
    后端处理器还会按租户字典再校验一遍，这里先把明显不合法的去掉"""
    item_ids = {i.get("id") for i in input_data.get("items") or [] if isinstance(i, dict)}
    conditions, lead_times, tax_rates = (_codes(input_data, k) for k in ("conditions", "leadTimes", "taxRates"))
    rows, seen = [], set()
    for r in structured.get("rows") or []:
        item_id = r.get("itemId")
        if item_id not in item_ids or item_id in seen:
            continue
        seen.add(item_id)
        check = r.get("confidence") != "sure"
        row = {"itemId": item_id, "raw": str(r.get("raw") or "").strip()[:200],
               "taxIncluded": bool(r.get("taxIncluded")), "note": str(r.get("note") or "").strip()[:200]}
        price = r.get("unitPrice")
        if isinstance(price, (int, float)) and not isinstance(price, bool) and price >= 0:
            row["unitPrice"] = price
        elif price is not None:
            check = True
        if row["taxIncluded"]:
            rate = r.get("taxRate")
            if rate in tax_rates:
                row["taxRate"] = rate
            else:
                check = True
        for field, allowed in (("itemCondition", conditions), ("leadTime", lead_times)):
            value = r.get(field)
            if value in allowed:
                row[field] = value
            elif value not in (None, 0):
                check = True
        row["confidence"] = "check" if check else "sure"
        rows.append(row)
    other_ids = {t.get("id") for t in input_data.get("otherTasks") or [] if isinstance(t, dict)}
    others, seen_other = [], set()
    for m in structured.get("otherModels") or []:
        model = str(m.get("model") or "").strip()[:100]
        if m.get("taskId") in other_ids and model and (m["taskId"], model) not in seen_other:
            seen_other.add((m["taskId"], model))
            others.append({"taskId": m["taskId"], "model": model})
    unknown = list(dict.fromkeys(s for s in (str(x).strip()[:100] for x in structured.get("unknownModels") or []) if s))
    return {"rows": rows[:QUOTE_ROWS_MAX], "otherModels": others, "unknownModels": unknown[:QUOTE_UNKNOWN_MAX],
            "common": str(structured.get("common") or "").strip()[:500]}


def call_quote_parse(cfg: AiConfig, input_data: dict) -> dict:
    """识别粘贴的报价；不联网，用翻译的模型与思考强度（轻量、快，采购在弹窗里等）"""
    raw = (input_data.get("rawContent") or "").strip()[:QUOTE_RAW_MAX]
    prepared = {**input_data, "rawContent": raw}
    structured = call_api(cfg, _read_skill(QUOTE_PARSE_SKILL), json.dumps(prepared, ensure_ascii=False, indent=1), [],
                          QUOTE_PARSE_SCHEMA, role="translate")
    if "rows" not in structured:
        raise RuntimeError("AI 未返回符合 schema 的报价识别结果")
    return sanitize_quote_parse(structured, prepared)


def process_quote_parse(payload: dict, callback_url: str):
    start_job()
    key = _api_key_of(payload)
    try:
        cfg = parse_chain(payload, callback_url)
        input_data = payload.get("input") or {}
        if not (input_data.get("rawContent") or "").strip():
            send_callback(callback_url, "failed", error="没有需要识别的报价")
            return
        send_callback(callback_url, "success", output=call_quote_parse(cfg, input_data))
    except ConfigError as e:
        send_callback(callback_url, "failed", error=str(e))
    except (JobTimeout, anthropic.APITimeoutError, openai.APITimeoutError):
        send_callback(callback_url, "failed", error=f"AI 处理超过 {TIMEOUT_SECONDS} 秒未完成")
    except Exception as e:
        message = scrub(str(e), key)
        print(f"[ai-orchestrator] {QUOTE_PARSE_SKILL} failed: {message}")
        send_callback(callback_url, "failed", error=message[:500])


def send_callback(callback_url: str, status: str, output=None, error=None) -> bool:
    """回调带上本线程累计的用量（spec ai-platform/task-orchestration「记录 AI 调用用量」）；旧版后端忽略 usage。
    投递失败（连不上、超时、后端 5xx）按 CALLBACK_RETRY_DELAYS 重投同一结果，不重新调用模型；
    后端按任务幂等接收，重复的回调不会重复处理（audit AI-002）。返回是否投递成功"""
    body = {"status": status, "output": output, "error": error}
    usage = _take_usage()
    if usage:
        body["usage"] = usage
    chain = _forget_chain(callback_url)
    if chain is not None:
        if chain.used:
            body["connection"] = chain.used
        if chain.failovers:
            body["failovers"] = chain.failovers
    body = json.dumps(body).encode("utf-8")
    req = urllib.request.Request(
        callback_url,
        data=body,
        method="POST",
        headers={"Content-Type": "application/json", "X-AI-Callback-Token": CALLBACK_TOKEN},
    )
    for attempt, delay in enumerate(CALLBACK_RETRY_DELAYS + (None,), 1):
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                print(f"[ai-orchestrator] callback -> {callback_url} status={resp.status}")
                return True
        except urllib.error.HTTPError as e:
            if e.code < 500 and e.code != 429:
                # 令牌不对、任务不存在等，重投也不会成功
                print(f"[ai-orchestrator] callback REJECTED -> {callback_url}: HTTP {e.code}")
                return False
            reason = f"HTTP {e.code}"
        except (urllib.error.URLError, OSError) as e:
            reason = str(e)
        except ValueError as e:
            print(f"[ai-orchestrator] callback FAILED -> {callback_url}: {e}")
            return False
        if delay is None:
            break
        print(f"[ai-orchestrator] callback FAILED -> {callback_url}（第 {attempt} 次）: {reason}，{delay} 秒后重投")
        time.sleep(delay)
    print(f"[ai-orchestrator] callback GAVE UP -> {callback_url}: 重投 {len(CALLBACK_RETRY_DELAYS)} 次仍失败，"
          f"任务将由后端超时扫描判为失败")
    return False


def send_failover(callback_url: str, event: dict):
    """执行中刚发生一次切换就报告后端，让失败的连接方式立刻暂停、新任务避开；只发一次、不重投，失败不影响任务
    （结果回调会再带上全部切换）"""
    body = json.dumps({"status": "failover", "failovers": [event]}).encode("utf-8")
    req = urllib.request.Request(callback_url, data=body, method="POST",
                                 headers={"Content-Type": "application/json", "X-AI-Callback-Token": CALLBACK_TOKEN})
    try:
        with urllib.request.urlopen(req, timeout=10):
            pass
    except (urllib.error.URLError, OSError, ValueError) as e:
        print(f"[ai-orchestrator] 切换报告失败（{event.get('connection')}）：{type(e).__name__}")


def send_progress(callback_url: str, done: int, total: int, stage: str):
    """报告分批解析进度（已完成批数 / 总批数）与当前阶段（read / extract / merge / verify），
    只发一次、不重投，失败不影响任务（spec「解析进度报告」）"""
    body = json.dumps({"status": "progress",
                       "output": {"done": done, "total": total, "stage": stage}}).encode("utf-8")
    req = urllib.request.Request(callback_url, data=body, method="POST",
                                 headers={"Content-Type": "application/json", "X-AI-Callback-Token": CALLBACK_TOKEN})
    try:
        with urllib.request.urlopen(req, timeout=10):
            pass
    except (urllib.error.URLError, OSError, ValueError) as e:
        print(f"[ai-orchestrator] 进度报告失败（{done}/{total}）：{type(e).__name__}")


def _nameplate_no(ref: dict):
    """后端给铭牌图片标了 role=nameplate 与编号 no；旧版后端没有这两个字段"""
    if ref.get("role") != "nameplate":
        return None
    try:
        return int(ref.get("no"))
    except (TypeError, ValueError):
        return None


EXTRACT_SCHEMA = copy.deepcopy(OUTPUT_SCHEMA)
EXTRACT_SCHEMA["properties"]["groups"]["items"]["properties"]["items"]["items"]["properties"]["source"] = {"type": "string"}

EXTRACT_PROMPT = (
    "这是一份大询盘拆开后的第 {no} / {total} 部分，包含：{labels}。只提取这部分出现的型号、数量、单位等询盘信息，"
    "按 skill 的结构返回；这一步不联网搜索，描述等字段按已有知识填写，拿不准的置信度填待核实。"
    "每个型号在 source 里写它出现的位置，用上面标注的「《文件》第 n 页」或行号。"
)
VERIFY_PROMPT = (
    "下面是从一份大询盘中提取、合并去重后的 {count} 个型号（JSON 数组）。请按 skill 流程逐个联网核验、纠正型号，"
    "补全订货号、描述、英文描述、生命周期、替代型号、采购难度、询价话术与搜索关键词。"
    "按原顺序逐个返回全部 {count} 个型号，不要增删、不要合并，数量与单位保持不变；"
    "每个型号的 originalModel 必须与下面给出的原样一致（用来对回原型号），纠正后的型号写在 confirmedModel。\n{items}"
)
VERIFIED_FIELDS = ("categoryCode", "confirmedModel", "orderCode", "confidence", "description", "descriptionEn",
                   "lifecycle", "replacementModel", "difficulty", "inquiryScript", "searchKeywords")


def _call_parse_degrading(cfg, raw_content, loaded, categories, nos):
    """单次解析；订阅方式达到搜索阈值时同一批改为不联网重新解析，型号标待核实，任务不失败（D10）"""
    try:
        return call_parse(cfg, raw_content, loaded, categories, nos)
    except SearchLimitError:
        print("[ai-orchestrator] 达到联网搜索上限，改为不联网重新解析")
        structured = call_parse(cfg, raw_content, loaded, categories, nos, web_search=False)
        for g in structured.get("groups", []):
            for item in g.get("items", []):
                item["confidence"] = lp.CONFIDENCE_PENDING_VERIFY
        return structured


def _small_inputs(refs, raw_content, work):
    """原文件放得进一次请求时返回 (正文, [(内容, 扩展名)], 铭牌编号)，与改动前的单次解析一致；放不下时返回 None。
    后端给出附件大小时先按大小判断，免得为大询盘先把文件全部下载下来"""
    sizes = [r.get("size") for r in refs]
    if sum(x for x in sizes if isinstance(x, int)) > lp.SMALL_MAX_BYTES:
        return None
    text_parts, loaded, nos, total, pages = [], [], [], 0, 0
    for i, r in enumerate(refs):
        path = lp.download(r, work, remaining)
        ext = "." + (r.get("ext") or "").lower().lstrip(".")
        name = r.get("name") or f"附件{i + 1}"
        try:
            total += os.path.getsize(path)
            if total > lp.SMALL_MAX_BYTES:
                return None
            if ext in (".xlsx", ".xls", ".csv"):
                text_parts.append(f"附件《{name}》：\n" + lp.sheet_text(path, ext))
                continue
            if ext == ".pdf":
                import pymupdf
                with pymupdf.open(path) as doc:
                    if doc.needs_pass:
                        return None
                    pages += len(doc)
            else:
                pages += 1
            with open(path, "rb") as fh:
                loaded.append((fh.read(), ext))
            nos.append(_nameplate_no(r))
        except Exception:  # noqa: BLE001 - 读不了的附件交给分批流程逐个报告
            return None
        finally:
            lp._remove(path)
    text = "\n\n".join(([raw_content] if raw_content else []) + text_parts)
    if pages > lp.SMALL_MAX_PDF_PAGES or len(text) > lp.SMALL_MAX_CHARS:
        return None
    return text, loaded, nos


def parse_large(cfg, raw_content, refs, categories, callback_url, work) -> dict:
    """分批解析：逐个附件下载、预处理、删除 → 切批 → 并行提取 → 合并去重 → 分批联网核验（D8–D10）。
    心跳从读取附件开始一直到核验结束（audit AI-009）；无论成功、失败还是超时，
    工作线程已经产生的用量都汇总回本线程，随回调上报（audit AI-010）"""
    usages = []
    try:
        with lp.Progress(lambda d, t, s: send_progress(callback_url, d, t, s), 0, lp.STAGE_READ) as progress:
            return _parse_large(cfg, raw_content, refs, categories, work, progress, usages)
    finally:
        for u in usages:
            _record_usage(u.get("model"), u.get("inputTokens"), u.get("outputTokens"), u.get("webSearches"))


def _parse_large(cfg, raw_content, refs, categories, work, progress, usages) -> dict:
    units, unparsed = [], []
    if raw_content:
        units.append(lp.Unit("text", "询盘正文", text="询盘正文：\n" + raw_content))
    for i, r in enumerate(refs):
        name = r.get("name") or f"附件{i + 1}"
        ext = "." + (r.get("ext") or "").lower().lstrip(".")
        try:
            path = lp.download(r, work, remaining)
        except JobTimeout:
            raise
        except Exception as e:  # noqa: BLE001 - 一个附件取不到只影响它自己
            print(f"[ai-orchestrator] 附件下载失败：{type(e).__name__}")
            unparsed.append({"file": name, "range": "", "reason": "无法读取"})
            continue
        try:
            got, bad = lp.preprocess(path, ext, name, _nameplate_no(r), remaining)
        finally:
            lp._remove(path)
        units.extend(got)
        unparsed.extend(bad)
    if not units:
        raise RuntimeError("；".join(f"《{u['file']}》{u['range']}{u['reason']}" for u in unparsed) or "没有可解析的内容")

    batches = lp.pack(units)
    job_deadline = _JOB.deadline
    propagate = _call_deadline_propagator(job_deadline)

    def job_expired() -> bool:
        """整个任务到期才算超时；单次调用到期（TIMEOUT_SECONDS）只算这一批的一次失败"""
        return time.monotonic() >= job_deadline

    def reason(e: Exception) -> str:
        """失败原因写进日志：异常类型与脱敏后的说明，不含询盘内容"""
        return f"{type(e).__name__}: {scrub(str(e), _keys_of(cfg))[:200]}"
    usage_lock = threading.Lock()
    system_prompt = build_system_prompt(categories)

    def collect_usage():
        u = _take_usage()
        if u:
            with usage_lock:
                usages.append(u)

    print(f"[ai-orchestrator] 大附件分批解析：{len(units)} 个单元，{len(batches)} 批")
    progress.set_stage(lp.STAGE_EXTRACT, len(batches))

    def extract(indexed):
        no, batch = indexed
        labels = [u.label for u in batch]
        texts = [u.text for u in batch if u.kind == "text"]
        images = [u for u in batch if u.kind == "image"]
        text = (EXTRACT_PROMPT.format(no=no, total=len(batches), labels="、".join(labels)) + "\n\n"
                + "\n\n".join(texts)
                + ("\n\n附件图片依次为：" + "、".join(u.label for u in images) if images else "")
                + _nameplate_hint([u.nameplate_no for u in images]))
        for attempt in range(lp.EXTRACT_RETRIES + 1):
            propagate()  # 每次调用（含重试）重新计算单次期限
            try:
                result = call_api(cfg, system_prompt, text, [(u.image, u.ext) for u in images], EXTRACT_SCHEMA,
                                  role="parse", web_search=False)
                collect_usage()
                progress.step()
                return result.get("groups", []), labels
            except Exception as e:  # noqa: BLE001 - 失败的批重试，仍失败只影响这一批
                if isinstance(e, JobTimeout) and job_expired():
                    raise
                collect_usage()
                print(f"[ai-orchestrator] 第 {no} 批提取失败（第 {attempt + 1} 次）：{reason(e)}")
        progress.step()
        unparsed.append({"file": "", "range": "、".join(labels),
                         "reason": f"解析失败，重试 {lp.EXTRACT_RETRIES} 次仍未成功"})
        return None

    results = lp.run_parallel(extract, list(enumerate(batches, 1)), lp.EXTRACT_WORKERS, propagate)
    progress.set_stage(lp.STAGE_MERGE)
    items, group_meta = lp.merge([r for r in results if r])
    if not items:
        raise RuntimeError("没有从附件中提取到型号")

    chunks = [items[i:i + lp.VERIFY_BATCH] for i in range(0, len(items), lp.VERIFY_BATCH)]
    progress.set_stage(lp.STAGE_VERIFY, len(chunks))

    def verify(chunk):
        brief = [dict({k: it.get(k) for k in ("originalModel", "confirmedModel", "quantity", "unit", "description")},
                      brand=it["_group"][0], category=it["_group"][1]) for it in chunk]
        propagate()
        try:
            result = call_api(cfg, system_prompt, VERIFY_PROMPT.format(count=len(chunk), items=lp.dumps(brief)),
                              [], OUTPUT_SCHEMA, role="parse", web_search=True)
            collect_usage()
            # 按原文型号把核验结果对回提交的型号，不依赖返回顺序（实测模型会调换顺序，按位置对会张冠李戴）；
            # 对不上的型号不采用核验结果，标待核实。原文型号相同的可能有多条（各批确认成不同型号），
            # 都登记，优先按确认型号对回，否则取最先提交的一条（AI-013）
            pending = {}
            for it in chunk:
                pending.setdefault(lp.normalize_model(it.get("originalModel") or it.get("confirmedModel")), []).append(it)
            matched = []
            for g in result.get("groups", []):
                for v in g.get("items", []):
                    same = pending.get(lp.normalize_model(v.get("originalModel")))
                    if not same:
                        continue
                    confirmed = lp.normalize_model(v.get("confirmedModel"))
                    it = next((x for x in same if lp.normalize_model(x.get("confirmedModel")) == confirmed), same[0])
                    same.remove(it)
                    matched.append((g, v, it))
            pending = [it for same in pending.values() for it in same]
            for it in pending:
                it["confidence"] = lp.CONFIDENCE_PENDING_VERIFY
                it["correctionNote"] = "；".join(x for x in (it.get("correctionNote"), "未联网核验") if x)
            if pending:
                print(f"[ai-orchestrator] 核验结果有 {len(pending)} 个型号对不上，标为待核实")
            for g, v, it in matched:
                for k in VERIFIED_FIELDS:
                    if k in v:
                        it[k] = v[k]
                if v.get("correctionNote"):
                    it["correctionNote"] = "；".join(x for x in (v["correctionNote"], it.get("correctionNote")) if x)
                if it.get("_unit_conflict"):
                    it["confidence"] = lp.CONFIDENCE_PENDING_VERIFY
                meta_key = (g.get("brand", it["_group"][0]), g.get("category", it["_group"][1]))
                with usage_lock:
                    group_meta.setdefault(meta_key, {k: x for k, x in g.items() if k != "items"})
                it["_group"] = meta_key
        except Exception as e:  # noqa: BLE001 - 核验失败或达到搜索上限：这批不核验，标待核实（D10）
            if isinstance(e, JobTimeout) and job_expired():
                raise
            collect_usage()
            print(f"[ai-orchestrator] 核验批失败，标为待核实：{reason(e)}")
            for it in chunk:
                it["confidence"] = lp.CONFIDENCE_PENDING_VERIFY
                it["correctionNote"] = "；".join(x for x in (it.get("correctionNote"), "未联网核验") if x)
        progress.step()

    lp.run_parallel(verify, chunks, lp.EXTRACT_WORKERS, propagate)
    output = {"groups": lp.regroup(items, group_meta)}
    if unparsed:
        output["unparsed"] = unparsed
    searches = sum(int(u.get("webSearches") or 0) for u in usages)
    print(f"[ai-orchestrator] 大附件解析完成：{len(items)} 个型号，{len(unparsed)} 处未解析，联网搜索共 {searches} 次")
    return output


def process_job(payload: dict, callback_url: str):
    start_job()
    limit = TIMEOUT_SECONDS
    key = _api_key_of(payload)
    try:
        cfg = parse_chain(payload, callback_url)
        input_data = payload.get("input") or {}
        raw_content = (input_data.get("rawContent") or "").strip()
        # 附件都通过后端签发的临时链接传来（[{"url", "ext", "name", "size", "role", "no"}]），本服务下载后处理。
        # 旧版的本地路径 attachmentPaths / rawAttachmentPath 不再接受。
        refs = [a for a in (input_data.get("attachments") or []) if a and a.get("url")]
        if not raw_content and not refs:
            send_callback(callback_url, "failed", error="询盘既没有正文也没有可读的附件，无法解析")
            return
        categories = input_data.get("categories") or []
        with tempfile.TemporaryDirectory(prefix="zhul-parse-") as work:
            try:
                small = _small_inputs(refs, raw_content, work) if refs else (raw_content, [], [])
            except JobTimeout:
                raise
            except Exception as e:  # noqa: BLE001 - 链接过期或网络问题都按附件读取失败回报（只有一个附件时）
                if len(refs) == 1 and not raw_content:
                    send_callback(callback_url, "failed", error=f"附件下载失败: {str(e)[:200]}")
                    return
                small = None
            if small is not None:
                text, loaded, nos = small
                output = _call_parse_degrading(cfg, text, loaded, categories, nos)
            else:
                limit = MAX_SECONDS
                start_job(limit)
                output = parse_large(cfg, raw_content, refs, categories, callback_url, work)
        send_callback(callback_url, "success", output=output)
    except ConfigError as e:
        send_callback(callback_url, "failed", error=str(e))
    except (JobTimeout, anthropic.APITimeoutError, openai.APITimeoutError):
        send_callback(callback_url, "failed", error=f"AI 处理超过 {limit} 秒未完成")
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
    if isinstance(e, (anthropic.APIStatusError, openai.APIStatusError)) and e.status_code >= 500:
        return f"服务商暂时不可用（HTTP {e.status_code}）"
    if isinstance(e, (anthropic.RateLimitError, openai.RateLimitError)):
        return "服务商限流，请稍后重试"
    return scrub(str(e), key)[:200]


def _retrieve_model(cfg: AiConfig, model: str):
    """查询单个模型信息（不产生调用费用）"""
    if cfg.provider == "openai":
        openai.OpenAI(api_key=cfg.api_key, timeout=QUICK_TIMEOUT_SECONDS, max_retries=0).models.retrieve(model)
    else:
        anthropic.Anthropic(api_key=cfg.api_key, timeout=QUICK_TIMEOUT_SECONDS, max_retries=0).models.retrieve(model)


def _check_model(cfg: AiConfig, model: str):
    """查询一个模型，成功返回 None，失败返回服务商异常"""
    try:
        _retrieve_model(cfg, model)
        return None
    except (anthropic.APIError, openai.APIError) as e:
        return e


def test_connection(raw) -> dict:
    """测试连接：API 方式查询 Key 与两个模型，订阅方式检查对应 CLI 是否登录。
    各项并行检查，快速检查不让 SDK 自动重试，整次诊断在后端 30 秒期限内返回（audit AI-004）"""
    try:
        cfg = parse_ai_config(raw, require_key=False)
    except ConfigError as e:
        return {"ok": False, "items": [{"name": "配置", "ok": False, "message": str(e)}]}
    if cfg.mode == "subscription":
        # 只检查所选服务商，不等另一家的 CLI
        cli = "codex" if cfg.provider == "openai" else "claude"
        name = "Codex 登录" if cli == "codex" else "Claude 登录"
        logged_in = login_status(QUICK_TIMEOUT_SECONDS, providers=(cli,))[cli]
        message = {True: "已登录", False: "未登录，请在容器内执行登录命令"}.get(logged_in, "未能检查登录状态（超时或找不到 CLI），请稍后重试")
        if logged_in and cli == "codex":
            # 本机登录文件在，服务端也可能已拒绝（登录过期）：列一次模型向服务端确认，不调用模型、不耗额度（AI-015）
            try:
                codex_models(QUICK_TIMEOUT_SECONDS)
            except RuntimeError as e:
                if getattr(e, "code", "") == "auth":
                    logged_in, message = False, "登录已失效（服务端拒绝），请在容器内重新执行登录命令"
                else:
                    message = "已登录（暂时无法向服务端确认，稍后可再测）"
        return {"ok": bool(logged_in), "items": [{"name": name, "ok": bool(logged_in), "message": message}]}
    if not cfg.api_key:
        return {"ok": False, "items": [{"name": "API Key", "ok": False, "message": "未填写 API Key"}]}
    models = [("解析模型", cfg.parse_model), ("翻译模型", cfg.translate_model)]
    with ThreadPoolExecutor(max_workers=len(models)) as pool:
        errors = list(pool.map(lambda m: _check_model(cfg, m[1]), models))
    denied = next((e for e in errors if isinstance(e, (anthropic.AuthenticationError, openai.AuthenticationError,
                                                       anthropic.PermissionDeniedError, openai.PermissionDeniedError))), None)
    if denied is not None:
        return {"ok": False, "items": [{"name": "API Key", "ok": False, "message": _provider_error(denied, cfg.api_key)}]}
    # 只有服务商实际回应了（查到模型，或回复模型不存在）才能确认 Key 有效；连不上、超时、5xx 无法确认（audit AI-006）
    verified = any(e is None or isinstance(e, (anthropic.NotFoundError, openai.NotFoundError)) for e in errors)
    if verified:
        key_item = {"name": "API Key", "ok": True, "message": "有效"}
    else:
        key_item = {"name": "API Key", "ok": False,
                    "message": "无法确认：" + _provider_error(next(e for e in errors if e is not None), cfg.api_key)}
    items = [key_item] + [{"name": f"{label} {model}", "ok": e is None,
                           "message": "可用" if e is None else _provider_error(e, cfg.api_key)}
                          for (label, model), e in zip(models, errors)]
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
            ids = [m.id for m in openai.OpenAI(api_key=cfg.api_key, timeout=QUICK_TIMEOUT_SECONDS,
                                               max_retries=0).models.list()]
            ids = sorted(i for i in ids if not any(marker in i for marker in NON_CHAT_MARKERS))
        else:
            ids = [m.id for m in anthropic.Anthropic(api_key=cfg.api_key, timeout=QUICK_TIMEOUT_SECONDS,
                                                     max_retries=0).models.list()]
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
        self._reject(401, "unauthorized")
        return False

    def _reject(self, status: int, error: str):
        """拒绝前读完请求体：带着未读数据关闭连接时，Windows 会发 RST，客户端读响应报 WinError 10053（audit AI-008）。
        请求体超过上限时不读，改为明确关闭连接，避免未授权请求占用服务。"""
        try:
            length = int(self.headers.get("Content-Length", 0))
        except ValueError:
            length = -1
        if 0 <= length <= MAX_REJECT_DRAIN_BYTES:
            if length:
                self.rfile.read(length)
        else:
            self.close_connection = True
        self.send_response(status)
        if self.close_connection:
            self.send_header("Connection", "close")
        data = json.dumps({"error": error}).encode("utf-8")
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

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
        # 两家并行检查；超时按未登录返回，后端约定不变
        status = login_status(QUICK_TIMEOUT_SECONDS)
        self._send_json(200, {"codex": {"logged_in": bool(status["codex"])}, "claude": {"logged_in": bool(status["claude"])}})

    def do_POST(self):
        if not self._authorized():
            return
        if self.path in ("/test", "/models"):
            ai = self._read_json().get("ai")
            self._send_json(200, test_connection(ai) if self.path == "/test" else list_models(ai))
            return
        match = re.fullmatch(r"/skills/([^/]+)/run", self.path)
        if not match:
            self._reject(404, "not found")
            return
        skill_id = match.group(1)
        payload = self._read_json()

        callback_url = payload.get("callback_url")
        job_id = str(uuid.uuid4())
        print(f"[ai-orchestrator] received skill={skill_id}, will callback {callback_url} (job_id={job_id})")

        self._send_json(202, {"job_id": job_id})

        if skill_id in (TRANSLATE_SKILL, NAME_SKILL, COMPANY_SKILL):
            prompt = {NAME_SKILL: NAME_PROMPT, COMPANY_SKILL: COMPANY_PROMPT}.get(skill_id, TRANSLATE_PROMPT)
            if callback_url:
                threading.Thread(target=process_translate, args=(payload, callback_url, prompt), daemon=True).start()
            return

        if skill_id in (FOLLOWUP_EXTRACT_SKILL, FOLLOWUP_REPLY_SKILL):
            if callback_url:
                threading.Thread(target=process_followup, args=(payload, callback_url, skill_id), daemon=True).start()
            return

        if skill_id == QUOTE_PARSE_SKILL:
            if callback_url:
                threading.Thread(target=process_quote_parse, args=(payload, callback_url), daemon=True).start()
            return

        if skill_id == CANDIDATE_SKILL:
            if callback_url:
                threading.Thread(target=process_candidate, args=(payload, callback_url), daemon=True).start()
            return

        if skill_id == REFINE_SKILL:
            if callback_url:
                threading.Thread(target=process_refine, args=(payload, callback_url), daemon=True).start()
            return

        if skill_id == PRODUCT_CONTENT_SKILL:
            if callback_url:
                threading.Thread(target=process_product_content, args=(payload, callback_url), daemon=True).start()
            return

        if skill_id == BACKGROUND_SKILL:
            if callback_url:
                threading.Thread(target=process_background, args=(payload, callback_url), daemon=True).start()
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
