"""询盘大附件的预处理与分批解析（OpenSpec inquiry-large-attachments，design D7–D11）。

- 附件流式下载到任务临时目录，逐个预处理、处理完即删，磁盘只取决于最大的单个文件（D7、D8）。
- 预处理：PDF 逐页区分文字页与扫描页，图片缩放，Excel / CSV 按行转文字（D8）。
- 能放进一次请求的小询盘沿用原来的单次解析，行为不变；放不下时分批提取 → 合并去重 → 分批联网核验（D9）。
- 文件、页、批次各自失败互不影响，只有全部内容都没提取出型号才失败（D10）。
"""
from concurrent.futures import ThreadPoolExecutor
import copy
import csv
import io
import json
import os
from pathlib import Path
import re
import threading
import time
import urllib.parse
import urllib.request

MAX_FILE_BYTES = 500 * 1024 * 1024
DOWNLOAD_CHUNK = 1024 * 1024

# 一批的上限：API 与订阅方式共用（订阅单次调用不超过 25MiB、20 张图片/页）
BATCH_MAX_IMAGES = 20
BATCH_MAX_CHARS = 60_000
BATCH_MAX_IMAGE_BYTES = 18 * 1024 * 1024
# 小询盘：原文件放得进一次请求时整份发送（与改动前一致）
SMALL_MAX_BYTES = 18 * 1024 * 1024
SMALL_MAX_PDF_PAGES = 20

TEXT_PAGE_MIN_CHARS = 200
RENDER_DPI = 150
MAX_IMAGE_EDGE = 2000
EXCEL_ROWS_PER_UNIT = 200

EXTRACT_WORKERS = 3
EXTRACT_RETRIES = 2
VERIFY_BATCH = 50
HEARTBEAT_SECONDS = 60

CONFIDENCE_PENDING_VERIFY = 3


class Unit:
    """预处理后的最小单元：一页文字、一页渲染图、一张图片或一组 Excel 行"""

    def __init__(self, kind, label, text="", image=None, ext=".jpg", nameplate_no=None):
        self.kind = kind  # "text" 或 "image"
        self.label = label  # 出现位置，如「《清单.pdf》第 3 页」
        self.text = text
        self.image = image
        self.ext = ext
        self.nameplate_no = nameplate_no

    @property
    def chars(self):
        return len(self.text)


# ---------------------------------------------------------------- 下载

def download(ref: dict, directory: str, deadline_left) -> str:
    """把后端签发的临时链接流式写到临时目录，单个超过 500MB 时失败（D7）"""
    url = ref["url"]
    if urllib.parse.urlsplit(url).scheme not in ("http", "https"):
        raise ValueError("附件链接只支持 http / https")
    ext = "." + (ref.get("ext") or "").lower().lstrip(".")
    fd, path = _mkstemp(directory, ext)
    written = 0
    try:
        with urllib.request.urlopen(url, timeout=min(120, deadline_left())) as resp, os.fdopen(fd, "wb") as out:
            while True:
                chunk = resp.read(DOWNLOAD_CHUNK)
                if not chunk:
                    break
                written += len(chunk)
                if written > MAX_FILE_BYTES:
                    raise ValueError("附件超过 500MB")
                out.write(chunk)
    except BaseException:
        _remove(path)
        raise
    return path


def _mkstemp(directory, ext):
    import tempfile
    return tempfile.mkstemp(dir=directory, suffix=ext)


def _remove(path):
    try:
        os.remove(path)
    except OSError:
        pass


# ---------------------------------------------------------------- 预处理

def preprocess(path: str, ext: str, name: str, nameplate_no=None):
    """把一个附件转成预处理单元；返回 (units, unparsed)"""
    if ext == ".pdf":
        return _pdf_units(path, name)
    if ext in (".jpg", ".jpeg", ".png", ".gif", ".webp"):
        try:
            return [Unit("image", f"《{name}》", image=_shrink_image(Path(path).read_bytes()), nameplate_no=nameplate_no)], []
        except Exception:  # noqa: BLE001 - 损坏的图片只影响它自己
            return [], [{"file": name, "range": "", "reason": "图片无法读取"}]
    if ext in (".xlsx", ".xls", ".csv"):
        try:
            return _sheet_units(path, ext, name), []
        except Exception:  # noqa: BLE001
            return [], [{"file": name, "range": "", "reason": "表格无法读取"}]
    return [], [{"file": name, "range": "", "reason": "不支持的文件类型"}]


def _pdf_units(path, name):
    import pymupdf
    units, unparsed = [], []
    try:
        doc = pymupdf.open(path)
    except Exception:  # noqa: BLE001
        return [], [{"file": name, "range": "", "reason": "文件损坏，无法读取"}]
    with doc:
        if doc.needs_pass:
            return [], [{"file": name, "range": "", "reason": "已加密，无法读取"}]
        for i in range(len(doc)):
            label = f"《{name}》第 {i + 1} 页"
            try:
                page = doc[i]
                text = page.get_text().strip()
                if len(text) >= TEXT_PAGE_MIN_CHARS:
                    units.append(Unit("text", label, text=f"{label}：\n{text}"))
                    continue
                scale = min(RENDER_DPI / 72, MAX_IMAGE_EDGE / max(page.rect.width, page.rect.height))
                pix = page.get_pixmap(matrix=pymupdf.Matrix(scale, scale), alpha=False)
                units.append(Unit("image", label, image=pix.tobytes("jpeg"), ext=".jpg"))
            except Exception:  # noqa: BLE001 - 单页损坏只记这一页
                unparsed.append({"file": name, "range": f"第 {i + 1} 页", "reason": "页面损坏，无法读取"})
    return units, unparsed


def _shrink_image(raw: bytes) -> bytes:
    """长边不超过 2000 像素，透明背景按白底合成，转 JPEG"""
    from PIL import Image
    with Image.open(io.BytesIO(raw)) as image:
        image.thumbnail((MAX_IMAGE_EDGE, MAX_IMAGE_EDGE))
        rgba = image.convert("RGBA")
        background = Image.new("RGB", rgba.size, "white")
        background.paste(rgba, mask=rgba.getchannel("A"))
        out = io.BytesIO()
        background.save(out, format="JPEG", quality=85)
        return out.getvalue()


def sheet_rows(path, ext):
    """逐表逐行读出 (表名, 行号, [单元格文字])，跳过空行；流式读取，不整表载入"""
    if ext == ".csv":
        raw = Path(path).read_bytes()
        for enc in ("utf-8-sig", "gbk"):
            try:
                text = raw.decode(enc)
                break
            except UnicodeDecodeError:
                continue
        else:
            text = raw.decode("utf-8", errors="replace")
        for n, row in enumerate(csv.reader(io.StringIO(text)), 1):
            cells = [c.strip() for c in row]
            if any(cells):
                yield "", n, cells
        return
    if ext == ".xlsx":
        import openpyxl
        wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
        try:
            for ws in wb.worksheets:
                for n, row in enumerate(ws.iter_rows(values_only=True), 1):
                    cells = ["" if v is None else str(v).strip() for v in row]
                    if any(cells):
                        yield ws.title, n, cells
        finally:
            wb.close()
        return
    import xlrd
    book = xlrd.open_workbook(path, on_demand=True)
    try:
        for sheet in book.sheets():
            for n in range(sheet.nrows):
                cells = [str(v).strip() for v in sheet.row_values(n)]
                if any(cells):
                    yield sheet.name, n + 1, cells
    finally:
        book.release_resources()


def sheet_text(path, ext) -> str:
    """整表转文字，格式与后端原来的 SpreadsheetText 一致（小询盘用，保持结果不变）"""
    parts, sheet = [], None
    for title, _, cells in sheet_rows(path, ext):
        if ext != ".csv" and title != sheet:
            sheet = title
            parts.append(f"Sheet: {title}")
        parts.append("\t".join(cells) + "\t")
    return "\n".join(parts).strip()


def _sheet_units(path, ext, name):
    """每 200 行一个单元，单元开头注明工作表与行号范围"""
    units, rows, sheet, first = [], [], None, None

    def flush():
        if rows:
            where = f"工作表「{sheet}」" if sheet else ""
            label = f"《{name}》{where}第 {first}–{last} 行"
            units.append(Unit("text", label, text=f"{label}：\n" + "\n".join(rows)))

    last = None
    for title, n, cells in sheet_rows(path, ext):
        if title != sheet or len(rows) >= EXCEL_ROWS_PER_UNIT:
            flush()
            rows, sheet, first = [], title, n
        rows.append("\t".join(cells))
        last = n
    flush()
    return units


# ---------------------------------------------------------------- 分批

def pack(units):
    """按图片数、文字量、图片字节数切分批次；单个单元超过上限时自成一批"""
    batches, cur, imgs, chars, size = [], [], 0, 0, 0
    for u in units:
        u_imgs = 1 if u.kind == "image" else 0
        u_size = len(u.image) if u.image else 0
        if cur and (imgs + u_imgs > BATCH_MAX_IMAGES or chars + u.chars > BATCH_MAX_CHARS
                    or size + u_size > BATCH_MAX_IMAGE_BYTES):
            batches.append(cur)
            cur, imgs, chars, size = [], 0, 0, 0
        cur.append(u)
        imgs += u_imgs
        chars += u.chars
        size += u_size
    if cur:
        batches.append(cur)
    return batches


# ---------------------------------------------------------------- 合并

def normalize_model(model: str) -> str:
    """合并用的型号键：转大写、去掉空白，保留连字符等符号"""
    return re.sub(r"\s+", "", (model or "")).upper()


def merge(batch_results):
    """batch_results 为 [(groups, labels)]：按确认型号合并去重，数量相加并注明位置（spec「同一型号出现在多处」）"""
    merged, order, group_meta = {}, [], {}
    for groups, labels in batch_results:
        for g in groups:
            meta_key = (g.get("brand", ""), g.get("category", ""))
            group_meta.setdefault(meta_key, {k: v for k, v in g.items() if k != "items"})
            for item in g.get("items", []):
                key = normalize_model(item.get("confirmedModel") or item.get("originalModel"))
                if not key:
                    continue
                where = (item.pop("source", "") or "").strip() or "；".join(labels)
                if key not in merged:
                    entry = copy.deepcopy(item)
                    entry["_group"] = meta_key
                    entry["_where"] = [where]
                    entry["_qty"] = [item.get("quantity", 0)]
                    merged[key] = entry
                    order.append(key)
                    continue
                entry = merged[key]
                entry["_where"].append(where)
                if (entry.get("unit") or "").strip().lower() == (item.get("unit") or "").strip().lower():
                    entry["quantity"] = int(entry.get("quantity") or 0) + int(item.get("quantity") or 0)
                    entry["_qty"].append(item.get("quantity", 0))
                else:
                    entry["_unit_conflict"] = True
                    entry["confidence"] = CONFIDENCE_PENDING_VERIFY
    items = []
    for key in order:
        e = merged[key]
        notes = [e.get("correctionNote", "")] if e.get("correctionNote") else []
        if len(e["_where"]) > 1:
            if e.get("_unit_conflict"):
                notes.append("在 " + "、".join(e["_where"]) + " 出现，单位不同未相加，请核对")
            else:
                notes.append("在 " + "、".join(e["_where"]) + " 出现，数量已相加（" + " + ".join(str(q) for q in e["_qty"]) + "）")
        elif e["_where"][0]:
            notes.append("出现在 " + e["_where"][0])
        e["correctionNote"] = "；".join(notes)
        items.append(e)
    return items, group_meta


def regroup(items, group_meta):
    """按品牌、品类重新分组，去掉内部字段"""
    groups, index = [], {}
    for item in items:
        meta_key = item.pop("_group", ("", ""))
        for k in [k for k in item if k.startswith("_")]:
            item.pop(k)
        if meta_key not in index:
            g = dict(group_meta.get(meta_key) or {"brand": meta_key[0], "category": meta_key[1]})
            g["items"] = []
            index[meta_key] = g
            groups.append(g)
        index[meta_key]["items"].append(item)
    return groups


# ---------------------------------------------------------------- 进度

class Progress:
    """每完成一批报告一次进度；超过 60 秒没有新进度时也报告一次，作为心跳（D11）"""

    def __init__(self, report, total):
        self.report = report
        self.total = total
        self.done = 0
        self.lock = threading.Lock()
        self.stop = threading.Event()
        self.thread = threading.Thread(target=self._beat, daemon=True)

    def __enter__(self):
        self.report(0, self.total)
        self.thread.start()
        return self

    def __exit__(self, *exc):
        self.stop.set()

    def add_total(self, n):
        with self.lock:
            self.total += n
            done, total = self.done, self.total
        self.report(done, total)

    def step(self):
        with self.lock:
            self.done += 1
            done, total = self.done, self.total
        self.report(done, total)

    def _beat(self):
        while not self.stop.wait(HEARTBEAT_SECONDS):
            with self.lock:
                done, total = self.done, self.total
            self.report(done, total)


def run_parallel(fn, items, workers, propagate):
    """线程池里执行；propagate 在每个工作线程开始前调用（复制任务期限等线程变量）"""
    def wrapped(arg):
        propagate()
        return fn(arg)
    with ThreadPoolExecutor(max_workers=workers) as pool:
        return list(pool.map(wrapped, items))


def now():
    return time.monotonic()


def dumps(value):
    return json.dumps(value, ensure_ascii=False)
