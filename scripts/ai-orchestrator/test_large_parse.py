"""询盘大附件的预处理与分批解析（OpenSpec inquiry-large-attachments 任务 5.1–5.7）"""
import csv
import http.server
import io
import json
import os
import re
import tempfile
import threading
import time
import tracemalloc
import unittest
from unittest import mock

import openpyxl
import pymupdf
from PIL import Image

import large_parse as lp
from test_server import ai, load_server

GROUP = {"brand": "西门子", "category": "PLC", "inquiryTemplate": "", "emailTemplateCn": "", "emailTemplateEn": ""}


def text_pdf(path, pages):
    """pages 为每页文字，空字符串表示扫描页（没有文字层）"""
    with pymupdf.open() as doc:
        for text in pages:
            page = doc.new_page()
            if text:
                page.insert_textbox(page.rect + (36, 36, -36, -36), text, fontsize=9)
        doc.save(path)


# ---------------------------------------------------------------- 5.1 流式下载

class DownloadTest(unittest.TestCase):
    SIZE = 50 * 1024 * 1024

    @classmethod
    def setUpClass(cls):
        size = cls.SIZE

        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(200)
                self.send_header("Content-Length", str(size))
                self.end_headers()
                block = b"x" * (1024 * 1024)
                for _ in range(size // len(block)):
                    self.wfile.write(block)

            def log_message(self, *args):
                pass

        cls.httpd = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        threading.Thread(target=cls.httpd.serve_forever, daemon=True).start()
        cls.url = f"http://127.0.0.1:{cls.httpd.server_address[1]}/a.pdf"

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()
        cls.httpd.server_close()

    def test_streams_to_disk_without_holding_whole_file(self):
        with tempfile.TemporaryDirectory() as work:
            tracemalloc.start()
            try:
                path = lp.download({"url": self.url, "ext": "pdf"}, work, lambda: 60)
                peak = tracemalloc.get_traced_memory()[1]
            finally:
                tracemalloc.stop()
            self.assertEqual(os.path.getsize(path), self.SIZE)
            self.assertLess(peak, 8 * 1024 * 1024, "下载时内存不应随文件大小增长")

    def test_over_limit_fails_and_leaves_nothing(self):
        with tempfile.TemporaryDirectory() as work, mock.patch.object(lp, "MAX_FILE_BYTES", 10 * 1024 * 1024):
            with self.assertRaisesRegex(ValueError, "500MB"):
                lp.download({"url": self.url, "ext": "pdf"}, work, lambda: 60)
            self.assertEqual(os.listdir(work), [])

    def test_non_http_rejected(self):
        with tempfile.TemporaryDirectory() as work:
            with self.assertRaisesRegex(ValueError, "http / https"):
                lp.download({"url": "file:///etc/passwd", "ext": "pdf"}, work, lambda: 60)


class Expired(Exception):
    """测试用的整任务到期"""


def deadline_in(seconds):
    """返回 deadline_left：剩余秒数，到期抛 Expired；calls 记录检查次数"""
    end = time.monotonic() + seconds
    calls = []

    def left():
        calls.append(1)
        rest = end - time.monotonic()
        if rest <= 0:
            raise Expired()
        return rest
    return left, calls


class SlowDownloadTest(unittest.TestCase):
    """audit AI-005：整任务期限在每次读取前检查，单次等待也不超过剩余期限"""

    @classmethod
    def setUpClass(cls):
        class Handler(http.server.BaseHTTPRequestHandler):
            def do_GET(self):
                size = 200
                self.send_response(200)
                self.send_header("Content-Length", str(size))
                self.end_headers()
                if self.path.endswith("stall.pdf"):
                    time.sleep(3)  # 有响应头、迟迟没有数据
                    return
                delay = 0.001 if self.path.endswith("fast.pdf") else 0.05
                try:
                    for _ in range(size):  # 每次只来 1 个字节：每次读取都很快有数据，但总时长 10 秒
                        self.wfile.write(b"x")
                        self.wfile.flush()
                        time.sleep(delay)
                except OSError:
                    pass

            def log_message(self, *args):
                pass

        cls.httpd = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.httpd.daemon_threads = True
        threading.Thread(target=cls.httpd.serve_forever, daemon=True).start()
        cls.base = f"http://127.0.0.1:{cls.httpd.server_address[1]}/"

    @classmethod
    def tearDownClass(cls):
        cls.httpd.shutdown()
        cls.httpd.server_close()

    def test_trickling_download_stops_at_job_deadline(self):
        left, calls = deadline_in(0.5)
        with tempfile.TemporaryDirectory() as work:
            started = time.monotonic()
            with self.assertRaises(Expired):
                lp.download({"url": self.base + "slow.pdf", "ext": "pdf"}, work, left)
            self.assertLess(time.monotonic() - started, 3, "不能等整个慢流读完")
            self.assertGreater(len(calls), 2, "每次读取前都检查期限")
            self.assertEqual(os.listdir(work), [], "未完成的文件已删除")

    def test_stalled_read_waits_no_longer_than_deadline(self):
        left, _ = deadline_in(0.5)
        with tempfile.TemporaryDirectory() as work:
            started = time.monotonic()
            with self.assertRaises(Expired, msg="等待被期限截断时报告任务超时，不当作附件读不了"):
                lp.download({"url": self.base + "stall.pdf", "ext": "pdf"}, work, left)
            self.assertLess(time.monotonic() - started, 2)
            self.assertEqual(os.listdir(work), [])

    def test_download_within_deadline_completes(self):
        left, _ = deadline_in(30)
        with tempfile.TemporaryDirectory() as work:
            path = lp.download({"url": self.base + "fast.pdf", "ext": "pdf"}, work, left)
            self.assertEqual(os.path.getsize(path), 200)


# ---------------------------------------------------------------- 5.2 预处理

class PreprocessTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)

    def path(self, name):
        return os.path.join(self.dir.name, name)

    def test_mixed_pdf_text_pages_and_scanned_pages(self):
        p = self.path("清单.pdf")
        text_pdf(p, ["6ES7 214-1AG40-0XB0 数量 2 " * 20, "", "6AV2 123-2GB03-0AX0 " * 20])
        units, unparsed = lp.preprocess(p, ".pdf", "清单.pdf")
        self.assertEqual([u.kind for u in units], ["text", "image", "text"])
        self.assertEqual(units[1].label, "《清单.pdf》第 2 页")
        self.assertIn("《清单.pdf》第 1 页", units[0].text)
        with Image.open(io.BytesIO(units[1].image)) as img:
            self.assertLessEqual(max(img.size), lp.MAX_IMAGE_EDGE)
        self.assertEqual(unparsed, [])

    def test_encrypted_pdf_reported(self):
        p = self.path("加密.pdf")
        with pymupdf.open() as doc:
            doc.new_page()
            doc.save(p, encryption=pymupdf.PDF_ENCRYPT_AES_256, user_pw="u", owner_pw="o")
        self.assertEqual(lp.preprocess(p, ".pdf", "加密.pdf"),
                         ([], [{"file": "加密.pdf", "range": "", "reason": "已加密，无法读取"}]))

    def test_corrupt_pdf_reported(self):
        p = self.path("坏.pdf")
        with open(p, "wb") as fh:
            fh.write(b"not a pdf at all")
        units, unparsed = lp.preprocess(p, ".pdf", "坏.pdf")
        self.assertEqual(units, [])
        self.assertEqual(unparsed[0]["file"], "坏.pdf")

    def test_broken_page_only_affects_that_page(self):
        p = self.path("清单.pdf")
        text_pdf(p, ["A" * 300, "B" * 300, "C" * 300])
        real = pymupdf.Page.get_text
        calls = []

        def flaky(page, *args, **kwargs):
            calls.append(page.number)
            if page.number == 1:
                raise RuntimeError("broken page")
            return real(page, *args, **kwargs)

        with mock.patch.object(pymupdf.Page, "get_text", flaky):
            units, unparsed = lp.preprocess(p, ".pdf", "清单.pdf")
        self.assertEqual([u.label for u in units], ["《清单.pdf》第 1 页", "《清单.pdf》第 3 页"])
        self.assertEqual(unparsed, [{"file": "清单.pdf", "range": "第 2 页", "reason": "页面损坏，无法读取"}])

    def test_large_image_is_shrunk(self):
        p = self.path("大图.png")
        Image.frombytes("RGB", (5000, 4000), os.urandom(5000 * 4000 * 3)).save(p)
        units, unparsed = lp.preprocess(p, ".png", "大图.png", nameplate_no=2)
        self.assertEqual(len(units), 1)
        self.assertEqual(units[0].nameplate_no, 2)
        with Image.open(io.BytesIO(units[0].image)) as img:
            self.assertEqual(max(img.size), lp.MAX_IMAGE_EDGE)
        self.assertLess(len(units[0].image), os.path.getsize(p))

    def test_broken_image_reported(self):
        p = self.path("坏.jpg")
        with open(p, "wb") as fh:
            fh.write(b"\xff\xd8 broken")
        self.assertEqual(lp.preprocess(p, ".jpg", "坏.jpg"),
                         ([], [{"file": "坏.jpg", "range": "", "reason": "图片无法读取"}]))

    def test_xlsx_multi_sheet_split_by_rows_with_location(self):
        p = self.path("清单.xlsx")
        wb = openpyxl.Workbook()
        ws = wb.active
        ws.title = "PLC"
        ws.append(["型号", "数量"])
        for i in range(250):
            ws.append([f"6ES7-{i}", i])
        ws2 = wb.create_sheet("HMI")
        ws2.append(["6AV2", 3])
        wb.save(p)
        units, unparsed = lp.preprocess(p, ".xlsx", "清单.xlsx")
        self.assertEqual([u.label for u in units], [
            "《清单.xlsx》工作表「PLC」第 1–200 行", "《清单.xlsx》工作表「PLC」第 201–251 行",
            "《清单.xlsx》工作表「HMI」第 1–1 行"])
        self.assertIn("6ES7-249\t249", units[1].text)
        self.assertEqual(unparsed, [])

    def test_csv_in_gbk(self):
        p = self.path("清单.csv")
        with open(p, "w", encoding="gbk", newline="") as fh:
            csv.writer(fh).writerows([["型号", "数量"], ["西门子 6ES7", "2"]])
        units, _ = lp.preprocess(p, ".csv", "清单.csv")
        self.assertEqual(len(units), 1)
        self.assertIn("西门子 6ES7\t2", units[0].text)
        self.assertIn("第 1–2 行", units[0].label)

    def test_sheet_text_matches_backend_format(self):
        p = self.path("清单.xlsx")
        wb = openpyxl.Workbook()
        wb.active.title = "Models"
        wb.active.append(["6ES7", 1])
        wb.save(p)
        self.assertEqual(lp.sheet_text(p, ".xlsx"), "Sheet: Models\n6ES7\t1")

    def test_deadline_checked_per_pdf_page_and_sheet_rows(self):
        p = self.path("清单.pdf")
        text_pdf(p, ["6ES7 214-1AG40-0XB0 " * 20] * 3)
        calls = []
        units, _ = lp.preprocess(p, ".pdf", "清单.pdf", check=lambda: calls.append(1))
        self.assertEqual((len(units), len(calls)), (3, 3), "每页之前检查一次")

        x = self.path("清单.csv")
        with open(x, "w", encoding="utf-8", newline="") as fh:
            csv.writer(fh).writerows([[f"6ES7-{i}", "1"] for i in range(450)])
        calls = []
        units, _ = lp.preprocess(x, ".csv", "清单.csv", check=lambda: calls.append(1))
        self.assertEqual((len(units), len(calls)), (3, 3), "每 200 行一组，每组之前检查一次")

    def test_expired_deadline_is_not_reported_as_broken_file(self):
        p = self.path("清单.pdf")
        text_pdf(p, ["6ES7 214-1AG40-0XB0 " * 20] * 3)
        x = self.path("清单.csv")
        with open(x, "w", encoding="utf-8", newline="") as fh:
            csv.writer(fh).writerows([[f"6ES7-{i}", "1"] for i in range(450)])
        for path, ext in ((p, ".pdf"), (x, ".csv")):
            calls = []

            def check():
                calls.append(1)
                if len(calls) > 1:
                    raise Expired()
            with self.assertRaises(Expired, msg=ext):
                lp.preprocess(path, ext, os.path.basename(path), check=check)

    def test_unsupported_type(self):
        self.assertEqual(lp.preprocess(self.path("a.zip"), ".zip", "a.zip"),
                         ([], [{"file": "a.zip", "range": "", "reason": "不支持的文件类型"}]))


# ---------------------------------------------------------------- 5.3 / 5.6 分批

class PackTest(unittest.TestCase):
    def images(self, n, size=1000):
        return [lp.Unit("image", f"第 {i + 1} 页", image=b"x" * size) for i in range(n)]

    def test_image_count_boundary(self):
        n = lp.BATCH_MAX_IMAGES
        self.assertEqual([len(b) for b in lp.pack(self.images(n))], [n])
        self.assertEqual([len(b) for b in lp.pack(self.images(n + 1))], [n, 1])

    def test_text_boundary(self):
        half = lp.BATCH_MAX_CHARS // 2
        exact = [lp.Unit("text", "a", text="x" * half), lp.Unit("text", "b", text="x" * half)]
        self.assertEqual(len(lp.pack(exact)), 1)
        over = exact + [lp.Unit("text", "c", text="x")]
        self.assertEqual([len(b) for b in lp.pack(over)], [2, 1])

    def test_image_bytes_boundary(self):
        units = self.images(3, size=lp.BATCH_MAX_IMAGE_BYTES // 2)
        self.assertEqual([len(b) for b in lp.pack(units)], [2, 1])

    def test_oversized_unit_is_its_own_batch(self):
        units = [lp.Unit("text", "a", text="x"), lp.Unit("text", "big", text="x" * (lp.BATCH_MAX_CHARS + 1))]
        self.assertEqual([len(b) for b in lp.pack(units)], [1, 1])

    def test_300_scanned_pages_fit_subscription_limit(self):
        batches = lp.pack(self.images(300, size=200_000))
        self.assertEqual(len(batches), -(-300 // lp.BATCH_MAX_IMAGES))
        for b in batches:
            self.assertLessEqual(len(b), 20)
            self.assertLessEqual(sum(len(u.image) for u in b), 25 * 1024 * 1024)


# ---------------------------------------------------------------- 5.4 合并

def item(model, qty, unit="pcs", source="", **extra):
    return dict({"originalModel": model, "confirmedModel": model, "confidence": 1, "quantity": qty, "unit": unit,
                 "source": source}, **extra)


class MergeTest(unittest.TestCase):
    def test_duplicates_across_batches_are_summed_with_locations(self):
        items, _ = lp.merge([
            ([dict(GROUP, items=[item("6ES7 214", 2, source="《a.pdf》第 1 页")])], ["《a.pdf》第 1 页"]),
            ([dict(GROUP, items=[item("6es7214", 3, source="《b.xlsx》第 5 行")])], ["《b.xlsx》第 1–200 行"]),
        ])
        self.assertEqual(len(items), 1)
        self.assertEqual(items[0]["quantity"], 5)
        self.assertEqual(items[0]["correctionNote"], "在 《a.pdf》第 1 页、《b.xlsx》第 5 行 出现，数量已相加（2 + 3）")

    def test_different_units_not_summed(self):
        items, _ = lp.merge([
            ([dict(GROUP, items=[item("6ES7", 2, "pcs", "第 1 页")])], ["第 1 页"]),
            ([dict(GROUP, items=[item("6ES7", 1, "box", "第 9 页")])], ["第 9 页"]),
        ])
        self.assertEqual(items[0]["quantity"], 2)
        self.assertEqual(items[0]["confidence"], lp.CONFIDENCE_PENDING_VERIFY)
        self.assertIn("单位不同未相加", items[0]["correctionNote"])

    def test_missing_source_falls_back_to_batch_labels(self):
        items, _ = lp.merge([([dict(GROUP, items=[item("6ES7", 1)])], ["《a.pdf》第 1 页", "《a.pdf》第 2 页"])])
        self.assertEqual(items[0]["correctionNote"], "出现在 《a.pdf》第 1 页；《a.pdf》第 2 页")

    def test_regroup_removes_internal_fields(self):
        items, meta = lp.merge([([dict(GROUP, items=[item("6ES7", 1, source="p1")])], ["p1"])])
        groups = lp.regroup(items, meta)
        self.assertEqual(groups[0]["brand"], "西门子")
        self.assertFalse([k for k in groups[0]["items"][0] if k.startswith("_") or k == "source"])


# ---------------------------------------------------------------- 5.4 / 5.5 / 5.7 分批流程

class FakeModel:
    """假模型：提取批从文字里读「ITEM|位置|型号|数量|单位」，核验批原样返回并补描述"""

    def __init__(self, server, fail_extract=(), fail_verify=(), delay=0.0, usage=None):
        self.server = server
        self.usage = usage
        self.fail_extract = set(fail_extract)
        self.fail_verify = set(fail_verify)
        self.delay = delay
        self.extract_calls, self.verify_sizes = [], []
        self.lock = threading.Lock()

    def __call__(self, cfg, system_prompt, text, attachments, schema, *, model, effort, web_search):
        self.server.remaining()  # 与真实调用一样，期限已到时不再发起请求
        if self.delay:
            time.sleep(self.delay)
        if self.usage:  # 与真实调用一样，用量记在发起调用的工作线程
            self.server._record_usage("m1", self.usage[0], self.usage[1], 0)
        if not web_search:
            no = int(re.search(r"第 (\d+) / \d+ 部分", text).group(1))
            with self.lock:
                self.extract_calls.append(no)
            if no in self.fail_extract:
                raise RuntimeError("模型返回异常")
            items = [item(m, int(q), u, where) for where, m, q, u in re.findall(r"ITEM\|(.+?)\|(.+?)\|(\d+)\|(\w+)", text)]
            return {"groups": [dict(GROUP, items=items)]}
        chunk = json.loads(text.split("\n", 1)[1])
        with self.lock:
            self.verify_sizes.append(len(chunk))
            no = len(self.verify_sizes)
        if no in self.fail_verify:
            raise self.server.SearchLimitError("联网搜索操作达到订阅模式上限")
        return {"groups": [dict(GROUP, items=[dict(it, confidence=1, description="已核验") for it in chunk])]}


def text_unit(label, *models):
    return lp.Unit("text", label, text=label + "：\n" + "\n".join(f"ITEM|{label}|{m}|{q}|pcs" for m, q in models))


class ParseLargeTest(unittest.TestCase):
    def setUp(self):
        self.server = load_server()
        self.progress = []
        self.sent = []
        self.usages = []
        self.work_dirs = []
        self.read_delay = 0

    def run_job(self, files, model, raw="", refs=None):
        """files: {附件名: 下载结果}；下载结果为 (units, unparsed) 或异常"""
        refs = refs or [{"url": f"https://files.example/{n}", "ext": "pdf", "name": n, "size": 100 * 1024 * 1024}
                        for n in files]

        def download(ref, directory, deadline_left):
            self.work_dirs.append(directory)
            got = files[ref["name"]]
            if isinstance(got, Exception):
                raise got
            path = os.path.join(directory, ref["name"])
            open(path, "wb").close()
            return path

        def preprocess(path, ext, name, nameplate_no=None, check=None):
            self.assertTrue(os.path.exists(path))
            if check:
                check()
            if self.read_delay:
                time.sleep(self.read_delay)
            return files[name]

        def send_callback(url, status, output=None, error=None):
            self.sent.append((status, output, error))
            self.usages.append(self.server._take_usage())

        with mock.patch.object(lp, "download", download), mock.patch.object(lp, "preprocess", preprocess), \
                mock.patch.object(self.server, "call_api", model), \
                mock.patch.object(self.server, "send_progress", lambda url, d, t, st: self.progress.append((d, t, st))), \
                mock.patch.object(self.server, "send_callback", send_callback):
            self.server.process_job({"ai": ai(), "input": {"rawContent": raw, "attachments": refs}}, "http://cb")
        self.assertEqual(len(self.sent), 1)
        return self.sent[0]

    def models(self, output):
        return {it["confirmedModel"]: it for g in output["groups"] for it in g["items"]}

    def test_duplicates_merged_and_120_models_verified_in_batches(self):
        files = {
            "a.pdf": ([text_unit(f"《a.pdf》第 {p} 页", *[(f"M{p * 10 + i}", 1) for i in range(10)])
                       for p in range(1, 7)] + [text_unit("《a.pdf》第 7 页", ("M11", 4))], []),
            "b.xlsx": ([text_unit(f"《b.xlsx》第 {r} 行", (f"N{r}", 2)) for r in range(1, 61)], []),
        }
        with mock.patch.object(lp, "BATCH_MAX_CHARS", 400):
            model = FakeModel(self.server)
            status, output, error = self.run_job(files, model)
        self.assertEqual(status, "success", error)
        got = self.models(output)
        self.assertEqual(len(got), 120)
        self.assertEqual(got["M11"]["quantity"], 5)
        self.assertIn("《a.pdf》第 1 页、《a.pdf》第 7 页", got["M11"]["correctionNote"])
        self.assertEqual(len(model.verify_sizes), -(-120 // lp.VERIFY_BATCH))
        self.assertTrue(all(n <= lp.VERIFY_BATCH for n in model.verify_sizes))
        self.assertEqual(sum(model.verify_sizes), 120)
        self.assertTrue(all(it["description"] == "已核验" for it in got.values()))
        self.assertNotIn("unparsed", output)
        for d in self.work_dirs:
            self.assertFalse(os.path.exists(d), "任务结束后临时目录应删除")

    def test_partial_failures_do_not_fail_the_task(self):
        files = {
            "a.pdf": ([text_unit("《a.pdf》第 1 页", ("6ES7", 1)), text_unit("《a.pdf》第 2 页", ("6AV2", 1))],
                      [{"file": "a.pdf", "range": "第 3 页", "reason": "页面损坏，无法读取"}]),
            "b.pdf": OSError("链接已过期"),
            "c.pdf": ([text_unit("《c.pdf》第 1 页", ("3RT2", 1))], []),
        }
        with mock.patch.object(lp, "BATCH_MAX_CHARS", 60), mock.patch.object(lp, "VERIFY_BATCH", 1), \
                mock.patch("builtins.print"):
            model = FakeModel(self.server, fail_extract={2}, fail_verify={1})
            status, output, error = self.run_job(files, model)
        self.assertEqual(status, "success", error)
        self.assertEqual(model.extract_calls.count(2), 1 + lp.EXTRACT_RETRIES)
        got = self.models(output)
        self.assertEqual(set(got), {"6ES7", "3RT2"})
        pending = [it for it in got.values() if it["confidence"] == lp.CONFIDENCE_PENDING_VERIFY]
        self.assertEqual(len(pending), 1)
        self.assertIn("未联网核验", pending[0]["correctionNote"])
        reasons = {(u["file"], u["range"], u["reason"]) for u in output["unparsed"]}
        self.assertIn(("a.pdf", "第 3 页", "页面损坏，无法读取"), reasons)
        self.assertIn(("b.pdf", "", "无法读取"), reasons)
        self.assertIn(("", "《a.pdf》第 2 页", f"解析失败，重试 {lp.EXTRACT_RETRIES} 次仍未成功"), reasons)

    def test_everything_failing_fails_the_task(self):
        files = {"a.pdf": OSError("链接已过期"),
                 "b.pdf": ([], [{"file": "b.pdf", "range": "", "reason": "已加密，无法读取"}])}
        with mock.patch("builtins.print"):
            status, _, error = self.run_job(files, FakeModel(self.server))
        self.assertEqual(status, "failed")
        self.assertIn("《b.pdf》已加密，无法读取", error)

    def test_no_models_extracted_fails_the_task(self):
        files = {"a.pdf": ([text_unit("《a.pdf》第 1 页")], [])}
        status, _, error = self.run_job(files, FakeModel(self.server))
        self.assertEqual(status, "failed")
        self.assertIn("没有从附件中提取到型号", error)

    def test_progress_reported_per_batch(self):
        files = {"a.pdf": ([text_unit(f"《a.pdf》第 {p} 页", (f"M{p}", 1)) for p in range(1, 4)], [])}
        with mock.patch.object(lp, "BATCH_MAX_CHARS", 60):
            status, _, error = self.run_job(files, FakeModel(self.server))
        self.assertEqual(status, "success", error)
        self.assertEqual(self.progress[0], (0, 0, "read"), "开始读取附件时先报告阶段")
        self.assertEqual(self.progress[1], (0, 3, "extract"))
        self.assertIn((3, 3, "extract"), self.progress)
        self.assertIn((3, 3, "merge"), self.progress)
        self.assertIn((3, 4, "verify"), self.progress, "进入核验时把核验批数加进总数")
        self.assertEqual(self.progress[-1], (4, 4, "verify"))
        dones = [d for d, _, _ in self.progress]
        self.assertEqual(dones, sorted(dones))
        order = ["read", "extract", "merge", "verify"]
        stages = [order.index(st) for _, _, st in self.progress]
        self.assertEqual(stages, sorted(stages), "阶段只前进不后退")

    def test_verify_results_matched_by_original_model_not_order(self):
        """核验结果按原文型号对回，不按返回顺序；漏返回的型号标待核实（2026-11-11 实测发现顺序调换导致数量错位）"""
        files = {"a.pdf": ([text_unit("《a.pdf》第 1 页", ("6AV2124", 1470), ("TK4S-14RN", 1529), ("E3Z-D61", 7))], [])}
        model = FakeModel(self.server)

        def call(cfg, system_prompt, text, attachments, schema, **kw):
            result = model(cfg, system_prompt, text, attachments, schema, **kw)
            if kw["web_search"]:
                items = result["groups"][0]["items"]
                for it in items:
                    if it["originalModel"] == "TK4S-14RN":
                        it["confirmedModel"] = "TK4S-14RN-X"  # 核验纠正了型号
                # 顺序调换、并漏掉一个
                result["groups"][0]["items"] = [it for it in reversed(items) if it["originalModel"] != "E3Z-D61"]
            return result

        with mock.patch("builtins.print"):
            status, output, error = self.run_job(files, call)
        self.assertEqual(status, "success", error)
        got = {it["originalModel"]: it for g in output["groups"] for it in g["items"]}
        self.assertEqual(got["6AV2124"]["quantity"], 1470)
        self.assertEqual(got["6AV2124"]["confirmedModel"], "6AV2124")
        self.assertEqual(got["TK4S-14RN"]["quantity"], 1529)
        self.assertEqual(got["TK4S-14RN"]["confirmedModel"], "TK4S-14RN-X")
        self.assertEqual(got["TK4S-14RN"]["description"], "已核验")
        self.assertEqual(got["E3Z-D61"]["confidence"], lp.CONFIDENCE_PENDING_VERIFY)
        self.assertIn("未联网核验", got["E3Z-D61"]["correctionNote"])

    def test_call_timeout_is_retried_with_a_fresh_deadline(self):
        """单次调用跑满期限只算这一批失败一次；重试重新计时，不把整个任务判超时（2026-11-10 实测发现）"""
        files = {"a.pdf": ([text_unit(f"《a.pdf》第 {p} 页", (f"M{p}", 1)) for p in range(1, 4)], [])}
        model = FakeModel(self.server)
        slow = {"left": 1}

        def call(cfg, system_prompt, text, attachments, schema, *, model_name=None, **kw):
            if not kw["web_search"] and "第 2 / 3 部分" in text and slow["left"]:
                slow["left"] -= 1
                time.sleep(0.4)
                self.server.remaining()  # 单次期限已过：与真实调用一样抛 JobTimeout
            return model(cfg, system_prompt, text, attachments, schema, **kw)

        with mock.patch.object(lp, "BATCH_MAX_CHARS", 60), mock.patch.object(self.server, "TIMEOUT_SECONDS", 0.3), \
                mock.patch("builtins.print"):
            status, output, error = self.run_job(files, call)
        self.assertEqual(status, "success", error)
        self.assertEqual(model.extract_calls.count(2), 1, "超时那次没有算成功，重试后才提取到")
        self.assertEqual(set(self.models(output)), {"M1", "M2", "M3"})
        self.assertNotIn("unparsed", output)

    def test_heartbeat_runs_while_reading_attachments(self):
        """audit AI-009：读取和预处理很慢时，心跳在读取阶段就开始，后端不会按空闲判失败"""
        files = {"a.pdf": ([text_unit("《a.pdf》第 1 页", ("M1", 1))], [])}
        self.read_delay = 0.4
        with mock.patch.object(lp, "HEARTBEAT_SECONDS", 0.05):
            status, _, error = self.run_job(files, FakeModel(self.server))
        self.assertEqual(status, "success", error)
        reads = [p for p in self.progress if p[2] == "read"]
        self.assertGreaterEqual(len(reads), 4, "读取期间按心跳间隔报告")
        self.assertEqual(self.progress[0], (0, 0, "read"))
        count = len(self.progress)
        time.sleep(0.2)
        self.assertEqual(len(self.progress), count, "任务结束后心跳停止")

    def test_failed_task_still_reports_usage_once(self):
        """audit AI-010：调用已产生用量但没有型号（或后续失败）时，失败回调仍带上全部用量"""
        files = {"a.pdf": ([text_unit(f"《a.pdf》第 {p} 页") for p in range(1, 4)], [])}
        with mock.patch.object(lp, "BATCH_MAX_CHARS", 30):
            model = FakeModel(self.server, usage=(120, 30))
            status, _, error = self.run_job(files, model)
        self.assertEqual(status, "failed")
        self.assertIn("没有从附件中提取到型号", error)
        calls = len(model.extract_calls)
        self.assertGreater(calls, 1, "多批调用都已产生用量")
        self.assertEqual(self.usages[0]["inputTokens"], 120 * calls)
        self.assertEqual(self.usages[0]["outputTokens"], 30 * calls)

    def test_timed_out_task_reports_usage_of_finished_calls(self):
        files = {"a.pdf": ([text_unit(f"《a.pdf》第 {p} 页", (f"M{p}", 1)) for p in range(1, 7)], [])}
        with mock.patch.object(lp, "BATCH_MAX_CHARS", 60), mock.patch.object(self.server, "MAX_SECONDS", 0.5), \
                mock.patch("builtins.print"):
            model = FakeModel(self.server, delay=0.7, usage=(100, 10))
            status, _, _ = self.run_job(files, model)
        self.assertEqual(status, "failed")
        self.assertEqual(self.usages[0]["inputTokens"], 100 * len(model.extract_calls), "已完成调用的用量随失败回调上报")

    def test_successful_task_reports_usage_without_double_counting(self):
        files = {"a.pdf": ([text_unit(f"《a.pdf》第 {p} 页", (f"M{p}", 1)) for p in range(1, 4)], [])}
        with mock.patch.object(lp, "BATCH_MAX_CHARS", 60):
            model = FakeModel(self.server, usage=(10, 1))
            status, _, error = self.run_job(files, model)
        self.assertEqual(status, "success", error)
        calls = len(model.extract_calls) + len(model.verify_sizes)
        self.assertEqual(self.usages[0]["inputTokens"], 10 * calls)

    def test_deadline_stops_new_batches(self):
        files = {"a.pdf": ([text_unit(f"《a.pdf》第 {p} 页", (f"M{p}", 1)) for p in range(1, 7)], [])}
        with mock.patch.object(lp, "BATCH_MAX_CHARS", 60), mock.patch.object(self.server, "MAX_SECONDS", 0.5), \
                mock.patch("builtins.print"):
            model = FakeModel(self.server, delay=0.7)
            status, _, error = self.run_job(files, model)
        self.assertEqual(status, "failed")
        self.assertIn("超过 0.5 秒未完成", error)
        self.assertEqual(len(model.extract_calls), lp.EXTRACT_WORKERS, "到期后不再发起新批")

    def test_small_inquiry_keeps_single_call(self):
        refs = [{"url": "https://files.example/a.pdf", "ext": "pdf", "name": "a.pdf", "size": 1000}]
        with pymupdf.open() as doc:
            doc.new_page()
            pdf = doc.tobytes()

        def download(ref, directory, deadline_left):
            path = os.path.join(directory, "a.pdf")
            with open(path, "wb") as fh:
                fh.write(pdf)
            return path

        with mock.patch.object(lp, "download", download), \
                mock.patch.object(self.server, "call_parse", return_value={"groups": []}) as call_parse, \
                mock.patch.object(self.server, "parse_large") as parse_large, \
                mock.patch.object(self.server, "send_callback"):
            self.server.process_job({"ai": ai(), "input": {"rawContent": "正文", "attachments": refs}}, "http://cb")
        parse_large.assert_not_called()
        call_parse.assert_called_once()
        self.assertEqual(call_parse.call_args[0][1:4], ("正文", [(pdf, ".pdf")], []))


class HeartbeatTest(unittest.TestCase):
    def test_heartbeat_reports_without_new_progress(self):
        reports = []
        with mock.patch.object(lp, "HEARTBEAT_SECONDS", 0.05):
            with lp.Progress(lambda d, t, st: reports.append((d, t, st)), 5) as progress:
                progress.step()
                time.sleep(0.3)
        self.assertGreaterEqual(reports.count((1, 5, "extract")), 3, "心跳带上当前阶段")
        count = len(reports)
        time.sleep(0.15)
        self.assertEqual(len(reports), count, "结束后不再发心跳")


if __name__ == "__main__":
    unittest.main()
