# syntax=docker/dockerfile:1
# 构建上下文为 scripts/，同一镜像包含真实编排服务与 stub，由 AI_ORCHESTRATOR_MODE 选择。
FROM python:3.12-slim
WORKDIR /app
COPY ai-orchestrator/requirements.txt ./requirements.txt
RUN --mount=type=cache,target=/root/.cache/pip pip install -r requirements.txt \
    && groupadd --gid 10001 zhul \
    && useradd --uid 10001 --gid 10001 --no-create-home zhul
COPY ai-orchestrator/ ./ai-orchestrator/
COPY ai-orchestrator-stub/ ./ai-orchestrator-stub/
COPY ai-orchestrator/docker-entrypoint.sh /usr/local/bin/zhul-ai-entrypoint
# Keep shell entrypoints usable after a Windows checkout with CRLF conversion.
RUN sed -i 's/\r$//' /usr/local/bin/zhul-ai-entrypoint \
    && chmod 755 /usr/local/bin/zhul-ai-entrypoint
ENV PYTHONUNBUFFERED=1 \
    AI_STUB_PORT=8899
# 与后端同一 UID，才能读取共享的私有上传目录中的附件
USER 10001:10001
EXPOSE 8899
HEALTHCHECK --interval=15s --timeout=3s --start-period=10s --retries=3 \
    CMD python -c "import socket; socket.create_connection(('127.0.0.1', 8899), 2)" || exit 1
ENTRYPOINT ["/usr/local/bin/zhul-ai-entrypoint"]
