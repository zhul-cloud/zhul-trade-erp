"""真实 Anthropic SDK 流式协议回归：MockTransport 拦截网络，不调用云模型。"""
import base64
from contextlib import contextmanager
import io
import json
import unittest
from unittest import mock

import anthropic
import httpx2
import pymupdf
from PIL import Image

from test_server import KEY, PARSE_RESULT, ai, load_server


def sse_result(text, stop_reason="end_turn"):
    events = [
        {"type": "message_start", "message": {"id": "msg_local", "type": "message", "role": "assistant",
            "model": "claude-opus-5-5", "content": [], "stop_reason": None, "stop_sequence": None,
            "usage": {"input_tokens": 10, "output_tokens": 1}}},
        {"type": "content_block_start", "index": 0, "content_block": {"type": "text", "text": ""}},
        {"type": "content_block_delta", "index": 0, "delta": {"type": "text_delta", "text": text}},
        {"type": "content_block_stop", "index": 0},
        {"type": "message_delta", "delta": {"stop_reason": stop_reason, "stop_sequence": None},
            "usage": {"output_tokens": 20}},
        {"type": "message_stop"},
    ]
    return "".join(f"event: {event['type']}\ndata: {json.dumps(event)}\n\n" for event in events).encode()


class ClaudeStreamProtocolTest(unittest.TestCase):
    def setUp(self):
        self.server = load_server()
        self.cfg = self.server.parse_ai_config(ai())
        self.requests = []

    @contextmanager
    def sdk(self, text=None, request_id=None, stop_reason="end_turn", error=None, replies=None):
        responses = iter(replies) if replies else None

        def respond(request):
            self.requests.append(json.loads(request.content))
            if error:
                raise error
            headers = {"content-type": "text/event-stream"}
            if request_id:
                headers["request-id"] = request_id
            reply, reason = next(responses) if responses else (
                text if text is not None else json.dumps(PARSE_RESULT), stop_reason)
            return httpx2.Response(200, headers=headers, content=sse_result(reply, reason))

        with anthropic.Anthropic(api_key=KEY, base_url="https://unit.test", max_retries=0,
                http_client=httpx2.Client(transport=httpx2.MockTransport(respond), trust_env=False)) as client:
            with mock.patch.object(self.server.anthropic, "Anthropic", return_value=client):
                yield client

    def test_real_final_message_has_no_private_request_id(self):
        with self.sdk(request_id="req_header") as client:
            with client.beta.messages.stream(model=self.cfg.parse_model, max_tokens=20,
                    messages=[{"role": "user", "content": "model"}]) as stream:
                response = stream.get_final_message()
                self.assertEqual(stream.request_id, "req_header")
        self.assertEqual(type(response).__name__, "ParsedBetaMessage")
        self.assertFalse(hasattr(response, "_request_id"))

    def test_call_parse_succeeds_with_and_without_header_request_id(self):
        for request_id in (None, "req_header"):
            with self.subTest(request_id=request_id), self.sdk(request_id=request_id), \
                    mock.patch("builtins.print") as printed:
                self.assertEqual(self.server.call_parse(self.cfg, "Siemens 6ES7 x1"), PARSE_RESULT)
            self.assertIn(f"request_id={request_id}", str(printed.call_args_list))
            self.assertEqual(self.requests[-1]["messages"][0]["content"], [
                {"type": "text", "text": "Siemens 6ES7 x1"}])

    def test_translate_uses_same_stream_metadata_path(self):
        result = {"items": [{"key": "1", "text": "Siemens PLC"}]}
        with self.sdk(json.dumps(result)), mock.patch("builtins.print"):
            self.assertEqual(self.server.call_translate(self.cfg, [{"key": "1", "text": "西门子 PLC"}]), result)
        request = self.requests[-1]
        self.assertEqual(request["model"], self.cfg.translate_model)
        self.assertNotIn("tools", request)
        self.assertNotIn("fallbacks", request)

    def test_process_job_preserves_valid_image_pdf_and_extracted_spreadsheet_text(self):
        png = io.BytesIO()
        Image.new("RGB", (8, 8), "white").save(png, format="PNG")
        with pymupdf.open() as doc:
            doc.new_page().insert_text((10, 20), "MODEL: 6ES7 QTY: 1")
            pdf = doc.tobytes()
        fixtures = [(png.getvalue(), ".png"), (pdf, ".pdf")]
        raw = "正文 Siemens 6ES7 x1\n\n附件《清单.xlsx》：\nSheet: Models\n6ES7\t1\n附件《清单.csv》：\nmodel,qty\n6ES7,1"
        with self.sdk(request_id="req_attachments"), \
                mock.patch.object(self.server, "_read_attachment", side_effect=fixtures) as read, \
                mock.patch.object(self.server, "send_callback") as callback:
            self.server.process_job({"ai": ai(), "input": {"rawContent": raw, "attachments": [
                {"url": "https://unit.test/png", "ext": "png"},
                {"url": "https://unit.test/pdf", "ext": "pdf"}]}}, "http://callback")
        callback.assert_called_once_with("http://callback", "success", output=PARSE_RESULT)
        self.assertEqual(read.call_count, 2)
        request = self.requests[-1]
        content = request["messages"][0]["content"]
        self.assertEqual([block["type"] for block in content], ["image", "document", "text"])
        for block, (data, ext) in zip(content, fixtures):
            self.assertEqual(base64.b64decode(block["source"]["data"]), data)
            self.assertEqual(block["source"]["type"], "base64")
            self.assertEqual(block["source"]["media_type"], "image/png" if ext == ".png" else "application/pdf")
        self.assertIn(raw, content[-1]["text"])
        self.assertEqual(request["tools"][0]["type"], "web_search_20260209")
        self.assertEqual(request["output_config"]["format"]["type"], "json_schema")

    def test_pause_turn_continues_original_messages(self):
        with self.sdk(replies=[("pause for search", "pause_turn"), (json.dumps(PARSE_RESULT), "end_turn")]):
            self.assertEqual(self.server.call_parse(self.cfg, "model"), PARSE_RESULT)
        self.assertEqual(len(self.requests), 2)
        messages = self.requests[-1]["messages"]
        self.assertEqual(messages[0], self.requests[0]["messages"][0])
        self.assertEqual(messages[1]["role"], "assistant")
        self.assertEqual(messages[1]["content"][0]["text"], "pause for search")

    def test_business_errors_still_fail_with_real_sdk(self):
        cases = [("{}", "end_turn", "业务 Schema"), ("not-json", "end_turn", None),
                 ("{}", "refusal", "拒绝"), ("{}", "max_tokens", "长度上限")]
        for text, reason, message in cases:
            with self.subTest(reason=reason, text=text), self.sdk(text, stop_reason=reason), \
                    mock.patch.object(self.server, "send_callback") as callback, mock.patch("builtins.print"):
                self.server.process_job({"ai": ai(), "input": {"rawContent": "model"}}, "http://callback")
            self.assertEqual(callback.call_args.args, ("http://callback", "failed"))
            if message:
                self.assertIn(message, callback.call_args.kwargs["error"])
        with self.sdk(error=httpx2.ReadTimeout("local timeout")), \
                mock.patch.object(self.server, "send_callback") as callback:
            self.server.process_job({"ai": ai(), "input": {"rawContent": "model"}}, "http://callback")
        self.assertEqual(callback.call_args.args, ("http://callback", "failed"))
        self.assertIn("秒未完成", callback.call_args.kwargs["error"])


if __name__ == "__main__":
    unittest.main()
