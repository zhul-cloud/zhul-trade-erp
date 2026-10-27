"""离线测试：真实图片/PDF转换、官方SDK类型、进程超时/协议与安全隔离。"""
import base64
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock
from types import SimpleNamespace

import pymupdf
from PIL import Image
import subscription

SCHEMA = {"type": "object", "properties": {"items": {"type": "array"}}, "required": ["items"]}


class AttachmentsTest(unittest.TestCase):
    def test_transparent_images_keep_black_details_on_white_background(self):
        rgba = Image.new("RGBA", (2, 1), (0, 0, 0, 0))
        rgba.putpixel((1, 0), (0, 0, 0, 255))
        palette = Image.new("P", (2, 1), 0)
        palette.putpalette([0, 0, 0] * 256)
        palette.putpixel((1, 0), 1)
        palette.info["transparency"] = 0
        with tempfile.TemporaryDirectory() as directory:
            for original in (rgba, palette):
                with self.subTest(mode=original.mode):
                    data = io.BytesIO()
                    original.save(data, format="PNG")
                    paths = subscription.prepare_images([(data.getvalue(), ".png")], directory)
                    with Image.open(paths[0]) as result:
                        self.assertEqual(result.getpixel((0, 0)), (255, 255, 255))
                        self.assertEqual(result.getpixel((1, 0)), (0, 0, 0))

    def test_real_images_and_scanned_pdf_render(self):
        with tempfile.TemporaryDirectory() as directory:
            pix = pymupdf.Pixmap(pymupdf.csRGB, (0, 0, 12, 12), False)
            pix.clear_with(255)
            attachments = [(pix.tobytes("png"), ".png"), (pix.tobytes("jpeg"), ".jpg")]
            doc = pymupdf.open()
            doc.new_page().insert_image(pymupdf.Rect(0, 0, 12, 12), stream=pix.tobytes("png"))
            attachments.append((doc.tobytes(), ".pdf"))
            paths = subscription.prepare_images(attachments, directory)
            self.assertEqual(len(paths), 3)
            self.assertTrue(all(Path(p).read_bytes().startswith(b"\x89PNG") for p in paths))

    def test_gif_and_webp(self):
        samples = [
            ("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", ".gif"),
            ("UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA", ".webp"),
        ]
        with tempfile.TemporaryDirectory() as directory:
            for data, ext in samples:
                self.assertEqual(len(subscription.prepare_images([(base64.b64decode(data), ext)], directory)), 1)

    def test_pdf_page_and_total_limits(self):
        doc = pymupdf.open()
        for _ in range(21):
            doc.new_page()
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(RuntimeError, "20 页"):
                subscription.prepare_images([(doc.tobytes(), ".pdf")], directory)
            with self.assertRaisesRegex(RuntimeError, "25 MiB"):
                subscription.prepare_images([(b"x" * (subscription.MAX_ATTACHMENT_BYTES + 1), ".png")], directory)


class IsolationTest(unittest.TestCase):
    @unittest.skipUnless(os.name == "posix", "Docker Linux entrypoint 回归")
    def test_entrypoint_initializes_empty_tmpfs_config_directories(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            bin_dir = root / "bin"
            bin_dir.mkdir()
            fake_python = bin_dir / "python"
            fake_python.write_text("#!/bin/sh\nexit 0\n", encoding="utf-8")
            fake_python.chmod(0o700)
            env = dict(os.environ, AI_ORCHESTRATOR_MODE="subscription", AI_TASK_CALLBACK_TOKEN="test-only",
                       CODEX_HOME=str(root / "home" / "codex"), CLAUDE_CONFIG_DIR=str(root / "home" / "claude"),
                       PATH=str(bin_dir) + os.pathsep + os.environ["PATH"])
            subprocess.run(["/bin/sh", str(Path(subscription.__file__).with_name("docker-entrypoint.sh"))],
                           env=env, check=True, timeout=5)
            for key in ("CODEX_HOME", "CLAUDE_CONFIG_DIR"):
                self.assertTrue(Path(env[key]).is_dir())
                self.assertEqual(Path(env[key]).stat().st_mode & 0o777, 0o700)

    def test_env_drops_keys_tokens_and_user_home(self):
        with tempfile.TemporaryDirectory() as directory:
            with mock.patch.dict(os.environ, {"AI_ORCHESTRATOR_HOME": directory, "ANTHROPIC_API_KEY": "secret",
                                             "CLAUDE_CODE_OAUTH_TOKEN": "secret", "OPENAI_API_KEY": "secret",
                                             "CODEX_HOME": "personal", "CLAUDE_CONFIG_DIR": "personal"}):
                env = subscription.subscription_env()
            self.assertEqual(env["HOME"], directory)
            self.assertEqual(env["CODEX_HOME"], str(Path(directory) / "codex"))
            self.assertNotIn("ANTHROPIC_API_KEY", env)
            self.assertNotIn("OPENAI_API_KEY", env)
            self.assertNotIn("CLAUDE_CODE_OAUTH_TOKEN", env)

    def test_claude_has_no_file_shell_or_mcp_tools(self):
        args = subscription.claude_command({"images": ["image.png"], "web_search": True,
            "model": "opus", "effort": "medium", "schema": SCHEMA, "system_prompt": "test"})
        self.assertEqual(args[args.index("--tools") + 1], "WebSearch,WebFetch")
        for flag in ("--restricted", "--safe-mode", "--permission-prompts", "--strict-mcp-config"):
            self.assertIn(flag, args)
        self.assertNotIn("--dangerously-skip-permissions", args)

    def test_search_limit_boundary_and_duplicates(self):
        seen = set()
        subscription._check_searches(seen, "one", 2)
        self.assertEqual(len(seen), 1)
        with self.assertRaisesRegex(subscription.SubscriptionError, "search_limit"):
            subscription._check_searches(seen, "two", 2)
        with self.assertRaises(subscription.SubscriptionError):
            subscription._check_searches(set(), "one", 1)


class WorkerTest(unittest.TestCase):
    def call(self):
        return subscription.call_subscription("openai", "system", "text", [], SCHEMA, model="", effort="medium",
                                              web_search=False, max_web_searches=20, timeout=1)

    def test_invalid_json_and_schema(self):
        for output in ("not-json", "{}"):
            process = mock.Mock(returncode=0, pid=123)
            process.communicate.return_value = (output, "")
            with mock.patch.object(subscription.subprocess, "Popen", return_value=process), \
                    mock.patch.object(subscription, "stop_process"), mock.patch.object(subscription.os, "killpg", create=True):
                with self.assertRaisesRegex(RuntimeError, "约定结构"):
                    self.call()

    def test_busy_subscription_fails_without_subprocess(self):
        subscription._SLOT.acquire()
        try:
            with self.assertRaisesRegex(RuntimeError, "其他任务"):
                self.call()
        finally:
            subscription._SLOT.release()

    def test_timeout_terminates_worker(self):
        process = mock.Mock(pid=123)
        process.communicate.side_effect = subprocess.TimeoutExpired("worker", 1)
        with mock.patch.object(subscription.subprocess, "Popen", return_value=process), \
                mock.patch.object(subscription, "stop_process") as stop, \
                mock.patch.object(subscription.os, "killpg", create=True):
            with self.assertRaisesRegex(RuntimeError, "已终止"):
                self.call()
            stop.assert_called_once_with(process)

    @unittest.skipUnless(os.name == "posix", "Docker Linux 进程组回归")
    def test_wrapper_child_killed_even_when_wrapper_exits(self):
        with tempfile.TemporaryDirectory() as directory:
            marker = Path(directory) / "child-ran"
            child = "import time,pathlib; time.sleep(2); pathlib.Path(%r).write_text('bad')" % str(marker)
            wrapper = ("import subprocess,sys,json; json.load(sys.stdin); "
                       "subprocess.Popen([sys.executable,'-c',%r],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); "
                       "print('runtime'); sys.exit(1)") % child
            real_popen = subprocess.Popen
            def fake_worker(args, **kwargs):
                return real_popen([sys.executable, "-c", wrapper], **kwargs)
            # 经公开入口的 finally 清理，不能直接调用清理函数掩盖回归。
            with mock.patch.object(subscription.subprocess, "Popen", side_effect=fake_worker):
                with self.assertRaisesRegex(RuntimeError, "调用失败"):
                    self.call()
            time.sleep(2.5)
            self.assertFalse(marker.exists())

    @unittest.skipUnless(os.name == "posix", "Docker Linux 进程组回归")
    def test_real_timeout_kills_worker_and_child(self):
        with tempfile.TemporaryDirectory() as directory:
            marker = Path(directory) / "child-ran"
            child = "import time,pathlib; time.sleep(2); pathlib.Path(%r).write_text('bad')" % str(marker)
            wrapper = ("import subprocess,sys,json,time; json.load(sys.stdin); "
                       "subprocess.Popen([sys.executable,'-c',%r],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL); "
                       "time.sleep(10)") % child
            real_popen = subprocess.Popen
            def fake_worker(args, **kwargs):
                return real_popen([sys.executable, "-c", wrapper], **kwargs)
            with mock.patch.object(subscription.subprocess, "Popen", side_effect=fake_worker):
                with self.assertRaisesRegex(RuntimeError, "已终止"):
                    self.call()
            time.sleep(2)
            self.assertFalse(marker.exists())


class ProtocolTest(unittest.TestCase):
    def request(self):
        return {"images": [], "text": "test", "model": "opus", "effort": "medium", "schema": SCHEMA,
                "system_prompt": "test", "web_search": True, "max_web_searches": 3}

    def test_claude_stream_image_and_structured_result(self):
        with tempfile.TemporaryDirectory() as directory:
            image = Path(directory) / "image.png"
            image.write_bytes(b"image")
            request = self.request()
            request["images"] = [str(image)]
            stream = [{"type": "stream_event", "event": {"type": "content_block_start", "content_block": {
                        "type": "tool_use", "id": "search1", "name": "WebSearch"}}},
                      {"type": "result", "subtype": "success", "is_error": False, "structured_output": {"items": []}}]
            process = mock.Mock(stdin=io.StringIO(), stdout=[json.dumps(e) for e in stream])
            process.wait.return_value = 0
            process.poll.return_value = 0
            # 记录 close 前 stdin，避免 mock 掩盖图片未传入。
            sent = []
            process.stdin.close = lambda: sent.append(process.stdin.getvalue())
            with mock.patch.object(subscription.subprocess, "run", return_value=SimpleNamespace(
                    returncode=0, stdout='{"authMethod":"claude.ai"}')), \
                    mock.patch.object(subscription.subprocess, "Popen", return_value=process):
                self.assertEqual(subscription.run_claude(request), {"items": []})
            content = json.loads(sent[0])["message"]["content"]
            self.assertEqual(content[0]["type"], "image")
            self.assertEqual(base64.b64decode(content[0]["source"]["data"]), b"image")

    def test_codex_real_typed_notifications_and_duplicate_search(self):
        from openai_codex.models import Notification
        from openai_codex.generated.v2_all import ItemStartedNotification, ItemCompletedNotification, TurnCompletedNotification
        search = {"type": "webSearch", "id": "search1", "query": "model", "action": {"type": "search", "query": "model", "queries": None}}
        message = {"type": "agentMessage", "id": "answer1", "text": '{"items":[]}', "phase": "final_answer"}
        events = [Notification("item/started", ItemStartedNotification.model_validate({
                    "threadId": "thread", "turnId": "turn", "startedAtMs": 1, "item": search})),
                  Notification("item/completed", ItemCompletedNotification.model_validate({
                    "threadId": "thread", "turnId": "turn", "completedAtMs": 2, "item": search})),
                  Notification("item/completed", ItemCompletedNotification.model_validate({
                    "threadId": "thread", "turnId": "turn", "completedAtMs": 3, "item": message})),
                  Notification("turn/completed", TurnCompletedNotification.model_validate({
                    "threadId": "thread", "turn": {"id": "turn", "status": "completed", "items": []}}))]
        client = mock.MagicMock()
        client.account.return_value.account = SimpleNamespace(root=SimpleNamespace(type="chatgpt"))
        client.thread_start.return_value.turn.return_value.stream.return_value = iter(events)
        with mock.patch("openai_codex.Codex", return_value=mock.MagicMock(__enter__=lambda s: client)):
            request = self.request()
            request["max_web_searches"] = 2
            self.assertEqual(subscription.run_codex(request), {"items": []})
            client.thread_start.return_value.turn.return_value.stream.return_value = iter(events[:-1])
            with self.assertRaisesRegex(subscription.SubscriptionError, "invalid"):
                subscription.run_codex(request)


if __name__ == "__main__":
    unittest.main()
