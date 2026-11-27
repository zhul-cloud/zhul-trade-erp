"""商品短视频脚本（openspec product-video-task）：不联网，序号越界或字幕为空的镜头丢弃"""
import json
import unittest
from unittest import mock

from test_server import FakeClaude, ai, load_server

INPUT = {"product": {"brand": "Siemens", "model": "6ES7214-1AG40-0XB0", "productName": "S7-1200 CPU 1214C"},
         "factsUnverified": False,
         "images": [{"index": 1, "fileName": "front.jpg"}, {"index": 2, "fileName": "label.jpg"},
                    {"index": 3, "fileName": "side.jpg"}, {"index": 4, "fileName": "box.jpg"}],
         "rules": {"minShots": 4, "maxShots": 8, "maxTitle": 100, "maxCaption": 120,
                   "wordsPerSecond": 2.5, "minSeconds": 15, "maxSeconds": 20}}
RESULT = {"title": "Siemens 6ES7214-1AG40-0XB0 S7-1200 CPU", "shots": [
    {"image": 1, "caption": "Siemens 6ES7214-1AG40-0XB0, the S7-1200 CPU 1214C."},
    {"image": 2, "caption": "Genuine label with full order number."},
    {"image": 9, "caption": "Out of range image."},
    {"image": 3, "caption": " "},
    {"image": 4, "caption": "In stock and ready to ship worldwide."},
]}


class VideoScriptTest(unittest.TestCase):

    def setUp(self):
        FakeClaude.calls = []
        self.server = load_server()

    def test_no_web_and_drops_invalid_shots(self):
        FakeClaude.reply = json.dumps(RESULT)
        cfg = self.server.parse_ai_config(ai())
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude):
            result = self.server.call_video_script(cfg, INPUT)
        self.assertEqual([s["image"] for s in result["shots"]], [1, 2, 4])
        p = FakeClaude.calls[-1]["params"]
        self.assertFalse(p.get("tools"), "写脚本不联网")
        system = p["system"] if isinstance(p["system"], str) else json.dumps(p["system"], ensure_ascii=False)
        self.assertIn("短视频脚本", system)
        self.assertIn("6ES7214-1AG40-0XB0", p["messages"][0]["content"][-1]["text"])

    def test_process_routes_and_rejects_without_images(self):
        FakeClaude.reply = json.dumps(RESULT)
        sent = []
        with mock.patch.object(self.server.anthropic, "Anthropic", FakeClaude), \
                mock.patch.object(self.server, "send_callback", lambda url, status, output=None, error=None:
                                  sent.append((status, output, error))):
            self.server.process_video_script({"input": {**INPUT, "images": []}, "ai": ai()}, "http://cb")
            self.server.process_video_script({"input": INPUT, "ai": ai()}, "http://cb")
        self.assertEqual(sent[0][0], "failed")
        self.assertIn("图片", sent[0][2])
        self.assertEqual(sent[1][0], "success")
        self.assertEqual(sent[1][1]["title"], RESULT["title"])


if __name__ == "__main__":
    unittest.main()
