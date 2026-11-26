# 视频生成服务（openspec product-video-task）：固定版本的 MoneyPrinterTurbo + 一层 FastAPI 封装（server.py）。
# 构建：docker build -t zhul-erp-video-generator:local scripts/video-generator
FROM python:3.11-slim-bookworm

# 上游固定版本，升级时改这里并单独验证（design 风险：上游内部函数可能变化）
ARG MPT_VERSION=v1.3.3

RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /MoneyPrinterTurbo
RUN curl -fsSL --retry 3 "https://github.com/harry0703/MoneyPrinterTurbo/archive/refs/tags/${MPT_VERSION}.tar.gz" \
        | tar xz --strip-components=1 \
    && cp config.example.toml config.toml \
    && pip install --no-cache-dir -r requirements.txt

COPY requirements.txt /app/requirements.txt
RUN pip install --no-cache-dir -r /app/requirements.txt
COPY crop.py server.py /app/

RUN useradd -u 10001 -m zhul && chown -R zhul /MoneyPrinterTurbo
USER zhul

ENV PYTHONPATH=/MoneyPrinterTurbo:/app \
    MPT_ROOT=/MoneyPrinterTurbo \
    VIDEO_GENERATOR_PORT=8900
EXPOSE 8900
HEALTHCHECK --interval=30s --timeout=5s --start-period=30s --retries=3 \
    CMD python -c "import socket; socket.create_connection(('127.0.0.1', 8900), 2)" || exit 1
CMD ["python", "/app/server.py"]
