"""整任务期限（audit AI-005）与结果回调重投（audit AI-002）：假时钟、假 SDK、假回调，不联网、不等待。"""
import json
import unittest
import urllib.error
from types import SimpleNamespace
from unittest import mock

import httpx2

from test_server import KEY, PARSE_RESULT, FakeStream, ai, load_server


class Clock:
    """可控的 time.monotonic；sleep 只推进时间并记下间隔"""

    def __init__(self):
        self.now = 0.0
        self.sleeps = []

    def monotonic(self):
        return self.now

    def sleep(self, seconds):
        self.sleeps.append(seconds)
        self.now += seconds


def claude_message(stop_reason, text=None):
    content = [SimpleNamespace(type="text", text=text)] if text else []
    return SimpleNamespace(stop_reason=stop_reason, model="claude-opus-5-5", content=content,
                           usage=SimpleNamespace(input_tokens=1, output_tokens=1, server_tool_use=None))


class TimedStream(FakeStream):
    """每收到一段输出时钟前进 step 秒；chunks 段之后给出完整消息"""

    def __init__(self, message, clock, chunks, step):
        super().__init__(message)
        self.clock, self.chunks, self.step = clock, chunks, step

    def __iter__(self):
        for _ in range(self.chunks):
            self.clock.now += self.step
            yield None


class ScriptedClaude:
    """按顺序回放每一轮：(stop_reason, 段数, 每段秒数) 或一个异常"""

    def __init__(self, clock, rounds):
        self.clock, self.rounds, self.calls = clock, list(rounds), []

    def __call__(self, **kwargs):
        self.client_kwargs = kwargs
        return SimpleNamespace(beta=SimpleNamespace(messages=SimpleNamespace(stream=self.stream)))

    def stream(self, **params):
        self.calls.append(params)
        step = self.rounds.pop(0)
        if isinstance(step, Exception):
            raise step
        stop_reason, chunks, seconds = step
        text = json.dumps(PARSE_RESULT) if stop_reason == "end_turn" else None
        return mock.MagicMock(__enter__=lambda s: TimedStream(claude_message(stop_reason, text), self.clock, chunks, seconds),
                              __exit__=lambda s, *a: False)


class JobDeadlineTest(unittest.TestCase):
    def setUp(self):
        self.server = load_server()
        self.clock = Clock()
        patches = [mock.patch("time.monotonic", self.clock.monotonic),
                   mock.patch("time.sleep", self.clock.sleep),
                   mock.patch("builtins.print")]
        for p in patches:
            p.start()
            self.addCleanup(p.stop)
        self.callbacks = []
        p = mock.patch.object(self.server, "send_callback",
                              side_effect=lambda url, status, output=None, error=None: self.callbacks.append((status, error)))
        p.start()
        self.addCleanup(p.stop)

    def run_job(self, claude):
        with mock.patch.object(self.server.anthropic, "Anthropic", claude):
            self.server.process_job({"input": {"rawContent": "Siemens 6ES7 x1"}, "ai": ai()}, "http://cb")
        return self.callbacks[-1]

    def test_continuations_stop_at_job_deadline(self):
        # 每轮 250 秒都没超过单请求超时，但累计超过 900 秒：第 4 轮后不再续传
        claude = ScriptedClaude(self.clock, [("pause_turn", 1, 250)] * 5 + [("end_turn", 1, 250)])
        status, error = self.run_job(claude)
        self.assertEqual(status, "failed")
        self.assertIn("900 秒未完成", error)
        self.assertEqual(len(claude.calls), 4, "到期后不再发起第 5 轮请求")
        self.assertLessEqual(self.clock.now, 1000)

    def test_continuations_within_deadline_complete(self):
        claude = ScriptedClaude(self.clock, [("pause_turn", 1, 100)] * 5 + [("end_turn", 1, 100)])
        status, error = self.run_job(claude)
        self.assertEqual((status, error), ("success", None))
        self.assertEqual(len(claude.calls), 6)

    def test_each_round_gets_remaining_budget_and_sdk_retries_are_off(self):
        claude = ScriptedClaude(self.clock, [("pause_turn", 1, 250), ("end_turn", 1, 10)])
        self.run_job(claude)
        self.assertEqual(claude.client_kwargs["max_retries"], 0)
        self.assertEqual([c["timeout"] for c in claude.calls], [900, 650])

    def test_long_stream_is_cut_at_deadline(self):
        # 一直有输出的流不会触发单请求超时：收到每一段都核对整任务期限
        claude = ScriptedClaude(self.clock, [("end_turn", 100, 10)])
        status, error = self.run_job(claude)
        self.assertEqual(status, "failed")
        self.assertIn("秒未完成", error)
        self.assertLessEqual(self.clock.now, 910)

    def test_recoverable_errors_retry_within_deadline(self):
        request = httpx2.Request("POST", "https://unit.test")
        overloaded = self.server.anthropic.InternalServerError(
            "overloaded", response=httpx2.Response(529, request=request), body=None)
        claude = ScriptedClaude(self.clock, [self.server.anthropic.APIConnectionError(request=request), overloaded,
                                             ("end_turn", 1, 10)])
        self.assertEqual(self.run_job(claude), ("success", None))
        self.assertEqual(self.clock.sleeps, [2, 5])

    def test_auth_error_is_not_retried(self):
        request = httpx2.Request("POST", "https://unit.test")
        denied = self.server.anthropic.AuthenticationError(
            "invalid x-api-key", response=httpx2.Response(401, request=request), body=None)
        claude = ScriptedClaude(self.clock, [denied, ("end_turn", 1, 10)])
        status, _ = self.run_job(claude)
        self.assertEqual(status, "failed")
        self.assertEqual(len(claude.calls), 1)

    def test_subscription_gets_remaining_budget(self):
        self.clock.now = 0
        captured = {}

        def fake_subscription(*args, timeout, **kwargs):
            captured["timeout"] = timeout
            return PARSE_RESULT

        self.server.start_job()
        self.clock.now = 300
        with mock.patch.object(self.server, "call_subscription", side_effect=fake_subscription):
            self.server.call_api(self.server.parse_ai_config(ai(mode="subscription", api_key="")), "s", "t", [],
                                 self.server.OUTPUT_SCHEMA, model="m", effort="low")
        self.assertEqual(captured["timeout"], 600)


class CallbackRetryTest(unittest.TestCase):
    def setUp(self):
        self.server = load_server()
        self.clock = Clock()
        for p in (mock.patch("time.sleep", self.clock.sleep), mock.patch("builtins.print")):
            p.start()
            self.addCleanup(p.stop)

    def deliver(self, outcomes):
        """outcomes：每次投递的结果，异常表示失败，其他表示 HTTP 状态码"""
        attempts = []

        def fake_urlopen(req, timeout=None):
            attempts.append(json.loads(req.data))
            outcome = outcomes.pop(0)
            if isinstance(outcome, Exception):
                raise outcome
            return mock.MagicMock(__enter__=lambda s: SimpleNamespace(status=outcome), __exit__=lambda s, *a: False)

        with mock.patch.object(self.server.urllib.request, "urlopen", side_effect=fake_urlopen):
            ok = self.server.send_callback("http://cb/1", "success", output={"ok": True})
        return ok, attempts

    def http_error(self, code):
        return urllib.error.HTTPError("http://cb/1", code, "err", {}, None)

    def test_transient_failures_redeliver_same_result(self):
        ok, attempts = self.deliver([urllib.error.URLError("refused"), TimeoutError("timed out"), 200])
        self.assertTrue(ok)
        self.assertEqual(len(attempts), 3)
        self.assertTrue(all(a == attempts[0] for a in attempts), "每次重投的是同一份结果")
        self.assertEqual(self.clock.sleeps, [5, 15])

    def test_backend_5xx_retries_then_gives_up(self):
        ok, attempts = self.deliver([self.http_error(503)] * 4)
        self.assertFalse(ok)
        self.assertEqual(len(attempts), 4)
        self.assertEqual(self.clock.sleeps, [5, 15, 45])

    def test_client_errors_are_not_retried(self):
        for code in (401, 404):
            with self.subTest(code=code):
                self.clock.sleeps.clear()
                ok, attempts = self.deliver([self.http_error(code), 200])
                self.assertFalse(ok)
                self.assertEqual(len(attempts), 1)
                self.assertEqual(self.clock.sleeps, [])

    def test_model_runs_once_while_callback_is_redelivered(self):
        outcomes = [urllib.error.URLError("refused"), self.http_error(502), 200]
        attempts = []

        def fake_urlopen(req, timeout=None):
            attempts.append(req)
            outcome = outcomes.pop(0)
            if isinstance(outcome, Exception):
                raise outcome
            return mock.MagicMock(__enter__=lambda s: SimpleNamespace(status=outcome), __exit__=lambda s, *a: False)

        parse = mock.MagicMock(return_value=PARSE_RESULT)
        with mock.patch.object(self.server, "call_parse", parse), \
                mock.patch.object(self.server.urllib.request, "urlopen", side_effect=fake_urlopen):
            self.server.process_job({"input": {"rawContent": "x"}, "ai": ai()}, "http://cb/1")
        self.assertEqual(parse.call_count, 1, "重投回调不重新调用模型")
        self.assertEqual(len(attempts), 3)

    def test_log_never_contains_key(self):
        with mock.patch("builtins.print") as printed, \
                mock.patch.object(self.server.urllib.request, "urlopen", side_effect=urllib.error.URLError("refused")):
            self.server.send_callback("http://cb/1", "failed", error="x")
        self.assertNotIn(KEY, str(printed.call_args_list))


if __name__ == "__main__":
    unittest.main()
