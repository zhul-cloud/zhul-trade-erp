"""AI 编排服务的单元测试：用假的 SDK 客户端拦截请求，不联网、不产生费用。

运行：pip install -r requirements.txt 后，在本目录执行 python -m unittest -v
"""
import importlib
import json
import os
import subprocess
import sys
import unittest
from types import SimpleNamespace
from unittest import mock

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

ENV_KEYS = ("AI_ORCHESTRATOR_MODE", "AI_ORCHESTRATOR_PROVIDER", "AI_ORCHESTRATOR_API_KEY", "AI_ORCHESTRATOR_PARSE_MODEL",
            "AI_ORCHESTRATOR_TRANSLATE_MODEL", "AI_ORCHESTRATOR_PARSE_EFFORT", "AI_ORCHESTRATOR_TRANSLATE_EFFORT",
            "AI_ORCHESTRATOR_MAX_WEB_SEARCHES", "ANTHROPIC_API_KEY", "OPENAI_API_KEY")
IMAGE = (b"\x89PNG fake", ".png")
PDF = (b"%PDF-1.7 fake", ".pdf")


def load_server(**env):
    """按给定环境变量重新加载 server 模块（配置在模块加载时读取）"""
    clean = {k: v for k, v in os.environ.items() if k not in ENV_KEYS}
    clean.update({"AI_ORCHESTRATOR_API_KEY": "test-key", **env})
    with mock.patch.dict(os.environ, clean, clear=True):
        sys.modules.pop("server", None)
        return importlib.import_module("server")


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
            stop_reason="end_turn", model=params["model"], _request_id="req_test",
            content=[SimpleNamespace(type="text", text=FakeClaude.reply)],
            usage=SimpleNamespace(input_tokens=1, output_tokens=1, server_tool_use=None))
        return mock.MagicMock(__enter__=lambda s: SimpleNamespace(get_final_message=lambda: message),
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


class ClaudeTest(unittest.TestCase):
    def setUp(self):
        FakeClaude.calls = []

    def run_parse(self, server):
        FakeClaude.reply = json.dumps(PARSE_RESULT)
        with mock.patch.object(server.anthropic, "Anthropic", FakeClaude):
            server.call_parse("询盘正文", [IMAGE, PDF], [])
        return FakeClaude.calls[-1]

    def run_translate(self, server):
        FakeClaude.reply = '{"items": [{"key": "1", "text": "Siemens PLC"}]}'
        with mock.patch.object(server.anthropic, "Anthropic", FakeClaude):
            server.call_translate([{"key": "1", "text": "西门子 PLC"}])
        return FakeClaude.calls[-1]

    def test_defaults(self):
        server = load_server()
        parse = self.run_parse(server)
        self.assertEqual(parse["client"]["api_key"], "test-key")
        p = parse["params"]
        self.assertEqual(p["model"], "claude-opus-5-5")
        self.assertEqual(p["output_config"]["effort"], "medium")
        self.assertEqual(p["output_config"]["format"]["type"], "json_schema")
        self.assertEqual(p["tools"], [{"type": "web_search_20260209", "name": "web_search", "max_uses": 20}])
        self.assertEqual(p["fallbacks"], "default")
        content = p["messages"][0]["content"]
        self.assertEqual([b["type"] for b in content], ["image", "document", "text"])

        t = self.run_translate(server)["params"]
        self.assertEqual(t["model"], "claude-haiku-5-5")
        self.assertEqual(t["output_config"]["effort"], "medium")
        self.assertNotIn("tools", t)
        self.assertNotIn("fallbacks", t)  # Haiku 没有服务端兜底

    def test_configured_model_and_effort(self):
        server = load_server(AI_ORCHESTRATOR_PARSE_MODEL="claude-sonnet-5-5", AI_ORCHESTRATOR_PARSE_EFFORT="high")
        p = self.run_parse(server)["params"]
        self.assertEqual((p["model"], p["output_config"]["effort"]), ("claude-sonnet-5-5", "high"))
        t = self.run_translate(server)["params"]
        self.assertEqual((t["model"], t["output_config"]["effort"]), ("claude-haiku-5-5", "medium"))

    def test_old_variable_names_ignored(self):
        server = load_server(AI_ORCHESTRATOR_API_KEY="", ANTHROPIC_API_KEY="old-key")
        with self.assertRaisesRegex(RuntimeError, "AI_ORCHESTRATOR_API_KEY"):
            server.call_translate([{"key": "1", "text": "x"}])


class CallbackTest(unittest.TestCase):
    def callback_error(self, server, side_effect):
        sent = []
        with mock.patch.object(server, "send_callback", lambda url, status, output=None, error=None: sent.append((status, error))), \
                mock.patch.object(server, "call_translate", side_effect=side_effect):
            server.process_translate({"input": {"items": [{"key": "1", "text": "x"}]}}, "http://cb")
        return sent[-1]

    def test_missing_key_reported(self):
        server = load_server(AI_ORCHESTRATOR_API_KEY="")
        sent = []
        with mock.patch.object(server, "send_callback", lambda url, status, output=None, error=None: sent.append((status, error))):
            server.process_translate({"input": {"items": [{"key": "1", "text": "x"}]}}, "http://cb")
        self.assertEqual(sent[-1][0], "failed")
        self.assertIn("AI_ORCHESTRATOR_API_KEY", sent[-1][1])

    def test_timeouts_of_both_sdks(self):
        server = load_server()
        request = mock.MagicMock()
        for error in (server.anthropic.APITimeoutError(request=request), server.openai.APITimeoutError(request=request)):
            status, message = self.callback_error(server, error)
            self.assertEqual(status, "failed")
            self.assertIn("秒未完成", message)


class OpenAISchemaTest(unittest.TestCase):
    def setUp(self):
        self.server = load_server(AI_ORCHESTRATOR_PROVIDER="openai")

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
        schema = self.server._openai_schema(self.server.OUTPUT_SCHEMA)
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

    def call(self, server, fn, *args):
        with mock.patch.object(server.openai, "OpenAI", FakeOpenAI):
            result = fn(*args)
        return result, FakeOpenAI.calls[-1]

    def test_parse_request(self):
        server = load_server(AI_ORCHESTRATOR_PROVIDER="openai")
        FakeOpenAI.response = openai_response(json.dumps(PARSE_RESULT))
        result, call = self.call(server, server.call_parse, "询盘正文", [IMAGE, PDF], [])
        self.assertEqual(result, PARSE_RESULT)
        self.assertEqual(call["client"]["api_key"], "test-key")
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
        server = load_server(AI_ORCHESTRATOR_PROVIDER="openai", AI_ORCHESTRATOR_TRANSLATE_EFFORT="low")
        FakeOpenAI.response = openai_response('{"items": [{"key": "1", "text": "约翰"}]}', output=[])
        result, call = self.call(server, server.call_translate, [{"key": "1", "text": "John"}], server.NAME_PROMPT)
        self.assertEqual(result, {"items": [{"key": "1", "text": "约翰"}]})
        p = call["params"]
        self.assertEqual((p["model"], p["reasoning"]), ("gpt-6-luna", {"effort": "low"}))
        self.assertNotIn("tools", p)
        self.assertNotIn("max_tool_calls", p)

    def test_optional_fields_removed(self):
        server = load_server(AI_ORCHESTRATOR_PROVIDER="openai")
        with_nulls = json.loads(json.dumps(PARSE_RESULT))
        with_nulls["groups"][0]["items"][0]["replacementModel"] = None
        FakeOpenAI.response = openai_response(json.dumps(with_nulls))
        result, _ = self.call(server, server.call_parse, "询盘正文", [], [])
        self.assertEqual(result, PARSE_RESULT)

    def test_incomplete_and_refusal(self):
        server = load_server(AI_ORCHESTRATOR_PROVIDER="openai")
        cases = [
            (openai_response("", status="incomplete", reason="max_output_tokens"), "长度上限"),
            (openai_response("", output=[SimpleNamespace(type="message", content=[SimpleNamespace(type="refusal")])]), "拒绝"),
            (openai_response(""), "未返回结果"),
        ]
        for response, message in cases:
            FakeOpenAI.response = response
            with self.assertRaisesRegex(RuntimeError, message):
                self.call(server, server.call_translate, [{"key": "1", "text": "x"}])


class StartupTest(unittest.TestCase):
    def start(self, **env):
        clean = {k: v for k, v in os.environ.items() if k not in ENV_KEYS}
        clean.update(env)
        return subprocess.run([sys.executable, os.path.join(HERE, "server.py")], env=clean,
                              capture_output=True, text=True, encoding="utf-8", timeout=30)

    def test_invalid_provider_exits(self):
        result = self.start(AI_ORCHESTRATOR_PROVIDER="deepseek")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("AI_ORCHESTRATOR_PROVIDER", result.stderr)
        self.assertIn("claude、openai", result.stderr)

    def test_invalid_effort_exits(self):
        result = self.start(AI_ORCHESTRATOR_TRANSLATE_EFFORT="fast")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("AI_ORCHESTRATOR_TRANSLATE_EFFORT", result.stderr)
        self.assertIn("low、medium、high、xhigh、max", result.stderr)

    def test_openai_defaults_when_models_empty(self):
        server = load_server(AI_ORCHESTRATOR_PROVIDER="openai")
        self.assertEqual((server.PARSE_MODEL, server.TRANSLATE_MODEL), ("gpt-6.1-sol", "gpt-6-luna"))


class SubscriptionRoutingTest(unittest.TestCase):
    def test_codex_uses_cli_default_and_strict_schema_without_api_key(self):
        server = load_server(AI_ORCHESTRATOR_MODE="subscription", AI_ORCHESTRATOR_PROVIDER="openai",
                             AI_ORCHESTRATOR_API_KEY="")
        self.assertEqual((server.PARSE_MODEL, server.TRANSLATE_MODEL), ("", ""))
        with mock.patch.object(server, "call_subscription", return_value={"items": [{"key": "1", "text": "PLC"}]}) as call:
            server.call_translate([{"key": "1", "text": "控制器"}])
        self.assertEqual(call.call_args.args[0], "openai")
        self.assertIs(call.call_args.args[4]["additionalProperties"], False)
        self.assertFalse(call.call_args.kwargs["web_search"])

    def test_claude_subscription_defaults(self):
        server = load_server(AI_ORCHESTRATOR_MODE="subscription")
        self.assertEqual((server.PARSE_MODEL, server.TRANSLATE_MODEL), ("opus", "haiku"))

    def test_subscription_failure_uses_existing_failed_callback(self):
        server = load_server(AI_ORCHESTRATOR_MODE="subscription")
        sent = []
        with mock.patch.object(server, "call_subscription", side_effect=RuntimeError("未登录个人订阅")), \
                mock.patch.object(server, "send_callback", side_effect=lambda *a, **kw: sent.append((a, kw))):
            server.process_translate({"input": {"items": [{"key": "1", "text": "x"}]}}, "http://cb")
        self.assertEqual(sent[0][0][1], "failed")
        self.assertIn("未登录", sent[0][1]["error"])


if __name__ == "__main__":
    unittest.main()
