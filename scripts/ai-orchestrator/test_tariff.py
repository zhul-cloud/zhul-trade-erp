"""目的国进口关税参考（openspec content-ai-batch-hs）：按 6 位编码联网查指定国家，只返回输入里的国家"""
import json
import unittest
from unittest import mock

from test_server import FakeClaude, ai, load_server

INPUT = {"hs6": "853650", "categories": ["光电传感器", "接近开关"],
         "countries": [{"code": "IN", "name": "印度"}, {"code": "IR", "name": "伊朗"}]}
RESULT = {"rates": [
    {"country": "IN", "rate": "7.5%", "note": "另加社会福利附加税", "source": "https://www.cbic.gov.in/"},
    {"country": "IR", "rate": " ", "note": "", "source": ""},
    {"country": "US", "rate": "2.7%", "note": "", "source": "https://hts.usitc.gov/"},
]}


class TariffTest(unittest.TestCase):

    def setUp(self):
        FakeClaude.calls = []
        self.server = load_server()

    def test_searches_and_keeps_only_requested_countries_with_rates(self):
        FakeClaude.reply = json.dumps(RESULT)
        cfg = self.server.parse_ai_config(ai())
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            result = self.server.call_tariff(cfg, INPUT)
        self.assertEqual([r["country"] for r in result["rates"]], ["IN"], "没查到的、不在输入里的国家不返回")
        p = FakeClaude.calls[-1]["params"]
        self.assertEqual([t["name"] for t in p["tools"]], ["web_search", "web_fetch"])
        self.assertIn("进口关税", p["system"] if isinstance(p["system"], str) else json.dumps(p["system"], ensure_ascii=False))
        self.assertIn("853650", p["messages"][0]["content"][-1]["text"])

    def test_process_rejects_bad_input(self):
        sent = []

        def callback(url, status, output=None, error=None):
            sent.append((status, output, error))

        with mock.patch.object(self.server, "send_callback", callback):
            self.server.process_tariff({"input": {**INPUT, "hs6": "85365"}, "ai": ai()}, "http://cb")
            self.server.process_tariff({"input": {**INPUT, "countries": []}, "ai": ai()}, "http://cb")
        self.assertEqual([s[0] for s in sent], ["failed", "failed"])
        self.assertIn("6 位", sent[0][2])
        self.assertIn("国家", sent[1][2])

    def test_process_success(self):
        FakeClaude.reply = json.dumps(RESULT)
        sent = []
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude), \
                mock.patch.object(self.server, "send_callback", lambda url, status, output=None, error=None:
                                  sent.append((status, output, error))):
            self.server.process_tariff({"input": INPUT, "ai": ai()}, "http://cb")
        self.assertEqual(sent[0][0], "success")
        self.assertEqual(sent[0][1]["rates"][0]["rate"], "7.5%")


if __name__ == "__main__":
    unittest.main()
