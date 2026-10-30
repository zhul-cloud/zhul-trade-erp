"""AI 编排服务的单元测试：用假的 SDK 客户端拦截请求，不联网、不产生费用。

运行：pip install -r requirements.txt 后，在本目录执行 python -m unittest -v
"""
import copy
import importlib
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
        return mock.MagicMock(__enter__=lambda s: SimpleNamespace(get_final_message=lambda: message, request_id="req_test"),
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
            "inquiryScript", "searchKeywords"})
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


if __name__ == "__main__":
    unittest.main()
