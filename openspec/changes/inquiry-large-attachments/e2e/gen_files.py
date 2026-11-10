"""生成 7.1 端到端实测用的附件：300 页混有扫描页的 PDF（约 300MB）与两个工作表的大 xlsx。"""
import io
import os
import random
import sys

import openpyxl
import pymupdf
from PIL import Image, ImageDraw, ImageFont

out = sys.argv[1]
random.seed(20261110)

MODELS = [
    ("Siemens", "6ES7214-1AG40-0XB0"), ("Siemens", "6ES7215-1AG40-0XB0"), ("Siemens", "6ES7231-4HD32-0XB0"),
    ("Siemens", "6ES7232-4HD32-0XB0"), ("Siemens", "6ES7221-1BF32-0XB0"), ("Siemens", "6ES7222-1BF32-0XB0"),
    ("Siemens", "6AV2123-2GB03-0AX0"), ("Siemens", "6AV2124-0GC01-0AX0"), ("Siemens", "6SL3210-1KE18-8UF1"),
    ("Siemens", "3RT2026-1BB40"), ("Siemens", "3RV2011-1JA10"), ("Siemens", "6ES7512-1DK01-0AB0"),
    ("Schneider", "LC1D18M7C"), ("Schneider", "LC1D25M7C"), ("Schneider", "GV2ME10"), ("Schneider", "ATV320U15N4C"),
    ("Schneider", "TM221CE16R"), ("Schneider", "XB5AD21"), ("Schneider", "RXM4AB2BD"), ("Schneider", "LRD16C"),
    ("Omron", "E3Z-D61"), ("Omron", "E3Z-R61"), ("Omron", "E2E-X5ME1"), ("Omron", "MY2N-GS DC24"),
    ("Omron", "CP1E-N30DR-A"), ("Omron", "H3CR-A8 AC100-240"), ("Omron", "G3NA-210B DC5-24"), ("Omron", "E6B2-CWZ6C 1000P/R"),
    ("Mitsubishi", "FX3U-32MR/ES-A"), ("Mitsubishi", "FX5U-32MR/ES"), ("Mitsubishi", "MR-J4-70A"), ("Mitsubishi", "HG-KR73"),
    ("Mitsubishi", "FR-D720S-0.4K"), ("Mitsubishi", "GT2107-WTBD"), ("ABB", "ACS580-01-12A7-4"), ("ABB", "S201-C16"),
    ("ABB", "AF09-30-10-13"), ("ABB", "MS132-10"), ("Phoenix Contact", "2866763"), ("Phoenix Contact", "3044102"),
    ("Pilz", "751104"), ("SICK", "WTB4-3P2161"), ("SICK", "DFS60B-S4PA10000"), ("Festo", "DSNU-25-100-PPV-A"),
    ("Festo", "MFH-5-1/4"), ("SMC", "SY5120-5DZ-01"), ("SMC", "CDQ2B32-20DZ"), ("Weidmuller", "1020100000"),
    ("Delta", "VFD022E43A"), ("Delta", "DVP32ES200T"), ("Panasonic", "MADLN15SE"), ("Panasonic", "FP0R-C32T"),
    ("Keyence", "LR-ZB250CN"), ("Keyence", "FS-N41N"), ("Autonics", "TK4S-14RN"), ("Autonics", "E40S6-1000-3-T-24"),
    ("Yaskawa", "SGD7S-2R8A00A"), ("Yaskawa", "SGM7J-04AFC6S"), ("Beckhoff", "EL1008"), ("Beckhoff", "EK1100"),
]
UNITS = {"Siemens": "pcs", "Schneider": "pcs", "Omron": "pcs"}

FILLER = ("Please quote the following spare parts for our production line maintenance. "
          "Original brand only, new and sealed, with delivery time and warranty. ")


def unit_of(brand):
    return UNITS.get(brand, "pcs")


# ---------------------------------------------------------------- PDF
doc = pymupdf.open()
font = ImageFont.load_default(size=36)
expected = {}


def note(model, qty, unit, where):
    expected.setdefault(model, []).append((qty, unit, where))


for p in range(1, 301):
    page = doc.new_page(width=595, height=842)
    picks = random.sample(MODELS, 2)
    # 刻意的重复：第 3 页与第 120 页都有 6ES7214-1AG40-0XB0（各 2 个）；第 45 页与第 88 页 3VA… 单位不同
    if p in (3, 120):
        picks[0] = ("Siemens", "6ES7214-1AG40-0XB0")
    rows = []
    for brand, model in picks:
        qty = 2 if model == "6ES7214-1AG40-0XB0" and p in (3, 120) else random.randint(1, 20)
        rows.append((brand, model, qty, unit_of(brand)))
    if p == 45:
        rows.append(("Siemens", "3VA2340-5JQ42-0AA0", 1, "pcs"))
    if p == 88:
        rows.append(("Siemens", "3VA2340-5JQ42-0AA0", 1, "set"))
    lines = [f"RFQ equipment list - page {p} / 300", FILLER, ""]
    lines += [f"{i + 1}. Brand: {b}   Model: {m}   Qty: {q} {u}" for i, (b, m, q, u) in enumerate(rows)]
    for b, m, q, u in rows:
        note(m, q, u, f"p{p}")
    if p <= 280:
        page.insert_textbox(pymupdf.Rect(40, 40, 555, 800), "\n".join(lines), fontsize=11)
        continue
    # 扫描页：文字画成图片（没有文字层），再加一块难以压缩的噪点图把文件撑大
    img = Image.new("RGB", (1240, 1754), "white")
    d = ImageDraw.Draw(img)
    y = 80
    for line in lines:
        for chunk in [line[i:i + 60] for i in range(0, len(line), 60)] or [""]:
            d.text((80, y), chunk, fill="black", font=font)
            y += 50
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    page.insert_image(pymupdf.Rect(0, 0, 595, 842), stream=buf.getvalue())
    noise = Image.frombytes("RGB", (2200, 2200), os.urandom(2200 * 2200 * 3))
    nb = io.BytesIO()
    noise.save(nb, format="PNG", compress_level=1)
    page.insert_image(pymupdf.Rect(495, 742, 585, 832), stream=nb.getvalue())
    del noise, nb

pdf_path = os.path.join(out, "设备清单-2026.pdf")
doc.save(pdf_path, garbage=0, deflate=False)
doc.close()

# ---------------------------------------------------------------- xlsx
wb = openpyxl.Workbook()
ws = wb.active
ws.title = "传感器"
ws.append(["序号", "品牌", "型号", "数量", "单位", "备注"])
ws2 = wb.create_sheet("备件")
ws2.append(["序号", "品牌", "型号", "数量", "单位", "备注"])
n = 0
for sheet, count in ((ws, 1200), (ws2, 1800)):
    for i in range(count):
        n += 1
        brand, model = random.choice(MODELS)
        qty = random.randint(1, 50)
        sheet.append([i + 1, brand, model, qty, unit_of(brand), "原装正品，含税含运"])
        note(model, qty, unit_of(brand), f"{sheet.title}#{i + 2}")
xlsx_path = os.path.join(out, "报价单汇总.xlsx")
wb.save(xlsx_path)

with open(os.path.join(out, "expected.txt"), "w", encoding="utf-8") as fh:
    for m in sorted(expected):
        units = {u for _, u, _ in expected[m]}
        fh.write(f"{m}\t{sum(q for q, _, _ in expected[m])}\t{','.join(sorted(units))}\t{len(expected[m])}\n")

for path in (pdf_path, xlsx_path):
    print(os.path.basename(path), round(os.path.getsize(path) / 1024 / 1024, 1), "MB")
print("型号数", len(expected))
