#!/usr/bin/env python3
"""
本地开发用的 AI 编排服务 stub —— 不是真正的 AI 编排服务实现，只用于让
zhul-erp-backend 能够本地联调"提交任务 -> 异步 webhook 回调"这条链路。

对应 openspec/changes/add-inquiry-management/design.md 决策3的通用契约：
  POST /skills/{skill_id}/run
    body: {"input": {...}, "callback_url": "http://.../api/v1/ai-tasks/{id}/callback"}
    -> 202 {"job_id": "..."}
  几秒后:
  POST {callback_url}
    header: X-AI-Callback-Token: <与 zhul-erp-backend 配置一致的共享密钥>
    body: {"status": "success", "output": {...}, "error": null}

只依赖 Python 标准库，不需要额外安装依赖。
"""
import json
import os
import threading
import time
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, HTTPServer

PORT = int(os.environ.get("AI_STUB_PORT", "8899"))
CALLBACK_TOKEN = os.environ.get("AI_TASK_CALLBACK_TOKEN", "zhul-erp-ai-callback-secret")
CALLBACK_DELAY_SECONDS = float(os.environ.get("AI_STUB_DELAY_SECONDS", "3"))

# 示例拆单结果，对应本次 UI 原型"客户询盘详情-待确认拆单预览"页展示的数据结构：
# 按品牌+品类分组，每组含若干型号明细，置信度枚举与 inquiry-parser skill 原定义一致
# （1=确认 2=已纠正 3=待核实 4=未识别）。
SAMPLE_OUTPUT = {
    "groups": [
        {
            "brand": "Siemens",
            "category": "PLC",
            "inquiryTemplate": "您好，我们有一批 Siemens PLC 型号需要询价：\n1) 6ES7214-1AG40-0XB0 x25pcs\n2) 6ED1052-1MD00-0BA6 x8pcs\n麻烦协助报价，注明含税单价、货期及付款方式，谢谢！",
            "emailTemplateCn": "尊敬的供应商，\n\n您好！我司现就以下 Siemens PLC 型号进行询价：\n1) 6ES7214-1AG40-0XB0，数量 25pcs\n2) 6ED1052-1MD00-0BA6，数量 8pcs\n\n烦请提供最优报价、交货周期及付款条件（FOB上海），谢谢配合！\n\n此致\n敬礼",
            "emailTemplateEn": "Dear Sir/Madam,\n\nRe: Inquiry for Siemens PLC — 6ES7214-1AG40-0XB0 x25pcs, 6ED1052-1MD00-0BA6 x8pcs.\nPlease kindly quote your best price, lead time and payment terms (FOB Shanghai).\n\nBest regards",
            "items": [
                {
                    "originalModel": "6ES7214-1AG40-0XB0",
                    "confirmedModel": "6ES7214-1AG40-0XB0",
                    "confidence": 1,
                    "correctionNote": "",
                    "description": "S7-1200 CPU 1214C，24VDC，14DI/10DO",
                    "quantity": 20,
                    "unit": "台",
                },
                {
                    "originalModel": "6ES7214-1AG400XB0",
                    "confirmedModel": "6ES7214-1AG40-0XB0",
                    "confidence": 2,
                    "correctionNote": "同上型号，客户原文缺少连字符",
                    "description": "S7-1200 CPU 1214C，24VDC，14DI/10DO",
                    "quantity": 5,
                    "unit": "台",
                },
                {
                    "originalModel": "6ED1052-1MD00-0BA6",
                    "confirmedModel": "6ED1052-1MD00-0BA6",
                    "confidence": 1,
                    "correctionNote": "",
                    "description": "LOGO! 12/24RCE 逻辑控制模块",
                    "quantity": 8,
                    "unit": "台",
                },
            ],
        },
        {
            "brand": "Schneider",
            "category": "变频器",
            "inquiryTemplate": "您好，我们有一批 Schneider 变频器型号需要询价：\n1) ATV320U15N4C x10pcs\n麻烦协助报价，注明含税单价、货期及付款方式，谢谢！",
            "emailTemplateCn": "尊敬的供应商，\n\n您好！我司现就以下 Schneider 变频器型号进行询价：\n1) ATV320U15N4C，数量 10pcs\n\n烦请提供最优报价、交货周期及付款条件（FOB上海），谢谢配合！\n\n此致\n敬礼",
            "emailTemplateEn": "Dear Sir/Madam,\n\nRe: Inquiry for Schneider ATV320U15N4C x10pcs.\nPlease kindly quote your best price and lead time.\n\nBest regards",
            "items": [
                {
                    "originalModel": "ATV320U15N4C",
                    "confirmedModel": "ATV320U15N4C",
                    "confidence": 1,
                    "correctionNote": "",
                    "description": "Altivar 320，1.5kW，三相380-480V",
                    "quantity": 10,
                    "unit": "台",
                },
                {
                    "originalModel": "ATV310HU75N4E",
                    "confirmedModel": "",
                    "confidence": 4,
                    "correctionNote": "",
                    "description": "联网搜索无匹配结果，需人工核实型号",
                    "quantity": 3,
                    "unit": "台",
                },
            ],
        },
        {
            "brand": "未识别品牌",
            "category": "传感器",
            "inquiryTemplate": "该组含待核实型号，暂不生成询价话术，建议先核实型号后再联系供应商。",
            "emailTemplateCn": "",
            "emailTemplateEn": "",
            "items": [
                {
                    "originalModel": "光电传感器 E3F-DS30",
                    "confirmedModel": "E3F-DS30C4",
                    "confidence": 3,
                    "correctionNote": "",
                    "description": "疑似 Autonics 光电传感器，型号后缀待核实",
                    "quantity": 15,
                    "unit": "个",
                },
            ],
        },
    ]
}


def send_callback(callback_url: str):
    time.sleep(CALLBACK_DELAY_SECONDS)
    body = json.dumps({"status": "success", "output": SAMPLE_OUTPUT, "error": None}).encode("utf-8")
    req = urllib.request.Request(
        callback_url,
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "X-AI-Callback-Token": CALLBACK_TOKEN,
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            print(f"[ai-orchestrator-stub] callback -> {callback_url} status={resp.status}")
    except Exception as e:
        print(f"[ai-orchestrator-stub] callback FAILED -> {callback_url}: {e}")


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        if not self.path.startswith("/skills/") or not self.path.endswith("/run"):
            self.send_response(404)
            self.end_headers()
            return

        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            payload = {}

        callback_url = payload.get("callback_url")
        job_id = str(uuid.uuid4())
        print(f"[ai-orchestrator-stub] received {self.path}, will callback {callback_url} in {CALLBACK_DELAY_SECONDS}s (job_id={job_id})")

        self.send_response(202)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps({"job_id": job_id}).encode("utf-8"))

        if callback_url:
            threading.Thread(target=send_callback, args=(callback_url,), daemon=True).start()

    def log_message(self, format, *args):
        print(f"[ai-orchestrator-stub] {self.address_string()} - {format % args}")


if __name__ == "__main__":
    server = HTTPServer(("0.0.0.0", PORT), Handler)
    print(f"[ai-orchestrator-stub] listening on :{PORT}, callback token={'*' * len(CALLBACK_TOKEN)} (len={len(CALLBACK_TOKEN)}), delay={CALLBACK_DELAY_SECONDS}s")
    server.serve_forever()
