"""AI 编排服务的单元测试：用假的 SDK 客户端拦截请求，不联网、不产生费用。

运行：pip install -r requirements.txt 后，在本目录执行 python -m unittest -v
"""
import copy
import importlib
import io
import json
import os
import subprocess
import sys
import threading
import unittest
import urllib.error
import urllib.request
from types import SimpleNamespace
from unittest import mock

REAL_OPEN = open

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

TOKEN = "unit-test-token"
KEY = "sk-unit-test-secret-key-7788"
IMAGE = (b"\x89PNG fake", ".png")
PDF = (b"%PDF-1.7 fake", ".pdf")


def load_server():
    """重新加载 server 模块（共享令牌在模块加载时读取）"""
    with mock.patch.dict(os.environ, {"AI_TASK_CALLBACK_TOKEN": TOKEN}):
        sys.modules.pop("server", None)
        return importlib.import_module("server")


def ai(**overrides):
    """请求体 ai 字段：默认 API 方式、Claude、带 Key"""
    raw = {"mode": "api", "provider": "claude", "api_key": KEY, "parse_model": "", "translate_model": "",
           "parse_effort": "", "translate_effort": "", "max_web_searches": 20}
    raw.update(overrides)
    return raw


class FakeStream:
    """messages.stream 的上下文对象：可逐段迭代（没有分段），最后取完整消息"""

    def __init__(self, message):
        self.message = message
        self.request_id = "req_test"

    def __iter__(self):
        return iter(())

    def get_final_message(self):
        return self.message


class FakeClaude:
    """记录 messages.stream 的参数，返回给定的 JSON 文本"""
    calls = []
    reply = '{"items": []}'

    def __init__(self, **kwargs):
        FakeClaude.calls.append({"client": kwargs})
        self.beta = SimpleNamespace(messages=SimpleNamespace(stream=self._stream))

    def _stream(self, **params):
        FakeClaude.calls[-1]["params"] = params
        message = SimpleNamespace(
            stop_reason="end_turn", model=params["model"],
            content=[SimpleNamespace(type="text", text=FakeClaude.reply)],
            usage=SimpleNamespace(input_tokens=1, output_tokens=1, server_tool_use=None))
        return mock.MagicMock(__enter__=lambda s: FakeStream(message),
                              __exit__=lambda s, *a: False)


class FakeOpenAI:
    """记录 responses.create 的参数，返回给定的响应"""
    calls = []
    response = None

    def __init__(self, **kwargs):
        FakeOpenAI.calls.append({"client": kwargs})
        self.responses = SimpleNamespace(create=self._create)

    def _create(self, **params):
        FakeOpenAI.calls[-1]["params"] = params
        return FakeOpenAI.response


def openai_response(text, status="completed", reason=None, output=None):
    return SimpleNamespace(
        status=status, incomplete_details=SimpleNamespace(reason=reason) if reason else None,
        output=output if output is not None else [SimpleNamespace(type="web_search_call"),
                                                 SimpleNamespace(type="message", content=[SimpleNamespace(type="output_text")])],
        output_text=text, model="gpt-test", id="resp_test",
        usage=SimpleNamespace(input_tokens=1, output_tokens=1))


PARSE_RESULT = {"groups": [{"brand": "西门子", "category": "PLC", "items": [
    {"originalModel": "6ES7", "confirmedModel": "6ES7", "confidence": 1, "quantity": 1, "unit": "pcs"}]}]}


class AiConfigTest(unittest.TestCase):
    def setUp(self):
        self.server = load_server()

    def test_defaults_per_provider(self):
        c = self.server.parse_ai_config(ai())
        self.assertEqual((c.parse_model, c.translate_model), ("claude-opus-5-5", "claude-haiku-5-5"))
        self.assertEqual((c.parse_effort, c.translate_effort, c.max_web_searches), ("medium", "medium", 20))
        o = self.server.parse_ai_config(ai(provider="openai", max_web_searches=None))
        self.assertEqual((o.parse_model, o.translate_model, o.max_web_searches), ("gpt-6.1-sol", "gpt-6-luna", 20))
        blank = self.server.parse_ai_config(ai(provider="", mode=""))
        self.assertEqual((blank.mode, blank.provider), ("api", "claude"))

    def test_invalid_values(self):
        cases = [(ai(provider="deepseek"), "claude、openai"), (ai(translate_effort="fast"), "low、medium、high、xhigh、max"),
                 (ai(max_web_searches=0), "1 到 100"), (ai(mode="paid"), "api、subscription"),
                 (ai(api_key=""), "API Key"), (None, "缺少 AI 配置")]
        for raw, message in cases:
            with self.assertRaisesRegex(self.server.ConfigError, message):
                self.server.parse_ai_config(raw)

    def test_subscription_needs_no_key_and_repr_masks(self):
        c = self.server.parse_ai_config(ai(mode="subscription", provider="openai", api_key=""))
        self.assertEqual(c.mode, "subscription")
        k = self.server.parse_ai_config(ai())
        self.assertNotIn(KEY, repr(k))
        self.assertNotIn(KEY, str(k))
        self.assertIn("sk-unit••••••••••••••••7788", repr(k))
        self.assertEqual(self.server.scrub(f"Incorrect key {KEY}", KEY), "Incorrect key sk-unit••••••••••••••••7788")
        # 短 Key 整串换成同样长度的点，显示长度一致
        self.assertEqual(self.server.mask_key("short-key"), "•" * 27)


class ClaudeTest(unittest.TestCase):
    def setUp(self):
        FakeClaude.calls = []
        self.server = load_server()

    def run_parse(self, cfg):
        FakeClaude.reply = json.dumps(PARSE_RESULT)
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            self.server.call_parse(cfg, "询盘正文", [IMAGE, PDF], [])
        return FakeClaude.calls[-1]

    def run_translate(self, cfg):
        FakeClaude.reply = '{"items": [{"key": "1", "text": "Siemens PLC"}]}'
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            self.server.call_translate(cfg, [{"key": "1", "text": "西门子 PLC"}])
        return FakeClaude.calls[-1]

    def test_defaults(self):
        cfg = self.server.parse_ai_config(ai())
        parse = self.run_parse(cfg)
        self.assertEqual(parse["client"]["api_key"], KEY)
        p = parse["params"]
        self.assertEqual(p["model"], "claude-opus-5-5")
        self.assertEqual(p["output_config"]["effort"], "medium")
        self.assertEqual(p["output_config"]["format"]["type"], "json_schema")
        self.assertEqual(p["tools"], [{"type": "web_search_20260209", "name": "web_search", "max_uses": 20}])
        self.assertEqual(p["fallbacks"], "default")
        content = p["messages"][0]["content"]
        self.assertEqual([b["type"] for b in content], ["image", "document", "text"])

        t = self.run_translate(cfg)["params"]
        self.assertEqual(t["model"], "claude-haiku-5-5")
        self.assertEqual(t["output_config"]["effort"], "medium")
        self.assertNotIn("tools", t)
        self.assertNotIn("fallbacks", t)  # Haiku 没有服务端兜底

    def test_configured_model_effort_and_searches(self):
        cfg = self.server.parse_ai_config(ai(parse_model="claude-sonnet-5-5", parse_effort="high", max_web_searches=5))
        p = self.run_parse(cfg)["params"]
        self.assertEqual((p["model"], p["output_config"]["effort"]), ("claude-sonnet-5-5", "high"))
        self.assertEqual(p["tools"][0]["max_uses"], 5)
        t = self.run_translate(cfg)["params"]
        self.assertEqual((t["model"], t["output_config"]["effort"]), ("claude-haiku-5-5", "medium"))

    def test_two_tenants_use_their_own_keys(self):
        self.run_translate(self.server.parse_ai_config(ai(api_key="sk-tenant-a-0000001")))
        self.run_translate(self.server.parse_ai_config(ai(api_key="sk-tenant-b-0000002")))
        self.assertEqual([c["client"]["api_key"] for c in FakeClaude.calls], ["sk-tenant-a-0000001", "sk-tenant-b-0000002"])


class ClaudeSchemaRegressionTest(unittest.TestCase):
    def setUp(self):
        FakeClaude.calls = []
        self.server = load_server()
        self.cfg = self.server.parse_ai_config(ai())

    def parse(self, result):
        FakeClaude.reply = json.dumps(result)
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            return self.server.call_parse(self.cfg, "询盘正文", [], [])

    def test_request_preserves_every_field_without_mutating_business_schemas(self):
        original = copy.deepcopy(self.server.OUTPUT_SCHEMA)
        translate_original = copy.deepcopy(self.server.TRANSLATE_SCHEMA)
        self.parse(PARSE_RESULT)
        schema = FakeClaude.calls[-1]["params"]["output_config"]["format"]["schema"]
        group = schema["properties"]["groups"]["items"]
        item = group["properties"]["items"]["items"]
        self.assertEqual(set(group["properties"]), {
            "brand", "category", "inquiryTemplate", "emailTemplateCn", "emailTemplateEn", "items"})
        self.assertEqual(set(item["properties"]), {
            "categoryCode", "originalModel", "confirmedModel", "orderCode", "confidence", "correctionNote", "description",
            "descriptionEn", "quantity", "unit", "remark", "lifecycle", "replacementModel", "difficulty",
            "inquiryScript", "searchKeywords", "nameplates"})
        for obj in (schema, group, item):
            self.assertEqual(set(obj["required"]), set(obj["properties"]))
            self.assertIs(obj["additionalProperties"], False)
        self.assertEqual(item["properties"]["difficulty"]["type"], ["integer", "null"])
        self.assertEqual(item["properties"]["confidence"]["type"], "integer")
        FakeClaude.reply = '{"items": []}'
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            self.server.call_translate(self.cfg, [{"key": "1", "text": "x"}])
        self.assertEqual(self.server.OUTPUT_SCHEMA, original)
        self.assertEqual(self.server.TRANSLATE_SCHEMA, translate_original)

    def test_null_means_missing_while_empty_values_are_preserved(self):
        result = copy.deepcopy(PARSE_RESULT)
        group = result["groups"][0]
        group.update(inquiryTemplate=None, emailTemplateCn="", emailTemplateEn=None)
        group["items"][0].update(description=None, remark="", lifecycle=0, difficulty=None, searchKeywords=[])
        normalized = self.parse(result)
        normalized_group = normalized["groups"][0]
        item = normalized_group["items"][0]
        self.assertNotIn("inquiryTemplate", normalized_group)
        self.assertNotIn("emailTemplateEn", normalized_group)
        self.assertEqual(normalized_group["emailTemplateCn"], "")
        self.assertNotIn("description", item)
        self.assertNotIn("difficulty", item)
        self.assertEqual((item["remark"], item["lifecycle"], item["searchKeywords"]), ("", 0, []))
        self.assertIsNone(group["inquiryTemplate"])  # 原响应对象也不改写

    def test_complete_result_round_trip(self):
        result = copy.deepcopy(PARSE_RESULT)
        group = result["groups"][0]
        group.update(inquiryTemplate="inquiry", emailTemplateCn="中文", emailTemplateEn="English")
        group["items"][0].update(categoryCode="plc", correctionNote="", description="控制器",
            descriptionEn="PLC", remark="", lifecycle=1, replacementModel="", difficulty=2,
            inquiryScript="询价", searchKeywords=["6ES7", "Siemens 6ES7"])
        self.assertEqual(self.parse(result), result)

    def test_invalid_results_use_failed_callback(self):
        missing = copy.deepcopy(PARSE_RESULT)
        del missing["groups"][0]["items"][0]["quantity"]
        required_null = copy.deepcopy(PARSE_RESULT)
        required_null["groups"][0]["items"][0]["quantity"] = None
        wrong_type = copy.deepcopy(PARSE_RESULT)
        wrong_type["groups"][0]["items"][0]["confidence"] = "private-customer-business-value"
        for result in ({}, missing, required_null, wrong_type):
            with self.subTest(result=result):
                FakeClaude.reply = json.dumps(result)
                with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude), \
                        mock.patch.object(self.server, "send_callback") as callback, \
                        mock.patch("builtins.print") as printed:
                    self.server.process_job({"ai": ai(), "input": {"rawContent": "model"}}, "http://cb")
                callback.assert_called_once()
                self.assertEqual(callback.call_args.args, ("http://cb", "failed"))
                self.assertEqual(callback.call_args.kwargs["error"], "AI 返回结果不符合业务 Schema")
                self.assertNotIn("private-customer-business-value", str(printed.call_args_list))
                self.assertNotIn("output", callback.call_args.kwargs)

    def test_bad_json_and_provider_schema_error_are_not_swallowed(self):
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            FakeClaude.reply = "not-json"
            with self.assertRaises(json.JSONDecodeError):
                self.server.call_parse(self.cfg, "model")
        error = self.server.anthropic.BadRequestError(
            "Schema is too complex", response=mock.MagicMock(status_code=400, headers={}), body=None)
        with mock.patch.object(self.server.anthropic, "Anthropic", side_effect=error), \
                mock.patch.object(self.server, "send_callback") as callback:
            self.server.process_job({"ai": ai(), "input": {"rawContent": "model"}}, "http://cb")
        self.assertEqual(callback.call_args.args, ("http://cb", "failed"))
        self.assertIn("Schema is too complex", callback.call_args.kwargs["error"])

    def test_subscription_requests_and_claude_command_unchanged_after_api_call(self):
        import subscription

        schemas = copy.deepcopy((self.server.OUTPUT_SCHEMA, self.server.TRANSLATE_SCHEMA))
        snapshots = {}
        commands = {}
        for phase in ("before", "after"):
            snapshots[phase] = []
            for provider in ("claude", "openai"):
                cfg = self.server.parse_ai_config(ai(mode="subscription", provider=provider, api_key=""))
                with mock.patch.object(self.server, "call_subscription", return_value=PARSE_RESULT) as call:
                    self.assertEqual(self.server.call_parse(cfg, "model", [IMAGE], []), PARSE_RESULT)
                snapshots[phase].append(copy.deepcopy((call.call_args.args, call.call_args.kwargs)))
            args, kwargs = snapshots[phase][0]
            self.assertEqual(args[4], schemas[0])
            self.assertEqual(kwargs["max_web_searches"], 20)
            self.assertTrue(kwargs["web_search"])
            commands[phase] = subscription.claude_command({
                "web_search": kwargs["web_search"], "model": kwargs["model"], "effort": kwargs["effort"],
                "schema": args[4], "system_prompt": args[1]})
            if phase == "before":
                self.parse(PARSE_RESULT)
        self.assertEqual(snapshots["before"], snapshots["after"])
        self.assertEqual(commands["before"], commands["after"])
        command = commands["after"]
        self.assertEqual(json.loads(command[command.index("--json-schema") + 1]), schemas[0])
        self.assertEqual((self.server.OUTPUT_SCHEMA, self.server.TRANSLATE_SCHEMA), schemas)


class CallbackTest(unittest.TestCase):
    def setUp(self):
        self.server = load_server()

    def translate(self, payload, **patches):
        sent = []
        with mock.patch.object(self.server, "send_callback",
                               lambda url, status, output=None, error=None: sent.append((status, error))):
            with mock.patch.multiple(self.server, **patches) if patches else mock.MagicMock():
                self.server.process_translate(payload, "http://cb")
        return sent[-1]

    def test_missing_or_invalid_config_fails_task(self):
        for raw, message in ((None, "缺少 AI 配置"), (ai(api_key=""), "API Key"), (ai(translate_effort="fast"), "low、medium")):
            status, error = self.translate({"input": {"items": [{"key": "1", "text": "x"}]}, "ai": raw})
            self.assertEqual(status, "failed")
            self.assertIn(message, error)

    def test_timeouts_of_both_sdks(self):
        request = mock.MagicMock()
        for error in (self.server.anthropic.APITimeoutError(request=request),
                      self.server.openai.APITimeoutError(request=request)):
            status, message = self.translate({"input": {"items": [{"key": "1", "text": "x"}]}, "ai": ai()},
                                             call_translate=mock.MagicMock(side_effect=error))
            self.assertEqual(status, "failed")
            self.assertIn("秒未完成", message)

    def test_error_and_log_never_contain_key(self):
        with mock.patch("builtins.print") as printed:
            status, error = self.translate({"input": {"items": [{"key": "1", "text": "x"}]}, "ai": ai()},
                                           call_translate=mock.MagicMock(side_effect=RuntimeError(f"bad key {KEY}")))
        self.assertEqual(status, "failed")
        self.assertNotIn(KEY, error)
        self.assertNotIn(KEY, " ".join(str(c) for c in printed.call_args_list))


class OpenAISchemaTest(unittest.TestCase):
    def setUp(self):
        self.server = load_server()

    def objects(self, node):
        if isinstance(node, dict):
            if node.get("type") == "object" or node.get("type") == ["object", "null"]:
                yield node
            for value in node.values():
                yield from self.objects(value)
        elif isinstance(node, list):
            for value in node:
                yield from self.objects(value)

    def test_every_field_required_and_optional_nullable(self):
        original = json.dumps(self.server.OUTPUT_SCHEMA, sort_keys=True)
        schema = self.server._required_nullable_schema(self.server.OUTPUT_SCHEMA)
        for obj in self.objects(schema):
            self.assertEqual(sorted(obj["required"]), sorted(obj["properties"]))
            self.assertIs(obj["additionalProperties"], False)
        item = schema["properties"]["groups"]["items"]["properties"]["items"]["items"]["properties"]
        self.assertEqual(item["originalModel"]["type"], "string")  # 原必填字段不变
        self.assertEqual(item["replacementModel"]["type"], ["string", "null"])
        self.assertEqual(item["searchKeywords"]["type"], ["array", "null"])
        self.assertEqual(json.dumps(self.server.OUTPUT_SCHEMA, sort_keys=True), original)  # 不改原结构

    def test_drop_nulls(self):
        result = self.server._drop_nulls({"a": None, "groups": [{"brand": "x", "inquiryTemplate": None,
                                                                 "items": [{"remark": None, "quantity": 0}]}]})
        self.assertEqual(result, {"groups": [{"brand": "x", "items": [{"quantity": 0}]}]})


class OpenAITest(unittest.TestCase):
    def setUp(self):
        FakeOpenAI.calls = []
        self.server = load_server()
        self.cfg = self.server.parse_ai_config(ai(provider="openai"))

    def call(self, fn, *args):
        with mock.patch.object(self.server.openai, "OpenAI", FakeOpenAI):
            result = fn(*args)
        return result, FakeOpenAI.calls[-1]

    def test_parse_request(self):
        FakeOpenAI.response = openai_response(json.dumps(PARSE_RESULT))
        result, call = self.call(self.server.call_parse, self.cfg, "询盘正文", [IMAGE, PDF], [])
        self.assertEqual(result, PARSE_RESULT)
        self.assertEqual(call["client"]["api_key"], KEY)
        p = call["params"]
        self.assertEqual(p["model"], "gpt-6.1-sol")
        self.assertEqual(p["reasoning"], {"effort": "medium"})
        self.assertEqual(p["tools"], [{"type": "web_search"}])
        self.assertEqual(p["max_tool_calls"], 20)
        self.assertIs(p["text"]["format"]["strict"], True)
        self.assertTrue(p["instructions"])
        content = p["input"][0]["content"]
        self.assertEqual([b["type"] for b in content], ["input_image", "input_file", "input_text"])
        self.assertTrue(content[0]["image_url"].startswith("data:image/png;base64,"))
        self.assertTrue(content[1]["file_data"].startswith("data:application/pdf;base64,"))

    def test_parse_marks_nameplates(self):
        FakeOpenAI.response = openai_response(json.dumps(PARSE_RESULT))
        _, call = self.call(self.server.call_parse, self.cfg, "询盘正文", [PDF, IMAGE, IMAGE], [], [None, 1, 2])
        text = call["params"]["input"][0]["content"][-1]["text"]
        self.assertIn("第 2 个附件是铭牌图片 #1；第 3 个附件是铭牌图片 #2", text)
        self.assertIn("nameplates", text)
        _, call = self.call(self.server.call_parse, self.cfg, "询盘正文", [IMAGE], [])
        self.assertNotIn("铭牌图片", call["params"]["input"][0]["content"][-1]["text"])

    def test_nameplate_no(self):
        self.assertEqual(self.server._nameplate_no({"url": "u", "role": "nameplate", "no": "3"}), 3)
        self.assertIsNone(self.server._nameplate_no({"url": "u", "role": "attachment"}))
        self.assertIsNone(self.server._nameplate_no({"url": "u"}))
        self.assertIsNone(self.server._nameplate_no({"url": "u", "role": "nameplate", "no": "x"}))

    def test_translate_request(self):
        cfg = self.server.parse_ai_config(ai(provider="openai", translate_effort="low"))
        FakeOpenAI.response = openai_response('{"items": [{"key": "1", "text": "约翰"}]}', output=[])
        result, call = self.call(self.server.call_translate, cfg, [{"key": "1", "text": "John"}], self.server.NAME_PROMPT)
        self.assertEqual(result, {"items": [{"key": "1", "text": "约翰"}]})
        p = call["params"]
        self.assertEqual((p["model"], p["reasoning"]), ("gpt-6-luna", {"effort": "low"}))
        self.assertNotIn("tools", p)
        self.assertNotIn("max_tool_calls", p)

    def test_optional_fields_removed(self):
        with_nulls = json.loads(json.dumps(PARSE_RESULT))
        with_nulls["groups"][0]["items"][0]["replacementModel"] = None
        FakeOpenAI.response = openai_response(json.dumps(with_nulls))
        result, _ = self.call(self.server.call_parse, self.cfg, "询盘正文", [], [])
        self.assertEqual(result, PARSE_RESULT)

    def test_incomplete_and_refusal(self):
        cases = [
            (openai_response("", status="incomplete", reason="max_output_tokens"), "长度上限"),
            (openai_response("", output=[SimpleNamespace(type="message", content=[SimpleNamespace(type="refusal")])]), "拒绝"),
            (openai_response(""), "未返回结果"),
        ]
        for response, message in cases:
            FakeOpenAI.response = response
            with self.assertRaisesRegex(RuntimeError, message):
                self.call(self.server.call_translate, self.cfg, [{"key": "1", "text": "x"}])


def api_error(module, cls, status):
    """构造 SDK 的 HTTP 错误（带 Key 的错误信息，验证不会被带出）"""
    response = mock.MagicMock(status_code=status, headers={}, request=mock.MagicMock())
    return getattr(module, cls)(f"Incorrect API key provided: {KEY}", response=response, body=None)


class FakeModels:
    """models.retrieve / models.list 的桩：按模型名决定成功或失败"""
    retrieve_errors = {}
    listed = []
    list_error = None

    def __init__(self, **kwargs):
        self.models = SimpleNamespace(retrieve=self._retrieve, list=self._list)

    def _retrieve(self, model):
        if model in FakeModels.retrieve_errors:
            raise FakeModels.retrieve_errors[model]
        return SimpleNamespace(id=model)

    def _list(self):
        if FakeModels.list_error:
            raise FakeModels.list_error
        return [SimpleNamespace(id=i) for i in FakeModels.listed]


class TestAndModelsTest(unittest.TestCase):
    def setUp(self):
        self.server = load_server()
        FakeModels.retrieve_errors, FakeModels.listed, FakeModels.list_error = {}, [], None

    def patched(self):
        return mock.patch.multiple(self.server, anthropic=mock.MagicMock(wraps=self.server.anthropic, Anthropic=FakeModels),
                                   openai=mock.MagicMock(wraps=self.server.openai, OpenAI=FakeModels))

    def test_connection_all_ok(self):
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeModels):
            result = self.server.test_connection(ai())
        self.assertTrue(result["ok"])
        self.assertEqual([i["name"] for i in result["items"]],
                         ["API Key", "解析模型 claude-opus-5-5", "翻译模型 claude-haiku-5-5"])

    def test_connection_model_missing_and_invalid_key(self):
        FakeModels.retrieve_errors = {"claude-opus-5-5": api_error(self.server.openai, "NotFoundError", 404)}
        with mock.patch.object(self.server.openai, "OpenAI", FakeModels):
            result = self.server.test_connection(ai(provider="openai", parse_model="claude-opus-5-5"))
        self.assertFalse(result["ok"])
        self.assertEqual((result["items"][0]["name"], result["items"][0]["ok"]), ("API Key", True))
        self.assertFalse(result["items"][1]["ok"])
        self.assertTrue(result["items"][2]["ok"])

        FakeModels.retrieve_errors = {"claude-opus-5-5": api_error(self.server.anthropic, "AuthenticationError", 401)}
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeModels):
            result = self.server.test_connection(ai())
        self.assertEqual(result["items"], [{"name": "API Key", "ok": False, "message": "Key 无效或已失效"}])
        self.assertNotIn(KEY, json.dumps(result, ensure_ascii=False))

    def test_connection_without_key_and_subscription(self):
        self.assertEqual(self.server.test_connection(ai(api_key=""))["items"][0]["message"], "未填写 API Key")
        with mock.patch.object(self.server, "login_status", return_value={"codex": True, "claude": False}):
            self.assertTrue(self.server.test_connection(ai(mode="subscription", provider="openai", api_key=""))["ok"])
            self.assertFalse(self.server.test_connection(ai(mode="subscription", provider="claude", api_key=""))["ok"])

    def test_models_four_cases(self):
        FakeModels.listed = ["gpt-6.1-sol", "text-embedding-3", "gpt-6-luna", "tts-1", "gpt-image-2", "whisper-1"]
        with mock.patch.object(self.server.openai, "OpenAI", FakeModels):
            openai_list = self.server.list_models(ai(provider="openai"))
        self.assertEqual([m["id"] for m in openai_list["models"]], ["gpt-6-luna", "gpt-6.1-sol"])
        self.assertEqual(openai_list["source"], "provider")

        FakeModels.listed = ["claude-opus-5-5", "claude-haiku-5-5"]
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeModels):
            self.assertEqual(len(self.server.list_models(ai())["models"]), 2)

        with mock.patch.object(self.server, "codex_models", return_value=[{"id": "gpt-6.1-sol", "default": True}]):
            codex = self.server.list_models(ai(mode="subscription", provider="openai", api_key=""))
        self.assertTrue(codex["models"][0]["default"])

        for raw in (ai(mode="subscription", api_key=""), ai(provider="openai", api_key="")):
            result = self.server.list_models(raw)
            self.assertEqual(result["source"], "defaults")
            self.assertEqual(len(result["models"]), 2)

    def test_models_failure_falls_back_without_key(self):
        FakeModels.list_error = api_error(self.server.anthropic, "AuthenticationError", 401)
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeModels):
            result = self.server.list_models(ai())
        self.assertEqual(result["source"], "defaults")
        self.assertEqual(result["error"], "Key 无效或已失效")
        self.assertNotIn(KEY, json.dumps(result, ensure_ascii=False))


class RejectDrainTest(unittest.TestCase):
    """audit AI-008：拒绝请求前读完请求体。不读完就关连接，Windows 会发 RST，客户端读响应时报 WinError 10053。"""

    def handle(self, path, token, body, length=None):
        server = load_server()
        handler = server.Handler.__new__(server.Handler)
        handler.rfile, handler.wfile = io.BytesIO(body), io.BytesIO()
        handler.headers = {"Content-Length": str(len(body) if length is None else length)}
        if token is not None:
            handler.headers["X-AI-Callback-Token"] = token
        handler.path, handler.command = path, "POST"
        handler.request_version, handler.requestline = "HTTP/1.1", f"POST {path} HTTP/1.1"
        handler.close_connection = False
        with mock.patch.object(server.Handler, "log_message"):
            handler.do_POST()
        return handler

    @staticmethod
    def status(handler):
        return int(handler.wfile.getvalue().split(b" ", 2)[1])

    def test_wrong_token_consumes_body_before_401(self):
        body = json.dumps({"ai": ai(), "pad": "x" * 4000}).encode()
        for token in (None, "wrong-token"):
            handler = self.handle("/test", token, body)
            self.assertEqual(self.status(handler), 401)
            self.assertEqual(handler.rfile.tell(), len(body), "401 前读完请求体")

    def test_unknown_path_consumes_body_before_404(self):
        body = b'{"x": 1}'
        handler = self.handle("/nope", TOKEN, body)
        self.assertEqual(self.status(handler), 404)
        self.assertEqual(handler.rfile.tell(), len(body))

    def test_oversized_unauthorized_body_not_read_and_connection_closed(self):
        """未授权请求不读超大请求体（避免被用来占用服务），改为明确关闭连接"""
        handler = self.handle("/test", "wrong-token", b"x" * 16, length=10 * 1024 * 1024)
        self.assertEqual(self.status(handler), 401)
        self.assertEqual(handler.rfile.tell(), 0)
        self.assertTrue(handler.close_connection)
        self.assertIn(b"Connection: close", handler.wfile.getvalue())


class HttpTokenTest(unittest.TestCase):
    """真实起一个 HTTP 服务，验证所有接口都校验共享令牌"""

    @classmethod
    def setUpClass(cls):
        cls.server_module = load_server()
        cls.httpd = cls.server_module.ThreadingHTTPServer(("127.0.0.1", 0), cls.server_module.Handler)
        cls.base = f"http://127.0.0.1:{cls.httpd.server_address[1]}"
        threading.Thread(target=cls.httpd.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()

    def request(self, method, path, token=None, body=None):
        headers = {"Content-Type": "application/json"}
        if token is not None:
            headers["X-AI-Callback-Token"] = token
        data = json.dumps(body or {}).encode() if method == "POST" else None
        req = urllib.request.Request(self.base + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=10) as resp:
                return resp.status, json.loads(resp.read() or b"{}")
        except urllib.error.HTTPError as e:
            return e.code, None

    def test_missing_or_wrong_token_rejected(self):
        for token in (None, "wrong-token"):
            for method, path in (("POST", "/skills/inquiry-parse-and-split/run"), ("POST", "/test"),
                                 ("POST", "/models"), ("GET", "/status")):
                self.assertEqual(self.request(method, path, token)[0], 401, (token, path))

    def test_correct_token_accepted(self):
        status, body = self.request("POST", "/skills/unknown-skill/run", TOKEN, {"ai": ai()})
        self.assertEqual(status, 202)
        self.assertIn("job_id", body)
        status, body = self.request("POST", "/test", TOKEN, {"ai": ai(api_key="")})
        self.assertEqual((status, body["items"][0]["message"]), (200, "未填写 API Key"))
        with mock.patch.object(self.server_module, "login_status", return_value={"codex": False, "claude": True}):
            status, body = self.request("GET", "/status", TOKEN)
        self.assertEqual(body, {"codex": {"logged_in": False}, "claude": {"logged_in": True}})


class StartupTest(unittest.TestCase):
    def test_invalid_timeout_exits(self):
        env = {k: v for k, v in os.environ.items()}
        env["AI_ORCHESTRATOR_TIMEOUT_SECONDS"] = "0"
        result = subprocess.run([sys.executable, os.path.join(HERE, "server.py")], env=env,
                                capture_output=True, text=True, encoding="utf-8", timeout=30)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("AI_ORCHESTRATOR_TIMEOUT_SECONDS", result.stderr)


class SubscriptionRoutingTest(unittest.TestCase):
    def setUp(self):
        self.server = load_server()

    def test_codex_defaults_and_strict_schema_without_api_key(self):
        cfg = self.server.parse_ai_config(ai(mode="subscription", provider="openai", api_key=""))
        self.assertEqual((cfg.parse_model, cfg.translate_model), ("gpt-6.1-sol", "gpt-6-luna"))
        with mock.patch.object(self.server, "call_subscription", return_value={"items": [{"key": "1", "text": "PLC"}]}) as call:
            self.server.call_translate(cfg, [{"key": "1", "text": "控制器"}])
        self.assertEqual(call.call_args.args[0], "openai")
        self.assertIs(call.call_args.args[4]["additionalProperties"], False)
        self.assertFalse(call.call_args.kwargs["web_search"])
        self.assertEqual(call.call_args.kwargs["max_web_searches"], 20)

    def test_claude_subscription_defaults(self):
        cfg = self.server.parse_ai_config(ai(mode="subscription", api_key=""))
        self.assertEqual((cfg.parse_model, cfg.translate_model), ("claude-opus-5-5", "claude-haiku-5-5"))

    def test_subscription_failure_uses_existing_failed_callback(self):
        sent = []
        with mock.patch.object(self.server, "call_subscription", side_effect=RuntimeError("未登录个人订阅")), \
                mock.patch.object(self.server, "send_callback", side_effect=lambda *a, **kw: sent.append((a, kw))):
            self.server.process_translate({"input": {"items": [{"key": "1", "text": "x"}]},
                                           "ai": ai(mode="subscription", api_key="")}, "http://cb")
        self.assertEqual(sent[0][0][1], "failed")
        self.assertIn("未登录", sent[0][1]["error"])

BACKGROUND_RESULT = {
    "conclusion": {"priority": 1, "score": 4, "mainConflict": "交期", "firstAction": "24 小时内报价", "identity": "系统集成商",
                   "supply": "现货", "keyFinding": "水厂项目", "obstacle": "本地代理"},
    "reportMarkdown": "# Pacific Controls",
    "suggestions": [{"module": 1, "field": "companySize", "value": "10–50 人", "evidence": "LinkedIn 23 人", "sourceUrl": ""},
                    {"module": 4, "field": "jobRole", "value": "采购", "evidence": "LinkedIn",
                     "contact": {"name": "James Walker", "email": "", "title": "Procurement Manager"}}],
}


class UsageTest(unittest.TestCase):
    """spec ai-platform/task-orchestration「记录 AI 调用用量」"""

    def setUp(self):
        FakeClaude.calls = []
        FakeOpenAI.calls = []
        self.server = load_server()

    def sent_body(self, **kwargs):
        captured = {}

        def fake_urlopen(req, timeout=None):
            captured["body"] = json.loads(req.data)
            return mock.MagicMock(__enter__=lambda s: SimpleNamespace(status=200), __exit__=lambda s, *a: False)

        with mock.patch.object(self.server.urllib.request, "urlopen", fake_urlopen):
            self.server.send_callback("http://cb", "success", output={}, **kwargs)
        return captured["body"]

    def test_usage_accumulates_within_task_and_is_cleared(self):
        cfg = self.server.parse_ai_config(ai())
        FakeClaude.reply = '{"items": [{"key": "1", "text": "x"}]}'
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            self.server.call_translate(cfg, [{"key": "1", "text": "x"}])
            self.server.call_translate(cfg, [{"key": "1", "text": "x"}])
        body = self.sent_body()
        self.assertEqual(body["usage"], {"model": "claude-haiku-5-5", "inputTokens": 2, "outputTokens": 2, "webSearches": 0})
        self.assertNotIn(KEY, json.dumps(body))
        self.assertNotIn("usage", self.sent_body(), "取出后清空，下一个任务从零开始")

    def test_openai_counts_web_searches(self):
        cfg = self.server.parse_ai_config(ai(provider="openai"))
        FakeOpenAI.response = openai_response(json.dumps(PARSE_RESULT))
        with mock.patch.object(self.server.openai, "OpenAI", FakeOpenAI):
            self.server.call_parse(cfg, "询盘正文", [], [])
        self.assertEqual(self.sent_body()["usage"], {"model": "gpt-test", "inputTokens": 1, "outputTokens": 1, "webSearches": 1})

    def test_no_api_call_no_usage(self):
        self.assertNotIn("usage", self.sent_body())


class BackgroundTest(unittest.TestCase):
    """系统版客户背调技能：research 联网、extract 不联网，共用一个输出结构"""

    def setUp(self):
        FakeClaude.calls = []
        self.server = load_server()
        self.cfg = self.server.parse_ai_config(ai())

    def run_mode(self, input_data):
        FakeClaude.reply = json.dumps(BACKGROUND_RESULT)
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            result = self.server.call_background(self.cfg, input_data)
        return result, FakeClaude.calls[-1]["params"]

    def test_research_searches_the_web(self):
        result, p = self.run_mode({"mode": "research", "fields": [{"key": "companySize", "label": "公司规模"}],
                                   "customer": {"name": "Pacific Controls Pty Ltd"}, "inquiries": ["Need 6ES7"]})
        self.assertEqual(result, BACKGROUND_RESULT)
        self.assertEqual(p["tools"][0]["name"], "web_search")
        self.assertEqual(p["model"], "claude-opus-5-5")
        text = p["messages"][0]["content"][-1]["text"]
        self.assertIn("mode=research", text)
        self.assertIn("Pacific Controls Pty Ltd", text)
        self.assertIn("companySize", text)
        self.assertIn("烛龙客户背调", p["system"], "系统提示词来自 customer-background 技能，注明了原版来源")
        self.assertNotIn("reportlab", p["system"])

    def test_extract_does_not_search_and_returns_empty_markdown(self):
        result, p = self.run_mode({"mode": "extract", "fields": [], "reportText": "Pacific Controls 背调报告……"})
        self.assertNotIn("tools", p)
        self.assertEqual(result["reportMarkdown"], "")
        self.assertEqual(result["conclusion"]["score"], 4)
        self.assertIn("Pacific Controls 背调报告", p["messages"][0]["content"][-1]["text"])

    def test_invalid_input_fails_task(self):
        for data, message in (({"mode": "guess"}, "不支持"), ({"mode": "extract", "reportText": " "}, "没有文字")):
            sent = []
            with mock.patch.object(self.server, "send_callback",
                                   lambda url, status, output=None, error=None: sent.append((status, error))):
                self.server.process_background({"input": data, "ai": ai()}, "http://cb")
            self.assertEqual(sent[-1][0], "failed")
            self.assertIn(message, sent[-1][1])


FOLLOWUP_INPUT = {
    "customer": {"name": "ADORA", "country": "Germany"},
    "stage": {"code": "S5", "name": "报价反馈"},
    "document": {"docNo": "QT20261101003", "currencyCode": "USD", "amount": "8100.00"},
    "blockers": [{"code": "PRICE", "name": "价格"}, {"code": "DELIVERY", "name": "交期"}],
    "profileFields": [{"field": "mainProducts", "label": "主营产品"}],
}


class FollowupTest(unittest.TestCase):
    """客户跟进的两个技能：followup-extract 整理跟进内容（只保留合法取值、截断原文），followup-reply 写英文回复"""

    def setUp(self):
        FakeClaude.calls = []
        self.server = load_server()
        self.cfg = self.server.parse_ai_config(ai())

    def extract(self, reply, raw="customer: competitor quoted USD 760, refurbished. We take 10 new at USD 780, 15 days?"):
        FakeClaude.reply = json.dumps(reply)
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            result = self.server.call_followup_extract(self.cfg, {**FOLLOWUP_INPUT, "rawContent": raw})
        return result, FakeClaude.calls[-1]["params"]

    def test_extract_output_structure(self):
        result, p = self.extract({
            "summary": "客户说竞争对手报 USD 760 是翻新件，愿意按 USD 780 拿 10 台全新，要求 15 天交货",
            "situation": ["feedback", "negotiating"], "blocker": "PRICE",
            "nextActions": [{"text": "向采购确认 15 天交期", "days": 0}, {"text": "回复 USD 780 成交方案并更新报价", "days": 1}],
            "profileSuggestions": [{"field": "mainProducts", "value": "PLC 备件", "evidence": "we sell PLC spares"}]})
        self.assertEqual(result["situation"], ["feedback", "negotiating"])
        self.assertEqual(result["blocker"], "PRICE")
        self.assertEqual(len(result["nextActions"]), 2)
        self.assertEqual(result["profileSuggestions"][0]["field"], "mainProducts")
        self.assertFalse(result["truncated"])
        self.assertEqual(p["model"], "claude-haiku-5-5", "用翻译的轻量模型")
        self.assertNotIn("tools", p, "不联网")
        self.assertIn("followup-extract", p["system"])
        self.assertIn("USD 780", p["messages"][0]["content"][-1]["text"])

    def test_extract_drops_illegal_values(self):
        result, _ = self.extract({
            "summary": "x", "situation": ["no_reply", "feedback"], "blocker": "WEATHER",
            "nextActions": [{"text": "a", "days": 1}, {"text": "b", "days": 99}, {"text": "c", "days": 2}],
            "profileSuggestions": [{"field": "currency", "value": "EUR", "evidence": ""},
                                   {"field": "mainProducts", "value": " ", "evidence": ""}]})
        self.assertEqual(result["situation"], ["feedback"], "没回复不和有反馈同时出现")
        self.assertEqual(result["blocker"], "", "卡点只能取输入的卡点码")
        self.assertEqual([a["text"] for a in result["nextActions"]], ["a", "b"], "最多 2 条")
        self.assertEqual(result["nextActions"][1]["days"], 60)
        self.assertEqual(result["profileSuggestions"], [], "只能取可补的档案字段，空值去掉")

    def test_extract_stage_answers_only_for_current_stage(self):
        base = {"summary": "x", "situation": [], "blocker": "", "nextActions": [], "profileSuggestions": []}
        answers = {"receiptAnswer": 2, "useAnswer": 1, "issueTypes": ["破损", "其他"], "satisfaction": 1, "reviewStatus": 2,
                   "etaDate": "2026-11-12"}
        FakeClaude.reply = json.dumps({**base, **answers})
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            a1 = self.server.call_followup_extract(self.cfg, {**FOLLOWUP_INPUT, "stage": {"code": "A1", "board": 4},
                                                              "rawContent": "got it, one screen cracked"})
            s5 = self.server.call_followup_extract(self.cfg, {**FOLLOWUP_INPUT, "rawContent": "thanks"})
            f6 = self.server.call_followup_extract(self.cfg, {**FOLLOWUP_INPUT, "stage": {"code": "F6", "board": 3},
                                                              "rawContent": "when will it arrive?"})
        self.assertEqual(a1["receiptAnswer"], 2)
        self.assertEqual(a1["issueTypes"], ["破损", "其他"])
        self.assertEqual(a1["etaDate"], "2026-11-12")
        self.assertNotIn("useAnswer", a1, "A1 不输出 A2 的答案")
        self.assertNotIn("satisfaction", a1)
        for k in answers:
            self.assertNotIn(k, s5, "商机成交阶段不输出订单阶段的答案")
        self.assertEqual(set(f6) - set(base) - {"truncated"}, {"etaDate"})

    def test_extract_account_situation_only_on_board_5(self):
        FakeClaude.reply = json.dumps({"summary": "x", "situation": ["feedback", "no_need", "new_need"], "blocker": "",
                                       "nextActions": [], "profileSuggestions": []})
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            g4 = self.server.call_followup_extract(self.cfg, {**FOLLOWUP_INPUT, "stage": {"code": "G4", "board": 5},
                                                              "rawContent": "we need 10 more next month"})
            s5 = self.server.call_followup_extract(self.cfg, {**FOLLOWUP_INPUT, "rawContent": "we need 10 more next month"})
        self.assertEqual(g4["situation"], ["new_need"], "客户经营单选，有新需求优先")
        self.assertEqual(s5["situation"], ["feedback"], "其他板块丢掉客户经营的取值")

    def test_extract_develop_situation_only_on_board_1(self):
        FakeClaude.reply = json.dumps({"summary": "x", "situation": ["feedback", "replied", "buying_signal"], "blocker": "",
                                       "nextActions": [], "profileSuggestions": [], "signalText": "ABB ACS580 5 台，下月" + "长" * 300})
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            d6 = self.server.call_followup_extract(self.cfg, {**FOLLOWUP_INPUT, "stage": {"code": "D6", "board": 1},
                                                              "rawContent": "we need 5 pcs ACS580 next month"})
            s5 = self.server.call_followup_extract(self.cfg, {**FOLLOWUP_INPUT, "rawContent": "we need 5 pcs ACS580 next month"})
        self.assertEqual(d6["situation"], ["buying_signal"], "主动开发单选，采购信号优先")
        self.assertTrue(d6["signalText"].startswith("ABB ACS580"))
        self.assertEqual(len(d6["signalText"]), 200, "采购信号最多 200 字")
        self.assertEqual(s5["situation"], ["feedback"], "其他板块丢掉主动开发的取值")
        self.assertNotIn("signalText", s5)

    def test_extract_develop_refused_has_no_signal_text(self):
        FakeClaude.reply = json.dumps({"summary": "x", "situation": ["refused"], "blocker": "", "nextActions": [],
                                       "profileSuggestions": [], "signalText": "should be dropped"})
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            d5 = self.server.call_followup_extract(self.cfg, {**FOLLOWUP_INPUT, "stage": {"code": "D5", "board": 1},
                                                              "rawContent": "please remove us from your list"})
        self.assertEqual(d5["situation"], ["refused"])
        self.assertNotIn("signalText", d5)

    def test_extract_stage_answers_drop_bad_date(self):
        FakeClaude.reply = json.dumps({"summary": "x", "situation": [], "blocker": "", "nextActions": [], "profileSuggestions": [],
                                       "receiptAnswer": 3, "etaDate": "next week"})
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            result = self.server.call_followup_extract(self.cfg, {**FOLLOWUP_INPUT, "stage": {"code": "A1"}, "rawContent": "not yet"})
        self.assertEqual(result["receiptAnswer"], 3)
        self.assertNotIn("etaDate", result)

    def test_extract_keeps_last_20000_chars(self):
        raw = "OLD" + "x" * 20000 + "NEWEST"
        result, p = self.extract({"summary": "x", "situation": [], "blocker": "", "nextActions": [], "profileSuggestions": []},
                                 raw=raw)
        sent = json.loads(p["messages"][0]["content"][-1]["text"])
        self.assertEqual(len(sent["rawContent"]), 20000)
        self.assertTrue(sent["rawContent"].endswith("NEWEST"))
        self.assertNotIn("OLD", sent["rawContent"])
        self.assertTrue(result["truncated"])

    def test_extract_without_content_fails(self):
        sent = []
        with mock.patch.object(self.server, "send_callback",
                               lambda url, status, output=None, error=None: sent.append((status, error))):
            self.server.process_followup({"input": {**FOLLOWUP_INPUT, "rawContent": "  "}, "ai": ai()}, "http://cb",
                                         "followup-extract")
        self.assertEqual(sent[-1], ("failed", "没有需要整理的内容"))

    def test_reply_returns_english_draft(self):
        FakeClaude.reply = json.dumps({"reply": " Hi Mark, thanks for the feedback... Could you share your target price? ",
                                       "intent": "问客户目标价，不直接降价"})
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            result = self.server.call_followup_reply(self.cfg, {**FOLLOWUP_INPUT, "blocker": {
                "name": "价格", "playbook": ["先确认对方报价条件是否相同", "问目标价"]}})
        p = FakeClaude.calls[-1]["params"]
        self.assertTrue(result["reply"].startswith("Hi Mark"))
        self.assertEqual(result["intent"], "问客户目标价，不直接降价")
        self.assertIn("烛龙英文回复", p["system"], "系统提示词注明了原版来源")
        self.assertIn("问目标价", p["messages"][0]["content"][-1]["text"])

    def test_reply_passes_intent_and_order(self):
        FakeClaude.reply = json.dumps({"reply": "Your order has been shipped by DHL, tracking 1234567890.", "intent": "告诉客户运单号"})
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            self.server.call_followup_reply(self.cfg, {**FOLLOWUP_INPUT, "intent": "logistics_update",
                                                       "order": {"soNo": "SO20261025004", "etaDate": "2026-11-09"}})
        p = FakeClaude.calls[-1]["params"]
        self.assertIn("logistics_update", p["system"], "提示词里有物流更新的写法")
        self.assertIn("review_invite", p["system"], "提示词里有评价邀请的写法")
        sent = json.loads(p["messages"][0]["content"][-1]["text"])
        self.assertEqual(sent["intent"], "logistics_update")
        self.assertEqual(sent["order"]["soNo"], "SO20261025004")

    def test_reply_empty_fails(self):
        FakeClaude.reply = json.dumps({"reply": " ", "intent": ""})
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            with self.assertRaises(RuntimeError):
                self.server.call_followup_reply(self.cfg, FOLLOWUP_INPUT)


class AttachmentSourceTest(unittest.TestCase):
    """附件只接受后端签发的 http(s) 临时链接，不读本地文件（audit AI-003）"""

    def setUp(self):
        self.server = load_server()

    @staticmethod
    def only_temp_files(path, *args, **kwargs):
        """只允许读写本服务自己的下载临时目录，不读请求里给的本地路径"""
        if "zhul-parse-" not in str(path):
            raise AssertionError("不应读本地文件")
        return REAL_OPEN(path, *args, **kwargs)

    def parse(self, input_data):
        sent = []
        with mock.patch.object(self.server, "send_callback",
                               lambda url, status, output=None, error=None: sent.append((status, error))), \
                mock.patch.object(self.server, "call_parse") as call_parse, \
                mock.patch("builtins.open", side_effect=self.only_temp_files):
            self.server.process_job({"ai": ai(), "input": input_data}, "http://cb")
        return sent[-1], call_parse

    def test_non_http_links_rejected_before_reading(self):
        for url in ("file:///etc/passwd", "ftp://host/a.pdf", "/data/files/a.pdf"):
            with mock.patch.object(self.server.urllib.request, "urlopen") as urlopen:
                (status, error), call_parse = self.parse({"attachments": [{"url": url, "ext": "pdf"}]})
            self.assertEqual(status, "failed", url)
            self.assertIn("http / https", error)
            urlopen.assert_not_called()
            call_parse.assert_not_called()

    def test_legacy_local_paths_ignored(self):
        (status, error), call_parse = self.parse({"attachmentPaths": ["/etc/hosts"], "rawAttachmentPath": "/etc/hosts"})
        self.assertEqual(status, "failed")
        self.assertIn("没有正文也没有可读的附件", error)
        call_parse.assert_not_called()

    def test_https_link_still_downloaded(self):
        import pymupdf
        with pymupdf.open() as doc:
            doc.new_page().insert_text((10, 20), "MODEL: 6ES7")
            pdf = doc.tobytes()
        response = mock.MagicMock()
        # 流式下载按块读到返回空为止；mock 必须在第二次读时返回 b""，否则会无限读下去
        response.__enter__.return_value.read1.side_effect = [pdf, b""]
        with mock.patch.object(self.server.urllib.request, "urlopen", return_value=response) as urlopen:
            (status, _), call_parse = self.parse({"attachments": [{"url": "https://files.example/a?sig=1", "ext": "pdf"}]})
        self.assertEqual(status, "success")
        urlopen.assert_called_once()
        self.assertEqual(call_parse.call_args[0][2], [(pdf, ".pdf")])



QUOTE_INPUT = {
    "task": {"code": "IQ20261113001-A", "brand": "三菱", "category": "伺服与运动"},
    "items": [{"id": 101, "model": "MR-J4-70B", "originalModel": "", "quantity": 2},
              {"id": 102, "model": "HG-KR73", "originalModel": "", "quantity": 2}],
    "otherTasks": [{"id": 88, "code": "IQ20261113002-B", "brand": "三菱", "category": "变频器",
                    "models": ["FR-E740-0.75K-CHT"]}],
    "conditions": [{"code": 1, "name": "全新"}, {"code": 2, "name": "99新"}],
    "leadTimes": [{"code": 1, "name": "现货"}, {"code": 2, "name": "3-5天"}],
    "taxRates": [{"code": 13, "name": "13%"}, {"code": 1, "name": "1%"}],
}


class QuoteParseTest(unittest.TestCase):
    """粘贴报价的 AI 识别：明细 id 只留当前任务、非法值置空并标请核对、其他任务的型号只留输入里的任务"""

    def setUp(self):
        FakeClaude.calls = []
        self.server = load_server()
        self.cfg = self.server.parse_ai_config(ai())

    def parse(self, reply, raw="70B 驱动 含税 2850 现货\nFR-E740-0.75K 780"):
        FakeClaude.reply = json.dumps(reply)
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            result = self.server.call_quote_parse(self.cfg, {**QUOTE_INPUT, "rawContent": raw})
        return result, FakeClaude.calls[-1]["params"]

    def test_keeps_valid_rows_and_uses_light_model(self):
        result, p = self.parse({
            "rows": [{"itemId": 101, "raw": "70B 驱动 含税 2850 现货", "unitPrice": 2850, "taxIncluded": True, "taxRate": 13,
                      "itemCondition": 1, "leadTime": 1, "note": "", "confidence": "check"},
                     {"itemId": 102, "raw": "HG-KR73 1350", "unitPrice": 1350, "taxIncluded": False, "taxRate": 13,
                      "note": "", "confidence": "sure"}],
            "otherModels": [{"taskId": 88, "model": "FR-E740-0.75K-CHT"}], "unknownModels": ["E2E-X5ME1"], "common": ""})
        self.assertEqual(result["rows"][0], {"itemId": 101, "raw": "70B 驱动 含税 2850 现货", "unitPrice": 2850,
                                             "taxIncluded": True, "taxRate": 13, "itemCondition": 1, "leadTime": 1,
                                             "note": "", "confidence": "check"})
        self.assertNotIn("taxRate", result["rows"][1], "不含税时不带税率")
        self.assertEqual(result["rows"][1]["confidence"], "sure")
        self.assertEqual(result["otherModels"], [{"taskId": 88, "model": "FR-E740-0.75K-CHT"}])
        self.assertEqual(result["unknownModels"], ["E2E-X5ME1"])
        self.assertEqual(p["model"], "claude-haiku-5-5")
        self.assertNotIn("tools", p, "不联网")
        self.assertIn("sourcing-quote-parse", p["system"])
        self.assertIn("70B 驱动", p["messages"][0]["content"][-1]["text"])

    def test_drops_foreign_ids_and_blanks_illegal_values(self):
        result, _ = self.parse({
            "rows": [{"itemId": 999, "raw": "x", "unitPrice": 1, "taxIncluded": False, "note": "", "confidence": "sure"},
                     {"itemId": 101, "raw": "70B -5", "unitPrice": -5, "taxIncluded": True, "taxRate": 6,
                      "itemCondition": 9, "leadTime": 1, "note": "", "confidence": "sure"},
                     {"itemId": 101, "raw": "重复", "unitPrice": 1, "taxIncluded": False, "note": "", "confidence": "sure"}],
            "otherModels": [{"taskId": 77, "model": "X"}, {"taskId": 88, "model": " "}],
            "unknownModels": ["A", "A", ""], "common": "以上含税"})
        self.assertEqual(len(result["rows"]), 1, "不属于任务的 id 和重复的行都去掉")
        row = result["rows"][0]
        self.assertNotIn("unitPrice", row)
        self.assertNotIn("taxRate", row)
        self.assertNotIn("itemCondition", row)
        self.assertEqual(row["leadTime"], 1)
        self.assertEqual(row["confidence"], "check")
        self.assertEqual(result["otherModels"], [])
        self.assertEqual(result["unknownModels"], ["A"])
        self.assertEqual(result["common"], "以上含税")

    def test_truncates_raw_content(self):
        _, p = self.parse({"rows": [], "otherModels": [], "unknownModels": [], "common": ""}, raw="x" * 25000)
        self.assertNotIn("x" * 20001, p["messages"][0]["content"][-1]["text"])

    def test_empty_content_fails_without_calling_ai(self):
        with mock.patch.object(self.server, "send_callback") as cb:
            self.server.process_quote_parse({"ai": ai(), "input": {**QUOTE_INPUT, "rawContent": "  "}}, "http://cb")
        cb.assert_called_once_with("http://cb", "failed", error="没有需要识别的报价")
        self.assertEqual(FakeClaude.calls, [])

if __name__ == "__main__":
    unittest.main()


class CandidateClassifyTest(unittest.TestCase):
    def setUp(self):
        self.server = load_server()
        self.cfg = self.server.parse_ai_config(ai())

    def test_batches_of_fifty_merged_in_order(self):
        items = [{"key": str(i), "mpn": f"M{i}"} for i in range(120)]
        calls = []

        def fake(cfg, prompt, text, attachments, schema, **kw):
            batch = json.loads(text)["items"]
            calls.append((prompt, len(batch), schema, kw.get("web_search", False)))
            return {"items": [{"key": b["key"], "categoryId": 7, "brand": ""} for b in batch]}

        with mock.patch.object(self.server, "call_api", fake):
            out = self.server.call_candidate_classify(
                self.cfg, {"mode": "category", "items": items, "categories": [{"id": 7, "name": "PLC 与控制器 / PLC CPU/一体机"}]})
        self.assertEqual([c[1] for c in calls], [50, 50, 20])
        self.assertEqual([i["key"] for i in out["items"]], [str(i) for i in range(120)])
        self.assertIn("7：PLC 与控制器 / PLC CPU/一体机", calls[0][0])
        self.assertIs(calls[0][2], self.server.CANDIDATE_SCHEMA)
        self.assertFalse(calls[0][3])

    def test_brand_mode_uses_brand_prompt(self):
        prompts = []
        with mock.patch.object(self.server, "call_api",
                               lambda cfg, prompt, *a, **kw: prompts.append(prompt) or {"items": []}):
            self.server.call_candidate_classify(self.cfg, {"mode": "brand", "items": [{"key": "1"}]})
        self.assertEqual(prompts, [self.server.CANDIDATE_BRAND_PROMPT])

    def test_empty_input_and_errors_fail_without_key(self):
        sent = []
        with mock.patch.object(self.server, "send_callback",
                               lambda url, status, output=None, error=None: sent.append((status, error))):
            self.server.process_candidate({"input": {"items": []}, "ai": ai()}, "http://cb")
            with mock.patch.object(self.server, "call_candidate_classify",
                                   mock.MagicMock(side_effect=RuntimeError(f"bad key {KEY}"))):
                self.server.process_candidate({"input": {"items": [{"key": "1"}]}, "ai": ai()}, "http://cb")
        self.assertEqual(sent[0], ("failed", "没有需要解析的候选"))
        self.assertEqual(sent[1][0], "failed")
        self.assertNotIn(KEY, sent[1][1])
