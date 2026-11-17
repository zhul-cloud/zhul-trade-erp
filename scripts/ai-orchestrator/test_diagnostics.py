"""连接测试与订阅状态诊断的时长（audit AI-004）和 Key 状态判断（audit AI-006）：假 CLI、假 SDK，不联网。"""
import json
import subprocess
import threading
import unittest
from types import SimpleNamespace
from unittest import mock

import httpx2

from test_server import KEY, ai, load_server

import subscription


def both_must_run_together():
    """两个检查同时到达才放行；如果仍是一个接一个地跑，第一个会在这里等到超时"""
    barrier = threading.Barrier(2, timeout=3)

    def wait():
        try:
            barrier.wait()
        except threading.BrokenBarrierError as exc:
            raise AssertionError("两项检查没有并行执行") from exc
    return wait


class LoginStatusTest(unittest.TestCase):
    def fake_cli(self, outputs, wait=None):
        commands = []

        def run(cmd, **kwargs):
            commands.append(cmd[0])
            if wait:
                wait()
            outcome = outputs[cmd[0]]
            if isinstance(outcome, Exception):
                raise outcome
            return SimpleNamespace(returncode=0, stdout=outcome, stderr="")
        return commands, mock.patch.object(subscription.subprocess, "run", side_effect=run)

    def test_both_clis_are_checked_in_parallel(self):
        _, patch = self.fake_cli({"codex": "Logged in using ChatGPT",
                                  "claude": json.dumps({"loggedIn": True, "authMethod": "claude.ai"})},
                                 wait=both_must_run_together())
        with patch:
            self.assertEqual(subscription.login_status(5), {"codex": True, "claude": True})

    def test_timeout_is_reported_separately_from_logged_out(self):
        _, patch = self.fake_cli({"codex": subprocess.TimeoutExpired("codex", 5),
                                  "claude": json.dumps({"loggedIn": False})})
        with patch:
            self.assertEqual(subscription.login_status(5), {"codex": None, "claude": False})


class SubscriptionTestConnectionTest(unittest.TestCase):
    def setUp(self):
        self.server = load_server()

    def run_test(self, provider, outputs):
        commands = []

        def run(cmd, **kwargs):
            commands.append(cmd[0])
            outcome = outputs[cmd[0]]
            if isinstance(outcome, Exception):
                raise outcome
            return SimpleNamespace(returncode=0, stdout=outcome, stderr="")
        with mock.patch.object(subscription.subprocess, "run", side_effect=run):
            result = self.server.test_connection(ai(mode="subscription", provider=provider, api_key=""))
        return result, commands

    def test_only_selected_provider_is_checked(self):
        result, commands = self.run_test("claude", {
            "codex": subprocess.TimeoutExpired("codex", 20),
            "claude": json.dumps({"loggedIn": True, "authMethod": "claude.ai"})})
        self.assertTrue(result["ok"])
        self.assertEqual(commands, ["claude"], "测 Claude 时不等 Codex 的检查")

    def test_timeout_message_differs_from_logged_out(self):
        result, _ = self.run_test("openai", {"codex": subprocess.TimeoutExpired("codex", 20), "claude": "{}"})
        self.assertFalse(result["ok"])
        self.assertEqual(result["items"][0]["message"], "未能检查登录状态（超时或找不到 CLI），请稍后重试")
        result, _ = self.run_test("openai", {"codex": FileNotFoundError("codex"), "claude": "{}"})
        self.assertEqual(result["items"][0]["message"], "未能检查登录状态（超时或找不到 CLI），请稍后重试")
        result, _ = self.run_test("openai", {"codex": "Logged in using an API key", "claude": "{}"})
        self.assertEqual(result["items"][0]["message"], "未登录，请在容器内执行登录命令")

    def test_codex_login_rejected_by_server(self):
        """audit AI-015：本机有登录文件，服务端已拒绝（401）时测试显示登录已失效，不再显示已登录"""
        logged_in = {"codex": "Logged in using ChatGPT", "claude": "{}"}
        cases = [(None, True, "已登录"),
                 (subscription.SubscriptionCallError("x", "auth"), False, "登录已失效（服务端拒绝），请在容器内重新执行登录命令"),
                 (RuntimeError("获取 Codex 模型列表超时"), True, "已登录（暂时无法向服务端确认，稍后可再测）")]
        for error, ok, message in cases:
            with self.subTest(error=error), mock.patch.object(subscription, "codex_models", side_effect=error) as models:
                result, _ = self.run_test("openai", logged_in)
                self.assertEqual((result["ok"], result["items"][0]["message"]), (ok, message))
                models.assert_called_once()

    def test_status_card_confirms_codex_and_caches(self):
        """audit AI-015：卡片状态里 Codex 登录被服务端拒绝时显示未登录；确认结果缓存，重新登录后立即重查"""
        subscription._codex_confirmed.clear()
        logged_in = {"codex": True, "claude": True}
        with mock.patch.object(self.server, "login_status", side_effect=lambda t, providers: {p: logged_in[p] for p in providers}), \
                mock.patch.object(subscription, "codex_models",
                                  side_effect=subscription.SubscriptionCallError("x", "auth")) as models:
            self.assertEqual(self.server.subscription_status(), {"codex": False, "claude": True})
            self.assertEqual(self.server.subscription_status(), {"codex": False, "claude": True})
            self.assertEqual(models.call_count, 1, "5 分钟内不重复确认")
            with mock.patch.object(subscription, "_codex_login_changed_at", return_value=-1):
                models.side_effect = None
                self.assertEqual(self.server.subscription_status(), {"codex": True, "claude": True})
            self.assertEqual(models.call_count, 2, "登录文件变化后立即重查")
            models.side_effect = RuntimeError("超时")
            subscription._codex_confirmed.clear()
            self.assertEqual(self.server.subscription_status()["codex"], True, "确认不了时按本机登录显示")
        subscription._codex_confirmed.clear()


class ApiTestConnectionTest(unittest.TestCase):
    def setUp(self):
        self.server = load_server()
        self.request = httpx2.Request("GET", "https://unit.test")

    def status_error(self, name, code):
        return getattr(self.server.anthropic, name)(
            "err", response=httpx2.Response(code, request=self.request), body=None)

    def run_test(self, errors, wait=None):
        """errors：模型名 → 要抛的异常；没有的模型查询成功"""
        clients = []

        class Models:
            def __init__(self, **kwargs):
                clients.append(kwargs)
                self.models = SimpleNamespace(retrieve=self.retrieve)

            def retrieve(self, model):
                if wait:
                    wait()
                if model in errors:
                    raise errors[model]
                return SimpleNamespace(id=model)

        with mock.patch.object(self.server.anthropic, "Anthropic", Models):
            result = self.server.test_connection(ai(parse_model="p-model", translate_model="t-model"))
        return result, clients

    def test_models_are_checked_in_parallel_without_sdk_retries(self):
        result, clients = self.run_test({}, wait=both_must_run_together())
        self.assertTrue(result["ok"])
        self.assertTrue(all(c["max_retries"] == 0 for c in clients), "SDK 自动重试会让诊断超过后端 30 秒期限")

    def test_unreachable_provider_does_not_mark_key_valid(self):
        for error in (self.server.anthropic.APIConnectionError(request=self.request),
                      self.server.anthropic.APITimeoutError(request=self.request),
                      self.status_error("InternalServerError", 503)):
            with self.subTest(error=type(error).__name__):
                result, _ = self.run_test({"p-model": error, "t-model": error})
                key = result["items"][0]
                self.assertEqual(key["name"], "API Key")
                self.assertFalse(key["ok"], "没收到服务商的回应，不能说 Key 有效")
                self.assertTrue(key["message"].startswith("无法确认："), key["message"])
                self.assertNotIn("Error code", key["message"], "不把 SDK 的原始报错给用户看")
                self.assertFalse(result["ok"])
                self.assertNotIn(KEY, json.dumps(result, ensure_ascii=False))

    def test_key_is_valid_once_provider_answers(self):
        cases = {
            "一个模型可用、另一个连不上": {"t-model": self.server.anthropic.APIConnectionError(request=self.request)},
            "模型不存在（服务商已认证 Key）": {"p-model": self.status_error("NotFoundError", 404)},
        }
        for label, errors in cases.items():
            with self.subTest(label):
                result, _ = self.run_test(errors)
                self.assertEqual(result["items"][0], {"name": "API Key", "ok": True, "message": "有效"})
                self.assertFalse(result["ok"])

    def test_provider_5xx_and_rate_limit_have_readable_messages(self):
        result, _ = self.run_test({"p-model": self.status_error("InternalServerError", 529),
                                   "t-model": self.status_error("RateLimitError", 429)})
        self.assertEqual(result["items"][0]["message"], "无法确认：服务商暂时不可用（HTTP 529）")
        self.assertEqual([i["message"] for i in result["items"][1:]], ["服务商暂时不可用（HTTP 529）", "服务商限流，请稍后重试"])

    def test_invalid_key_is_reported_once(self):
        denied = self.status_error("AuthenticationError", 401)
        result, _ = self.run_test({"p-model": denied, "t-model": denied})
        self.assertEqual(result["items"], [{"name": "API Key", "ok": False, "message": "Key 无效或已失效"}])


if __name__ == "__main__":
    unittest.main()
