"""商品资料与营销内容（openspec product-content-ai）：固定版本的技能、联网核实、两份 Markdown 输出"""
import json
import unittest
from unittest import mock

from test_server import BACKGROUND_RESULT, FakeClaude, FakeOpenAI, ai, load_server, openai_response

RESULT = {"factsMarkdown": "---\nschema: 2\ntype: facts\n---\n# 资料", "marketingMarkdown": "---\nschema: 2\ntype: marketing\n---\n# 营销",
          "selfScore": 92, "summary": "已核实型号页与手册；海关编码为类目参考。"}
INPUT = {"factsFileName": "6ES7214-1AG40-0XB0.facts.md", "factsPack": "---\ntype: facts\nmodel: 6ES7214-1AG40-0XB0\n---",
         "marketingFileName": "6ES7214-1AG40-0XB0.marketing.md", "marketingPack": "---\ntype: marketing\n---\n## 阿里国际站"}


class ProductContentTest(unittest.TestCase):

    def setUp(self):
        FakeClaude.calls = []
        FakeOpenAI.calls = []
        self.server = load_server()

    def test_prompt_is_pinned_skill_with_runtime_notes(self):
        p = self.server.product_content_prompt()
        self.assertNotIn("{{", p, "原文与输出格式都已拼入")
        self.assertIn("在 ERP 里运行时的约定", p)
        self.assertIn("fouwell-product-content", p)
        self.assertIn("6bcd5f920c", p, "记录技能来源提交")
        self.assertIn("两份 Markdown 的格式", p, "输出格式原文")

    def test_claude_searches_and_opens_pages_with_raised_limit(self):
        FakeClaude.reply = json.dumps(RESULT)
        cfg = self.server.parse_ai_config(ai())
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            result = self.server.call_product_content(cfg, INPUT)
        self.assertEqual(result, RESULT)
        p = FakeClaude.calls[-1]["params"]
        self.assertEqual([t["name"] for t in p["tools"]], ["web_search", "web_fetch"])
        self.assertEqual(p["tools"][0]["max_uses"], self.server.PRODUCT_CONTENT_MIN_WEB_CALLS,
                         "公司设置 20 次不够，按最少次数")
        text = p["messages"][0]["content"][-1]["text"]
        self.assertIn("6ES7214-1AG40-0XB0.facts.md", text)
        self.assertIn("## 阿里国际站", text)

    def test_company_limit_higher_than_minimum_is_kept(self):
        FakeClaude.reply = json.dumps(RESULT)
        cfg = self.server.parse_ai_config(ai(max_web_searches=80))
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            self.server.call_product_content(cfg, INPUT)
        self.assertEqual(FakeClaude.calls[-1]["params"]["tools"][1]["max_uses"], 80)

    def test_other_skills_do_not_open_pages(self):
        FakeClaude.reply = json.dumps(BACKGROUND_RESULT)
        cfg = self.server.parse_ai_config(ai())
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            self.server.call_background(cfg, {"mode": "research", "fields": [], "customer": {}, "inquiries": []})
        self.assertEqual([t["name"] for t in FakeClaude.calls[-1]["params"]["tools"]], ["web_search"])

    def test_openai_uses_hosted_web_search(self):
        FakeOpenAI.response = openai_response(json.dumps(RESULT))
        cfg = self.server.parse_ai_config(ai(provider="openai"))
        with mock.patch.object(self.server.openai, "OpenAI", FakeOpenAI):
            self.assertEqual(self.server.call_product_content(cfg, INPUT), RESULT)
        p = FakeOpenAI.calls[-1]["params"]
        self.assertEqual(p["tools"], [{"type": "web_search"}])
        self.assertEqual(p["max_tool_calls"], self.server.PRODUCT_CONTENT_MIN_WEB_CALLS)

    def test_empty_markdown_fails(self):
        FakeClaude.reply = json.dumps({**RESULT, "marketingMarkdown": " "})
        cfg = self.server.parse_ai_config(ai())
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            with self.assertRaises(RuntimeError):
                self.server.call_product_content(cfg, INPUT)

    def test_process_success_and_invalid_input(self):
        FakeClaude.reply = json.dumps(RESULT)
        sent = []

        def callback(url, status, output=None, error=None):
            sent.append((status, output, error))

        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude), \
                mock.patch.object(self.server, "send_callback", callback), \
                mock.patch.object(self.server, "send_progress"):
            self.server.process_product_content({"input": INPUT, "ai": ai()}, "http://cb")
            self.server.process_product_content({"input": {**INPUT, "marketingPack": ""}, "ai": ai()}, "http://cb")
        self.assertEqual(sent[0][0], "success")
        self.assertEqual(sent[0][1]["selfScore"], 92)
        self.assertEqual(sent[1][0], "failed")
        self.assertIn("任务包", sent[1][2])

    def test_heartbeat_reports_progress_while_running(self):
        progress = []
        release = __import__("threading").Event()

        def slow(cfg, data):
            release.wait(2)
            return RESULT

        with mock.patch.object(self.server, "PRODUCT_CONTENT_HEARTBEAT", 0.05), \
                mock.patch.object(self.server, "call_product_content", slow), \
                mock.patch.object(self.server, "send_callback"), \
                mock.patch.object(self.server, "send_progress", lambda *a: (progress.append(a), release.set())):
            self.server.process_product_content({"input": INPUT, "ai": ai()}, "http://cb")
        self.assertTrue(progress, "生成期间报告进度，后端据此计时")
        self.assertEqual(progress[0][1:], (0, 0, None))


if __name__ == "__main__":
    unittest.main()
