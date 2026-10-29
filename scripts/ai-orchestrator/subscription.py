"""个人订阅适配：凭据仅由官方 CLI 管理；每次任务使用隔离的工作目录和进程组。"""
import base64
import io
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import threading

import jsonschema

MAX_ATTACHMENT_BYTES = 25 * 1024 * 1024
MAX_IMAGES = 20
MAX_PDF_PAGES = 20
MAX_IMAGE_EDGE = 2000
_SLOT = threading.BoundedSemaphore(1)


def subscription_env():
    """不继承 API key、CLI token、MCP 配置或用户主目录。"""
    env = {k: v for k, v in os.environ.items() if k.upper() in (
        "PATH", "SYSTEMROOT", "WINDIR", "TEMP", "TMP", "LANG", "LC_ALL", "TZ",
        "SSL_CERT_FILE", "SSL_CERT_DIR", "HTTPS_PROXY", "HTTP_PROXY", "NO_PROXY",
    )}
    home = Path(os.environ.get("AI_ORCHESTRATOR_HOME") or Path(tempfile.gettempdir()) / "zhul-ai-home")
    home.mkdir(parents=True, exist_ok=True, mode=0o700)
    env.update(HOME=str(home), USERPROFILE=str(home), CODEX_HOME=str(home / "codex"),
               CLAUDE_CONFIG_DIR=str(home / "claude"), PYTHONIOENCODING="utf-8", PYTHONDONTWRITEBYTECODE="1")
    for name in ("CODEX_HOME", "CLAUDE_CONFIG_DIR"):
        Path(env[name]).mkdir(parents=True, exist_ok=True, mode=0o700)
    return env


def stop_process(process):
    """终止 worker 及其 SDK/CLI 子进程；不留下仍占用订阅额度的后台调用。"""
    if os.name == "nt":
        subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True, check=False)
    else:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
    process.communicate()


def prepare_images(attachments, directory):
    """图片重新编码，PDF 每页渲染；数量/体积超限明确失败，不截断附件。"""
    import pymupdf
    from PIL import Image

    paths = []
    total_bytes = 0
    for raw, ext in attachments:
        total_bytes += len(raw)
        if total_bytes > MAX_ATTACHMENT_BYTES:
            raise RuntimeError("订阅模式附件总大小不能超过 25 MiB")
        if ext == ".pdf":
            with pymupdf.open(stream=raw, filetype="pdf") as doc:
                if doc.needs_pass or not 0 < len(doc) <= MAX_PDF_PAGES:
                    raise RuntimeError("订阅模式 PDF 必须未加密且为 1 到 20 页")
                if len(paths) + len(doc) > MAX_IMAGES:
                    raise RuntimeError("订阅模式附件最多共 20 张图片/PDF 页")
                for page in doc:
                    scale = min(2, MAX_IMAGE_EDGE / max(page.rect.width, page.rect.height))
                    path = Path(directory) / f"attachment-{len(paths)}.png"
                    page.get_pixmap(matrix=pymupdf.Matrix(scale, scale), alpha=False).save(str(path))
                    paths.append(str(path))
        else:
            if ext not in (".png", ".jpg", ".jpeg", ".gif", ".webp"):
                raise RuntimeError("订阅模式只支持图片和 PDF 附件")
            if len(paths) >= MAX_IMAGES:
                raise RuntimeError("订阅模式附件最多共 20 张图片/PDF 页")
            with Image.open(io.BytesIO(raw)) as image:
                if image.width * image.height > 20_000_000:
                    raise RuntimeError("订阅模式单张图片不能超过 2000 万像素")
                image.thumbnail((MAX_IMAGE_EDGE, MAX_IMAGE_EDGE))
                path = Path(directory) / f"attachment-{len(paths)}.png"
                # 透明背景按白底合成，避免黑字/工程线条与透明像素一起变黑。
                rgba = image.convert("RGBA")
                background = Image.new("RGB", rgba.size, "white")
                background.paste(rgba, mask=rgba.getchannel("A"))
                background.save(path, format="PNG")
                paths.append(str(path))
    return paths


def call_subscription(provider, system_prompt, text, attachments, schema, *, model, effort,
                      web_search, max_web_searches, timeout):
    if not _SLOT.acquire(blocking=False):
        raise RuntimeError("个人订阅正在处理其他任务，请稍后重试")
    try:
        with tempfile.TemporaryDirectory(prefix="zhul-ai-task-") as directory:
            images = prepare_images(attachments, directory)
            request = {"provider": provider, "system_prompt": system_prompt, "text": text, "schema": schema,
                       "model": model, "effort": effort, "web_search": web_search,
                       "max_web_searches": max_web_searches, "images": images}
            process = subprocess.Popen(
                [sys.executable, str(Path(__file__).resolve()), "worker"], cwd=directory,
                env=subscription_env(), stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                text=True, encoding="utf-8", start_new_session=os.name != "nt")
            try:
                stdout, _ = process.communicate(json.dumps(request, ensure_ascii=False), timeout=timeout)
            except subprocess.TimeoutExpired as exc:
                stop_process(process)
                raise RuntimeError(f"AI 处理超过 {timeout} 秒未完成，已终止订阅进程") from exc
            finally:
                # 即使 worker 已退出，仍按原进程组清理 CLI wrapper 派生的子进程。
                if os.name != "nt":
                    try:
                        os.killpg(process.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass
            if process.returncode:
                # 只接受 worker 自己生成的安全错误码，不把 CLI stderr/认证信息带入回调。
                errors = {"auth": "未登录个人订阅；请在专用容器内运行官方 CLI 登录命令",
                          "search_limit": "联网搜索操作达到订阅模式上限，任务已终止，请拆分询盘后重试",
                          "invalid": "订阅 CLI 未返回符合约定结构的完整结果",
                          "runtime": "订阅 CLI 调用失败；请检查登录、订阅额度、模型与容器运行环境"}
                raise RuntimeError(errors.get(stdout.strip(), errors["runtime"]))
            try:
                result = json.loads(stdout)
                jsonschema.validate(result, schema)
            except (json.JSONDecodeError, jsonschema.ValidationError) as exc:
                raise RuntimeError("订阅 CLI 未返回符合约定结构的完整结果") from exc
            return result
    finally:
        _SLOT.release()


def login_status(timeout=10):
    """两个官方 CLI 是否已用个人订阅登录；只返回是否登录，不返回账号信息。"""
    env = subscription_env()
    status = {"codex": False, "claude": False}
    try:
        result = subprocess.run(["codex", "login", "status"], env=env, capture_output=True, text=True,
                                encoding="utf-8", timeout=timeout)
        # API Key 登录也会显示 Logged in，订阅只认 ChatGPT 登录
        status["codex"] = result.returncode == 0 and "chatgpt" in (result.stdout + result.stderr).lower()
    except (OSError, subprocess.TimeoutExpired):
        pass
    try:
        result = subprocess.run(["claude", "auth", "status"], env=env, capture_output=True, text=True,
                                encoding="utf-8", timeout=timeout)
        data = json.loads(result.stdout or "{}")
        status["claude"] = bool(data.get("loggedIn")) and data.get("authMethod") == "claude.ai"
    except (OSError, subprocess.TimeoutExpired, json.JSONDecodeError):
        pass
    return status


def codex_models(timeout=30):
    """Codex 订阅账号可用的模型 [{"id", "default"}]；在隔离的工作目录与环境中调用官方 SDK。"""
    with tempfile.TemporaryDirectory(prefix="zhul-ai-models-") as directory:
        process = subprocess.Popen(
            [sys.executable, str(Path(__file__).resolve()), "worker"], cwd=directory,
            env=subscription_env(), stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, encoding="utf-8", start_new_session=os.name != "nt")
        try:
            stdout, _ = process.communicate(json.dumps({"action": "models"}), timeout=timeout)
        except subprocess.TimeoutExpired as exc:
            stop_process(process)
            raise RuntimeError("获取 Codex 模型列表超时") from exc
        if process.returncode:
            if stdout.strip() == "auth":
                raise RuntimeError("Codex 未登录个人订阅")
            raise RuntimeError("获取 Codex 模型列表失败")
        try:
            return json.loads(stdout)
        except json.JSONDecodeError as exc:
            raise RuntimeError("获取 Codex 模型列表失败") from exc


class SubscriptionError(RuntimeError):
    pass


def list_codex_models():
    from openai_codex import Codex, CodexConfig

    config = CodexConfig(cwd=os.getcwd(), config_overrides=(
        'forced_login_method="chatgpt"', 'cli_auth_credentials_store="file"', 'mcp_servers={}'))
    with Codex(config) as codex:
        account = codex.account().account
        if account is None or account.root.type != "chatgpt":
            raise SubscriptionError("auth")
        return [{"id": m.model, "default": bool(m.is_default)} for m in codex.models().data if not m.hidden]


def _check_searches(seen, item_id, limit):
    seen.add(item_id)
    # 事件在调用开始时发出；达到上限立即终止，避免下一轮越限调用。
    if len(seen) >= limit:
        raise SubscriptionError("search_limit")


def run_codex(request):
    from openai_codex import ApprovalMode, Codex, CodexConfig, LocalImageInput, Sandbox, TextInput
    from openai_codex.generated.v2_all import ReasoningEffort

    config = CodexConfig(cwd=os.getcwd(), config_overrides=(
        'forced_login_method="chatgpt"', 'cli_auth_credentials_store="file"',
        'features.shell_tool=false', 'features.apps=false', 'features.hooks=false',
        'features.multi_agent=false', 'features.remote_plugin=false', 'tools.view_image=false',
        'mcp_servers={}', 'web_search="live"' if request["web_search"] else 'web_search="disabled"',
    ))
    with Codex(config) as codex:
        account = codex.account().account
        if account is None or account.root.type != "chatgpt":
            raise SubscriptionError("auth")
        thread = codex.thread_start(cwd=os.getcwd(), ephemeral=True, model=request["model"] or None,
                                    sandbox=Sandbox.read_only, approval_mode=ApprovalMode.deny_all,
                                    base_instructions=request["system_prompt"])
        inputs = [TextInput(request["text"])] + [LocalImageInput(p) for p in request["images"]]
        turn = thread.turn(inputs, effort=ReasoningEffort(request["effort"]), output_schema=request["schema"])
        seen, final, completed = set(), None, False
        for event in turn.stream():
            payload = event.payload
            if event.method in ("item/started", "item/completed"):
                item = payload.item.root
                if item.type == "webSearch" and item.id not in seen:
                    _check_searches(seen, item.id, request["max_web_searches"])
                if (event.method == "item/completed" and item.type == "agentMessage"
                        and (item.phase is None or item.phase.value == "final_answer")):
                    final = item.text
            if event.method == "turn/completed":
                if payload.turn.status.value != "completed":
                    raise SubscriptionError("runtime")
                completed = True
        if not final or not completed:
            raise SubscriptionError("invalid")
        return json.loads(final)


def claude_command(request):
    tools = []
    if request["web_search"]:
        tools += ["WebSearch", "WebFetch"]
    return ["claude", "-p", "--input-format", "stream-json", "--output-format", "stream-json",
            "--verbose", "--include-partial-messages",
            "--safe-mode", "--restricted", "--permission-mode", "dontAsk", "--permission-prompts", "none",
            "--tools", ",".join(tools), "--allowedTools", ",".join(tools), "--disallowedTools", "mcp__*",
            "--strict-mcp-config", "--mcp-config", '{"mcpServers":{}}', "--no-session-persistence",
            "--disable-slash-commands", "--no-chrome", "--model", request["model"], "--effort", request["effort"],
            "--json-schema", json.dumps(request["schema"]), "--system-prompt", request["system_prompt"]]


def run_claude(request):
    status = subprocess.run(["claude", "auth", "status"], capture_output=True, text=True, encoding="utf-8", timeout=30)
    if status.returncode or json.loads(status.stdout).get("authMethod") != "claude.ai":
        raise SubscriptionError("auth")
    content = [{"type": "image", "source": {"type": "base64", "media_type": "image/png",
                "data": base64.b64encode(Path(path).read_bytes()).decode("ascii")}} for path in request["images"]]
    content.append({"type": "text", "text": request["text"]})
    prompt = json.dumps({"type": "user", "message": {"role": "user", "content": content}}) + "\n"
    process = subprocess.Popen(claude_command(request), stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.DEVNULL, text=True, encoding="utf-8")
    process.stdin.write(prompt)
    process.stdin.close()
    seen, final = set(), None
    try:
        for line in process.stdout:
            event = json.loads(line)
            if event.get("type") == "stream_event":
                data = event.get("event", {})
                block = data.get("content_block", {})
                if data.get("type") == "content_block_start" and block.get("name") in ("WebSearch", "WebFetch"):
                    _check_searches(seen, block["id"], request["max_web_searches"])
            if event.get("type") == "result":
                if event.get("is_error") or event.get("subtype") != "success":
                    raise SubscriptionError("runtime")
                final = event.get("structured_output")
        if process.wait() or not isinstance(final, dict):
            raise SubscriptionError("invalid")
        return final
    finally:
        if process.poll() is None:
            process.kill()
        process.wait()


if __name__ == "__main__":
    try:
        request = json.load(sys.stdin)
        if request.get("action") == "models":
            print(json.dumps(list_codex_models(), ensure_ascii=False))
            sys.exit(0)
        result = run_codex(request) if request["provider"] == "openai" else run_claude(request)
        jsonschema.validate(result, request["schema"])
        print(json.dumps(result, ensure_ascii=False))
    except SubscriptionError as exc:
        print(str(exc))
        sys.exit(1)
    except (json.JSONDecodeError, jsonschema.ValidationError):
        print("invalid")
        sys.exit(1)
    except Exception:
        print("runtime")
        sys.exit(1)
