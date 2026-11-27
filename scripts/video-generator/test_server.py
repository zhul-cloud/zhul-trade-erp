"""生成接口：令牌、忙时 409、两个画幅都成功才回调成功、任一失败回调失败并带画幅名"""
import os
import tempfile
import threading
import unittest
from unittest import mock

from fastapi.testclient import TestClient
from PIL import Image

import server

JOB = {"taskId": 7, "version": 2, "title": "ITV V-1", "aspects": ["9:16", "16:9"], "voice": "en-US-GuyNeural",
       "voiceRate": 1.1, "callbackUrl": "http://backend/cb",
       "images": [{"id": 1, "url": "http://backend/img/1"}, {"id": 2, "url": "http://backend/img/2"}],
       "shots": [{"image": 0, "caption": "one two", "seconds": 4}, {"image": 1, "caption": "three", "seconds": 4},
                 {"image": 0, "caption": "four", "seconds": 4}, {"image": 1, "caption": "five", "seconds": 4}]}


class GenerateTest(unittest.TestCase):

    def setUp(self):
        self.client = TestClient(server.app)
        self.dir = tempfile.mkdtemp()
        server.WORK_ROOT = self.dir

    def tearDown(self):
        if server._busy.locked():
            server._busy.release()

    def test_token_and_busy(self):
        self.assertEqual(self.client.post("/generate", json=JOB).status_code, 401)
        server._busy.acquire()
        r = self.client.post("/generate", json=JOB, headers={"X-Video-Token": server.TOKEN})
        self.assertEqual(r.status_code, 409)

    def _png(self):
        path = os.path.join(self.dir, "p.png")
        Image.new("RGB", (900, 1200), (10, 200, 10)).save(path)
        with open(path, "rb") as f:
            return f.read()

    def _run(self, render):
        png = self._png()
        sent = []
        renders = []

        def fake_render(job, aspect, materials):
            renders.append((aspect, len(materials)))
            return render(aspect, materials)

        def fake_get(url, headers=None, timeout=None):
            self.assertEqual(headers["X-Video-Token"], server.TOKEN)
            return mock.Mock(status_code=200, content=png)

        def fake_post(url, data=None, files=None, headers=None, timeout=None):
            sent.append((data, sorted((files or {}).keys())))
            return mock.Mock(status_code=200, text="")

        with mock.patch.object(server, "render", fake_render), \
                mock.patch.object(server, "extract_cover", lambda v, d: open(d, "wb").close()), \
                mock.patch.object(server, "probe_duration", lambda v: 17.4), \
                mock.patch.object(server.requests, "get", fake_get), \
                mock.patch.object(server.requests, "post", fake_post):
            server._busy.acquire()
            server._run(JOB)
        self.assertFalse(server._busy.locked(), "结束后释放，可以接下一个任务")
        self.assertEqual(os.listdir(self.dir), ["p.png"], "临时目录已删除")
        return sent, renders

    def test_success_portrait_then_landscape(self):
        def render(aspect, materials):
            out = materials[0] + ".mp4"
            open(out, "wb").close()
            return out

        sent, renders = self._run(render)
        self.assertEqual(renders, [("9:16", 4), ("16:9", 4)], "先竖屏后横屏，每个镜头一张裁好的图")
        data, files = sent[0]
        self.assertEqual(data["status"], "success")
        self.assertEqual(data["version"], "2")
        self.assertEqual(data["portraitDuration"], "17")
        self.assertEqual(files, ["landscape", "landscapeCover", "portrait", "portraitCover"])

    def test_landscape_failure_reports_aspect(self):
        def render(aspect, materials):
            if aspect == "16:9":
                raise server.JobError("配音失败")
            out = materials[0] + ".mp4"
            open(out, "wb").close()
            return out

        sent, _ = self._run(render)
        data, files = sent[0]
        self.assertEqual(data["status"], "failed")
        self.assertEqual(data["error"], "横屏：配音失败")
        self.assertEqual(files, [])


class FailureReasonTest(unittest.TestCase):

    def test_stage_to_reason(self):
        self.assertIn("配音", server.failure_reason({"failed_stage": "audio", "error": "failed to synthesize audio"}))
        self.assertIn("图片", server.failure_reason({"failed_stage": "materials"}))
        self.assertNotIn("MoneyPrinterTurbo", server.failure_reason(None))
        self.assertNotIn("failed", server.failure_reason({"failed_stage": "pipeline", "error": "boom failed"}))


if __name__ == "__main__":
    unittest.main()
