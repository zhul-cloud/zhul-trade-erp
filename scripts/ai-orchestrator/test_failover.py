"""连接方式自动切换（openspec ai-connection-failover）的单元测试：用假的单次调用代替真实模型，不联网、不产生费用。

运行：在本目录执行 python -m unittest test_failover -v
"""
import json
import unittest
from unittest import mock

import httpx2 as httpx

from test_server import KEY, ai, api_error, load_server

OTHER_KEY = "sk-unit-test-other-key-9900"
RESULT = {"items": [{"key": "1", "text": "ok"}]}


def claude(**kw):
    return ai(**kw)


def gpt(**kw):
    return ai(**{"provider": "openai", "api_key": OTHER_KEY, **kw})


class FakeCalls:
    """按连接方式回放：值是异常就抛出，否则返回；记录每次调用用的方式与模型"""

    def __init__(self, server, outcomes):
        self.server, self.outcomes, self.calls = server, outcomes, []

    def __call__(self, cfg, system_prompt, text, attachments, schema, *, model, effort, web_search=False, web_fetch=False):
        name = self.server.connection_name(cfg)
        self.calls.append((name, model, effort))
        outcome = self.outcomes[name]
        if callable(outcome) and not isinstance(outcome, Exception):
            outcome = outcome()
        if isinstance(outcome, Exception):
            raise outcome
        return outcome


class FailoverReasonTest(unittest.TestCase):
    """哪些失败算连接方式本身的问题（换方式），哪些是内容问题（不换）"""

    def setUp(self):
        self.server = load_server()
        self.request = httpx.Request("POST", "https://example.invalid")

    def reason(self, e, remaining=900):
        with mock.patch.object(self.server, "remaining", return_value=remaining):
            return self.server.failover_reason(e)

    def test_status_errors(self):
        a, o = self.server.anthropic, self.server.openai
        cases = [
            (api_error(a, "AuthenticationError", 401), "auth"),
            (api_error(a, "PermissionDeniedError", 403), "auth"),
            (api_error(o, "AuthenticationError", 401), "auth"),
            (api_error(a, "RateLimitError", 429), "rate_limit"),
            (api_error(a, "InternalServerError", 500), "provider_error"),
            (api_error(o, "InternalServerError", 503), "provider_error"),
            (api_error(a, "BadRequestError", 400), None),
            (api_error(o, "UnprocessableEntityError", 422), None),
        ]
        for error, expected in cases:
            with self.subTest(error=type(error).__name__, status=error.status_code):
                self.assertEqual(self.reason(error), expected)

    def test_quota_is_recognised_from_error_text(self):
        response = httpx.Response(429, request=self.request)
        quota = self.server.openai.RateLimitError("You exceeded your current quota", response=response,
                                                  body={"code": "insufficient_quota"})
        self.assertEqual(self.reason(quota), "quota")
        response = httpx.Response(400, request=self.request)
        credit = self.server.anthropic.BadRequestError("Your credit balance is too low", response=response, body=None)
        self.assertEqual(self.reason(credit), "quota")

    def test_connection_errors_and_timeouts(self):
        a, o = self.server.anthropic, self.server.openai
        self.assertEqual(self.reason(a.APIConnectionError(request=self.request)), "provider_error")
        self.assertEqual(self.reason(o.APIConnectionError(request=self.request)), "provider_error")
        self.assertEqual(self.reason(a.APITimeoutError(request=self.request)), "timeout")
        # 剩余时间不够再调一次，换了也来不及：不切换，按原规则超时失败
        self.assertIsNone(self.reason(o.APITimeoutError(request=self.request), remaining=60))

    def test_subscription_errors(self):
        err = self.server.SubscriptionCallError
        self.assertEqual(self.reason(err("未登录", "auth")), "not_logged_in")
        self.assertEqual(self.reason(err("用量上限", "runtime")), "usage_limit")
        self.assertEqual(self.reason(err("超时", "timeout")), "timeout")
        self.assertIsNone(self.reason(err("超时", "timeout"), remaining=30))
        self.assertIsNone(self.reason(err("格式不对", "invalid")))
        self.assertIsNone(self.reason(err("搜索超限", "search_limit")))

    def test_content_and_job_errors_never_switch(self):
        for error in (RuntimeError("AI 未返回符合业务 Schema 的结果"), ValueError("bad json"),
                      self.server.JobTimeout(), self.server.ConfigError("缺少 AI 配置"),
                      self.server.SearchLimitError("超过搜索次数"), self.server.AllConnectionsFailed("x")):
            with self.subTest(error=type(error).__name__):
                self.assertIsNone(self.reason(error))


class CallApiFailoverTest(unittest.TestCase):
    def setUp(self):
        self.server = load_server()
        self.server.start_job()
        self.failover_sent = []
        patcher = mock.patch.object(self.server, "send_failover",
                                    lambda url, event: self.failover_sent.append((url, event)))
        patcher.start()
        self.addCleanup(patcher.stop)

    def chain(self, *raws, callback="http://cb"):
        return self.server.parse_chain({"connections": list(raws), "ai": raws[0]}, callback)

    def run_call(self, chain, outcomes, role="parse"):
        fake = FakeCalls(self.server, outcomes)
        with mock.patch.object(self.server, "_call_once", fake), mock.patch("builtins.print"):
            result = self.server.call_api(chain, "s", "t", [], {}, role=role)
        return result, fake.calls

    def test_auth_failure_switches_to_next_connection(self):
        chain = self.chain(claude(), gpt())
        result, calls = self.run_call(chain, {"api:claude": api_error(self.server.anthropic, "AuthenticationError", 401),
                                              "api:openai": RESULT})
        self.assertEqual(result, RESULT)
        self.assertEqual([c[0] for c in calls], ["api:claude", "api:openai"])
        self.assertEqual(chain.used, "api:openai")
        self.assertEqual([(f["connection"], f["reason"]) for f in chain.failovers], [("api:claude", "auth")])
        self.assertIsInstance(chain.failovers[0]["at"], int)
        # 切换一发生就报告后端，让这个方式立刻暂停
        self.assertEqual(self.failover_sent, [("http://cb", chain.failovers[0])])

    def test_each_connection_uses_its_own_models(self):
        chain = self.chain(claude(parse_model="c-parse", translate_model="c-tr", translate_effort="low"),
                           gpt(parse_model="o-parse", translate_model="o-tr", translate_effort="high"))
        _, calls = self.run_call(chain, {"api:claude": api_error(self.server.anthropic, "InternalServerError", 500),
                                         "api:openai": RESULT}, role="translate")
        self.assertEqual(calls, [("api:claude", "c-tr", "low"), ("api:openai", "o-tr", "high")])

    def test_content_error_does_not_switch(self):
        chain = self.chain(claude(), gpt())
        with self.assertRaisesRegex(RuntimeError, "Schema"):
            self.run_call(chain, {"api:claude": RuntimeError("AI 未返回符合业务 Schema 的结果"), "api:openai": RESULT})
        self.assertEqual(chain.failovers, [])
        self.assertEqual(self.failover_sent, [])

    def test_failed_connection_skipped_for_later_calls_and_earlier_results_kept(self):
        """分批解析：前几批用第一个方式成功，中途它额度用完，后面的批次改用下一个，已完成的批次不重做"""
        chain = self.chain(claude(), gpt())
        state = {"n": 0}

        def claude_outcome():
            state["n"] += 1
            if state["n"] <= 3:
                return {"batch": state["n"]}
            return api_error(self.server.anthropic, "RateLimitError", 429)

        outcomes = {"api:claude": claude_outcome, "api:openai": {"batch": "openai"}}
        results, all_calls = [], []
        for _ in range(5):
            result, calls = self.run_call(chain, outcomes)
            results.append(result)
            all_calls.extend(c[0] for c in calls)
        self.assertEqual(results, [{"batch": 1}, {"batch": 2}, {"batch": 3}, {"batch": "openai"}, {"batch": "openai"}])
        self.assertEqual(all_calls, ["api:claude"] * 4 + ["api:openai"] * 2)
        self.assertEqual(len(chain.failovers), 1)

    def test_api_group_before_subscription(self):
        chain = self.chain(claude(), gpt(), ai(mode="subscription", api_key=""))
        err = api_error(self.server.anthropic, "AuthenticationError", 401)
        result, calls = self.run_call(chain, {"api:claude": err, "api:openai": api_error(self.server.openai,
                                                                                         "AuthenticationError", 401),
                                              "subscription:claude": RESULT})
        self.assertEqual(result, RESULT)
        self.assertEqual([c[0] for c in calls], ["api:claude", "api:openai", "subscription:claude"])
        self.assertEqual(chain.used, "subscription:claude")

    def test_all_failed_message_lists_connections_without_keys(self):
        chain = self.chain(claude(), gpt())
        with self.assertRaises(self.server.AllConnectionsFailed) as ctx:
            self.run_call(chain, {"api:claude": api_error(self.server.anthropic, "AuthenticationError", 401),
                                  "api:openai": api_error(self.server.openai, "InternalServerError", 500)})
        message = str(ctx.exception)
        self.assertIn("AI 连接方式都不可用", message)
        self.assertIn("Claude API Key认证失败", message)
        self.assertIn("OpenAI API Key服务商故障", message)
        self.assertNotIn(KEY, message)
        self.assertNotIn(OTHER_KEY, message)

    def test_single_connection_keeps_original_error(self):
        chain = self.chain(claude())
        timeout = self.server.anthropic.APITimeoutError(request=httpx.Request("POST", "https://example.invalid"))
        with self.assertRaises(self.server.anthropic.APITimeoutError):
            self.run_call(chain, {"api:claude": timeout})
        # 仍记下切换，后端据此暂停
        self.assertEqual([f["reason"] for f in chain.failovers], ["timeout"])

    def test_plain_config_still_accepted(self):
        cfg = self.server.parse_ai_config(claude())
        result, calls = self.run_call(cfg, {"api:claude": RESULT})
        self.assertEqual(result, RESULT)
        self.assertEqual(len(calls), 1)


class ParseChainTest(unittest.TestCase):
    def setUp(self):
        self.server = load_server()

    def test_connections_keep_order(self):
        chain = self.server.parse_chain({"ai": claude(), "connections": [gpt(), claude()]})
        self.assertEqual([self.server.connection_name(c) for c in chain.configs], ["api:openai", "api:claude"])

    def test_old_request_with_only_ai(self):
        chain = self.server.parse_chain({"ai": gpt()})
        self.assertEqual([self.server.connection_name(c) for c in chain.configs], ["api:openai"])

    def test_invalid_connection_rejected(self):
        with self.assertRaises(self.server.ConfigError):
            self.server.parse_chain({"ai": claude(), "connections": [claude(), gpt(api_key="")]})

    def test_all_keys_scrubbed(self):
        keys = self.server._api_key_of({"ai": claude(), "connections": [claude(), gpt()]})
        self.assertIn(KEY, keys)
        self.assertIn(OTHER_KEY, keys)


class FakeResponse:
    status = 200

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


class CallbackBodyTest(unittest.TestCase):
    """结果回调带回实际使用的方式与全部切换；切换事件实时报告"""

    def setUp(self):
        self.server = load_server()
        self.bodies = []

        def urlopen(req, timeout=None):
            self.bodies.append(json.loads(req.data.decode("utf-8")))
            return FakeResponse()

        patcher = mock.patch.object(self.server.urllib.request, "urlopen", urlopen)
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_translate_with_failover_reports_event_and_result(self):
        fake = FakeCalls(self.server, {"api:claude": api_error(self.server.anthropic, "AuthenticationError", 401),
                                       "api:openai": RESULT})
        payload = {"input": {"items": [{"key": "1", "text": "x"}]}, "ai": claude(), "connections": [claude(), gpt()]}
        with mock.patch.object(self.server, "_call_once", fake), mock.patch("builtins.print"):
            self.server.process_translate(payload, "http://cb/1")
        failover, final = self.bodies
        self.assertEqual(failover["status"], "failover")
        self.assertEqual([(f["connection"], f["reason"]) for f in failover["failovers"]], [("api:claude", "auth")])
        self.assertEqual(final["status"], "success")
        self.assertEqual(final["connection"], "api:openai")
        self.assertEqual(final["failovers"], failover["failovers"])
        self.assertNotIn(KEY, json.dumps(self.bodies))
        # 任务结束后不再留着这个列表
        self.assertNotIn("http://cb/1", self.server._CHAINS)

    def test_no_failover_reports_only_connection(self):
        fake = FakeCalls(self.server, {"api:claude": RESULT})
        payload = {"input": {"items": [{"key": "1", "text": "x"}]}, "ai": claude(), "connections": [claude()]}
        with mock.patch.object(self.server, "_call_once", fake), mock.patch("builtins.print"):
            self.server.process_translate(payload, "http://cb/2")
        self.assertEqual(len(self.bodies), 1)
        self.assertEqual(self.bodies[0]["connection"], "api:claude")
        self.assertNotIn("failovers", self.bodies[0])

    def test_all_failed_task_fails_with_summary(self):
        fake = FakeCalls(self.server, {"api:claude": api_error(self.server.anthropic, "AuthenticationError", 401),
                                       "api:openai": api_error(self.server.openai, "AuthenticationError", 401)})
        payload = {"input": {"items": [{"key": "1", "text": "x"}]}, "ai": claude(), "connections": [claude(), gpt()]}
        with mock.patch.object(self.server, "_call_once", fake), mock.patch("builtins.print"):
            self.server.process_translate(payload, "http://cb/3")
        final = self.bodies[-1]
        self.assertEqual(final["status"], "failed")
        self.assertIn("AI 连接方式都不可用", final["error"])
        self.assertNotIn("connection", final)
        self.assertEqual(len(final["failovers"]), 2)

    def test_failover_report_failure_is_ignored(self):
        with mock.patch.object(self.server.urllib.request, "urlopen", side_effect=OSError("down")), \
                mock.patch("builtins.print"):
            self.server.send_failover("http://cb/4", {"connection": "api:claude", "reason": "auth", "at": 1})


if __name__ == "__main__":
    unittest.main()
