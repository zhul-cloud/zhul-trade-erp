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

    def test_unsupported_type(self):
        self.assertEqual(lp.preprocess(self.path("a.zip"), ".zip", "a.zip"),
                         ([], [{"file": "a.zip", "range": "", "reason": "不支持的文件类型"}]))


# ---------------------------------------------------------------- 5.3 / 5.6 分批

class PackTest(unittest.TestCase):
    def images(self, n, size=1000):
        return [lp.Unit("image", f"第 {i + 1} 页", image=b"x" * size) for i in range(n)]

    def test_image_count_boundary(self):
        self.assertEqual([len(b) for b in lp.pack(self.images(20))], [20])
        self.assertEqual([len(b) for b in lp.pack(self.images(21))], [20, 1])

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
        self.assertEqual(len(batches), 15)
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

    def __init__(self, server, fail_extract=(), fail_verify=(), delay=0.0):
        self.server = server
        self.fail_extract = set(fail_extract)
        self.fail_verify = set(fail_verify)
        self.delay = delay
        self.extract_calls, self.verify_sizes = [], []
        self.lock = threading.Lock()

    def __call__(self, cfg, system_prompt, text, attachments, schema, *, model, effort, web_search):
        self.server.remaining()  # 与真实调用一样，期限已到时不再发起请求
        if self.delay:
            time.sleep(self.delay)
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
        self.work_dirs = []

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

        def preprocess(path, ext, name, nameplate_no=None):
            self.assertTrue(os.path.exists(path))
            return files[name]

        with mock.patch.object(lp, "download", download), mock.patch.object(lp, "preprocess", preprocess), \
                mock.patch.object(self.server, "call_api", model), \
                mock.patch.object(self.server, "send_progress", lambda url, d, t: self.progress.append((d, t))), \
                mock.patch.object(self.server, "send_callback",
                                  lambda url, status, output=None, error=None: self.sent.append((status, output, error))):
            self.server.process_job({"ai": ai(), "input": {"rawContent": raw, "attachments": refs}}, "http://cb")
        self.assertEqual(len(self.sent), 1)
        return self.sent[0]

    def models(self, output):
        return {it["confirmedModel"]: it for g in output["groups"] for it in g["items"]}

    def test_duplicates_merged_and_120_models_verified_in_3_batches(self):
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
        self.assertEqual(sorted(model.verify_sizes), [20, 50, 50])
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
        self.assertEqual(self.progress[0], (0, 3))
        self.assertIn((3, 3), self.progress)
        self.assertEqual(self.progress[-1], (4, 4), "核验批加入总数后一并报告")
        dones = [d for d, _ in self.progress]
        self.assertEqual(dones, sorted(dones))

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
            with lp.Progress(lambda d, t: reports.append((d, t)), 5) as progress:
                progress.step()
                time.sleep(0.3)
        self.assertGreaterEqual(reports.count((1, 5)), 3)
        count = len(reports)
        time.sleep(0.15)
        self.assertEqual(len(reports), count, "结束后不再发心跳")


if __name__ == "__main__":
    unittest.main()
