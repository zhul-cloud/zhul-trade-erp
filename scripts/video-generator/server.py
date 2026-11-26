"""视频生成服务（openspec product-video-task design 决策 1）：MoneyPrinterTurbo 外层的薄封装。

POST /generate  后端提交一个任务（带共享令牌），同一时间只跑一个，忙时返回 409；
                收到后在后台线程里：下载图片 → 按画幅裁切铺满 → 依次生成竖屏、横屏 → 一次 multipart 回调交付结果。
GET  /health    健康检查。
"""
import logging
import os
import shutil
import subprocess
import threading
import uuid

import requests
import uvicorn
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse

from crop import cover_crop

TOKEN = os.environ.get("VIDEO_TASK_CALLBACK_TOKEN") or "zhul-erp-video-callback-secret"
PORT = int(os.environ.get("VIDEO_GENERATOR_PORT") or "8900")
# MoneyPrinterTurbo 只读取 storage/local_videos 下的本地素材
MPT_ROOT = os.environ.get("MPT_ROOT") or "/MoneyPrinterTurbo"
WORK_ROOT = os.path.join(MPT_ROOT, "storage", "local_videos")
NAMES = {"9:16": "portrait", "16:9": "landscape"}
LABELS = {"9:16": "竖屏", "16:9": "横屏"}
# MoneyPrinterTurbo 失败阶段 → 给运营看的原因；英文原始错误只写在生成服务日志里
STAGE_REASONS = {
    "audio": "配音生成失败，语音服务暂时不可用，稍后重新生成",
    "materials": "图片素材处理失败，请检查图片后重新生成",
    "video": "视频合成失败，稍后重新生成",
}

log = logging.getLogger("video-generator")
logging.basicConfig(level=logging.INFO, format="[video-generator] %(message)s")

app = FastAPI()
_busy = threading.Lock()


class JobError(RuntimeError):
    pass


@app.get("/health")
def health():
    return {"busy": _busy.locked()}


@app.post("/generate")
async def generate(request: Request, x_video_token: str = Header(default="")):
    if x_video_token != TOKEN:
        raise HTTPException(status_code=401, detail="令牌不正确")
    job = await request.json()
    if not job.get("shots") or not job.get("images") or not job.get("callbackUrl"):
        raise HTTPException(status_code=400, detail="缺少镜头、图片或回调地址")
    if not _busy.acquire(blocking=False):
        return JSONResponse(status_code=409, content={"detail": "正在生成其他任务"})
    threading.Thread(target=_run, args=(job,), daemon=True).start()
    return JSONResponse(status_code=202, content={"accepted": True})


def _run(job: dict):
    work = os.path.join(WORK_ROOT, f"job-{job.get('taskId')}-{uuid.uuid4().hex[:8]}")
    try:
        os.makedirs(work, exist_ok=True)
        try:
            files = process(job, work)
        except JobError as e:
            callback(job, {"status": "failed", "error": str(e)[:500]})
            return
        except Exception as e:  # 生成过程中任何意外都要回调失败，否则任务要等后端超时
            log.exception("任务 %s 生成失败", job.get("taskId"))
            callback(job, {"status": "failed", "error": f"{type(e).__name__}: {e}"[:500]})
            return
        callback(job, {"status": "success", **files["fields"]}, files["files"])
    finally:
        shutil.rmtree(work, ignore_errors=True)
        _busy.release()


def process(job: dict, work: str) -> dict:
    """返回回调要带的字段和文件；任一画幅失败抛 JobError（两个都成功才算成功）"""
    sources = download_images(job, work)
    fields, files = {}, {}
    for aspect in job.get("aspects") or ["9:16", "16:9"]:
        name = NAMES[aspect]
        try:
            materials = []
            for i, shot in enumerate(job["shots"]):
                dst = os.path.join(work, f"{name}-{i + 1:02d}.jpg")
                cover_crop(sources[shot["image"]], dst, aspect)
                materials.append(dst)
            video = render(job, aspect, materials)
            cover = os.path.join(work, f"{name}.jpg")
            extract_cover(video, cover)
        except JobError as e:
            raise JobError(f"{LABELS[aspect]}：{e}") from e
        fields[f"{name}Duration"] = str(round(probe_duration(video)))
        files[name] = video
        files[f"{name}Cover"] = cover
    return {"fields": fields, "files": files}


def download_images(job: dict, work: str) -> list[str]:
    out = []
    for i, img in enumerate(job["images"]):
        r = requests.get(img["url"], headers={"X-Video-Token": TOKEN}, timeout=60)
        if r.status_code != 200:
            raise JobError(f"下载第 {i + 1} 张图片失败（{r.status_code}）")
        path = os.path.join(work, f"src-{i + 1:02d}")
        with open(path, "wb") as f:
            f.write(r.content)
        out.append(path)
    return out


def render(job: dict, aspect: str, materials: list[str]) -> str:
    """在本进程里调用 MoneyPrinterTurbo：本地素材按顺序拼接，字幕逐句拼成配音文案"""
    from app.models.schema import MaterialInfo, VideoParams
    from app.services import task as mpt

    seconds = sum(float(s.get("seconds") or 0) for s in job["shots"])
    params = VideoParams(
        video_subject=job.get("title") or "",
        video_script=" ".join(s["caption"].strip() for s in job["shots"]),
        video_aspect=aspect,
        video_concat_mode="sequential",
        video_clip_duration=max(1, round(seconds / len(job["shots"]))),
        video_count=1,
        video_source="local",
        video_materials=[MaterialInfo(provider="local", url=m, duration=0) for m in materials],
        video_language="en-US",
        voice_name=f"{job.get('voice') or 'en-US-GuyNeural'}-Male",
        voice_rate=float(job.get("voiceRate") or 1.1),
        subtitle_enabled=True,
        font_name="MicrosoftYaHeiBold.ttc",
        font_size=60 if aspect == "9:16" else 54,
    )
    result = mpt.start(f"erp-{job.get('taskId')}-{NAMES[aspect]}-{uuid.uuid4().hex[:6]}", params, stop_at="video")
    videos = (result or {}).get("videos") or []
    if not videos or not os.path.exists(videos[0]):
        raise JobError(failure_reason(result))
    return videos[0]


def failure_reason(result: dict | None) -> str:
    stage = (result or {}).get("failed_stage")
    return STAGE_REASONS.get(stage, "视频生成失败，稍后重新生成；多次失败请联系管理员")


def extract_cover(video: str, dst: str):
    proc = subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-ss", "1", "-i", video, "-frames:v", "1", "-q:v", "2", dst],
                          capture_output=True, text=True)
    if proc.returncode != 0 or not os.path.exists(dst):
        raise JobError(f"截取封面失败：{proc.stderr.strip()[:200]}")


def probe_duration(video: str) -> float:
    proc = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "default=nw=1:nk=1", video],
                          capture_output=True, text=True)
    try:
        return float(proc.stdout.strip())
    except ValueError as e:
        raise JobError("读取视频时长失败") from e


def callback(job: dict, fields: dict, files: dict | None = None):
    data = {"version": str(job.get("version", 0)), **fields}
    handles = {}
    try:
        for key, path in (files or {}).items():
            mime = "video/mp4" if path.endswith(".mp4") else "image/jpeg"
            handles[key] = (os.path.basename(path), open(path, "rb"), mime)
        for attempt in range(3):
            try:
                for h in handles.values():
                    h[1].seek(0)
                r = requests.post(job["callbackUrl"], data=data, files=handles or None,
                                  headers={"X-Video-Token": TOKEN}, timeout=300)
                if r.status_code == 200:
                    log.info("任务 %s 回调完成：%s", job.get("taskId"), fields.get("status"))
                    return
                log.warning("任务 %s 回调返回 %s：%s", job.get("taskId"), r.status_code, r.text[:200])
            except requests.RequestException as e:
                log.warning("任务 %s 回调失败（第 %s 次）：%s", job.get("taskId"), attempt + 1, e)
    finally:
        for h in handles.values():
            h[1].close()


if __name__ == "__main__":
    if TOKEN == "zhul-erp-video-callback-secret":
        log.warning("未配置 VIDEO_TASK_CALLBACK_TOKEN，正在使用默认令牌，生产环境必须设置")
    uvicorn.run(app, host="0.0.0.0", port=PORT)
