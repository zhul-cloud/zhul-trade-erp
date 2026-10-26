#!/usr/bin/env bash
# 生产部署脚本。安装到服务器 /opt/zhul-trade-erp/deploy.sh（root:root 700），安装方法见
# docs/06-部署运维/08-生产发版.md。GitHub Actions 用受限部署密钥登录时，authorized_keys 的
# command= 会强制执行本脚本，版本号来自 SSH_ORIGINAL_COMMAND；也可以在服务器上手动执行：
#   /opt/zhul-trade-erp/deploy.sh v1.3.0
# 仓库是公开的，Actions 日志所有人可见：这里只输出状态，不输出容器日志和配置内容。
set -euo pipefail

APP_DIR=/opt/zhul-trade-erp
REPO=zhul-cloud/zhul-trade-erp
REGISTRY=ghcr.io/zhul-cloud
SERVICES=(backend frontend ai-orchestrator)
CONTAINERS=(zhul-erp-mysql zhul-erp-redis zhul-erp-backend zhul-erp-frontend zhul-erp-ai-orchestrator)
HEALTH_TIMEOUT=600
KEEP_VERSIONS=3
PULL_ATTEMPTS=3

log() { echo "[$(date '+%F %T')] $*"; }
fail() { echo "[$(date '+%F %T')] 失败：$*" >&2; exit 1; }

# 部署成功后发通知邮件：收件人取 .env 的 DEPLOY_NOTIFY_TO（逗号分隔，留空不发），用 MAIL_* 的发信邮箱发送；
# 正文列出上一版到本版之间的提交标题（GitHub 取不到时只写版本信息）。发送失败只记日志，不影响部署结果。
notify_success() {
    APP_DIR="$APP_DIR" REPO="$REPO" VERSION="$VERSION" PREV="$1" DONE_AT="$2" python3 - <<'PY'
import json, os, smtplib, ssl, urllib.request
from email.message import EmailMessage
from email.utils import formataddr

env = {}
with open(os.path.join(os.environ["APP_DIR"], ".env"), encoding="utf-8") as f:
    for line in f:
        line = line.rstrip("\n")
        if "=" in line and not line.lstrip().startswith("#"):
            key, value = line.split("=", 1)
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "'\"":
                value = value[1:-1]
            env[key.strip()] = value

to = [a.strip() for a in env.get("DEPLOY_NOTIFY_TO", "").split(",") if a.strip()]
if not to:
    print("未设置 DEPLOY_NOTIFY_TO，不发通知邮件")
    raise SystemExit(0)
if not env.get("MAIL_HOST"):
    print("未配置发信邮箱 MAIL_HOST，不发通知邮件")
    raise SystemExit(0)

repo, version, prev, done_at = os.environ["REPO"], os.environ["VERSION"], os.environ["PREV"], os.environ["DONE_AT"]
changes = ""
if prev and prev != version:
    try:
        req = urllib.request.Request(f"https://api.github.com/repos/{repo}/compare/{prev}...{version}",
                                     headers={"Accept": "application/vnd.github+json", "User-Agent": "zhul-erp-deploy"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            commits = json.load(resp).get("commits", [])
        # 提交标题习惯写成「概括：详细说明」，邮件里只列概括，过长的截断
        titles = [c["commit"]["message"].splitlines()[0].split("：", 1)[0][:100]
                  for c in commits if len(c.get("parents", [])) < 2]
        if titles:
            shown = titles[-30:]
            changes = "本次更新：\n" + "\n".join(f"- {t}" for t in shown)
            if len(titles) > len(shown):
                changes += f"\n（共 {len(titles)} 条，只列出最近 {len(shown)} 条）"
            changes += f"\n\n完整对比：https://github.com/{repo}/compare/{prev}...{version}"
    except Exception as e:  # GitHub 取不到不影响发信
        changes = f"（未能从 GitHub 获取更新内容：{type(e).__name__}）"
elif prev == version:
    changes = "本次为重新部署同一版本。"

# CORS_ALLOWED_ORIGINS 就是用户访问的页面地址，取第一个作为网址
site = env.get("CORS_ALLOWED_ORIGINS", "").split(",")[0].strip()
lines = [f"版本：{version}（上一版 {prev or '无'}）", f"完成时间：{done_at}（北京时间）"]
if site:
    lines.append(f"网址：{site}")
body = "\n".join(lines) + ("\n\n" + changes if changes else "") + "\n"

msg = EmailMessage()
msg["Subject"] = f"[烛龙ERP] 生产发版成功 {version}"
msg["From"] = formataddr((env.get("MAIL_FROM_NAME") or "烛龙ERP", env.get("MAIL_FROM") or env.get("MAIL_USERNAME", "")))
msg["To"] = ", ".join(to)
msg.set_content(body)

port = int(env.get("MAIL_PORT") or 465)
if (env.get("MAIL_SSL") or "true").lower() == "true":
    server = smtplib.SMTP_SSL(env["MAIL_HOST"], port, context=ssl.create_default_context(), timeout=30)
else:
    server = smtplib.SMTP(env["MAIL_HOST"], port, timeout=30)
    if (env.get("MAIL_STARTTLS") or "").lower() == "true":
        server.starttls(context=ssl.create_default_context())
with server:
    if env.get("MAIL_USERNAME"):
        server.login(env["MAIL_USERNAME"], env.get("MAIL_PASSWORD", ""))
    server.send_message(msg)
# Actions 日志公开，不输出收件人地址
print(f"已发送部署通知邮件（{len(to)} 个收件人）")
PY
}

VERSION="${1:-${SSH_ORIGINAL_COMMAND:-}}"
[[ "$VERSION" =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "版本号应为 vX.Y.Z，收到 '${VERSION}'"

cd "$APP_DIR"
exec 9>"$APP_DIR/.deploy.lock"
flock -n 9 || fail "已有部署在进行"

stage=$(mktemp -d)
trap 'rm -rf "$stage"' EXIT

log "下载 $VERSION 的 compose 文件与 .env.example"
for f in docker-compose.yaml docker-compose.prod.yaml .env.example; do
    curl -fsSL --retry 3 -o "$stage/$f" "https://raw.githubusercontent.com/$REPO/$VERSION/$f" \
        || fail "下载 $f 失败"
done

missing=$(comm -23 \
    <(grep -oE '^[A-Za-z_][A-Za-z0-9_]*=' "$stage/.env.example" | tr -d '=' | sort -u) \
    <(grep -oE '^[A-Za-z_][A-Za-z0-9_]*=' .env | tr -d '=' | sort -u) | xargs)
[ -z "$missing" ] || fail "服务器 .env 缺少 $VERSION 新增的配置项：$missing（参考该版本 .env.example 补齐后重新部署）"

docker compose --project-directory "$APP_DIR" -f "$stage/docker-compose.yaml" -f "$stage/docker-compose.prod.yaml" \
    --profile app config --quiet || fail "compose 配置校验未通过"

log "拉取镜像"
# ghcr.io 偶尔响应超时，每个镜像最多拉 PULL_ATTEMPTS 次；已下载的层会保留，重试只补缺的部分。
for s in "${SERVICES[@]}"; do
    for attempt in $(seq 1 "$PULL_ATTEMPTS"); do
        docker pull -q "$REGISTRY/zhul-erp-$s:$VERSION" >/dev/null && break
        [ "$attempt" -lt "$PULL_ATTEMPTS" ] || fail "拉取 zhul-erp-$s:$VERSION 失败（已重试 $PULL_ATTEMPTS 次）"
        log "拉取 zhul-erp-$s 失败，$((attempt * 15))s 后第 $((attempt + 1)) 次重试"
        sleep $((attempt * 15))
    done
done

prev=$(docker image inspect -f '{{ index .Config.Labels "org.opencontainers.image.version" }}' \
    zhul-erp-backend:prod 2>/dev/null || true)
log "切换 :prod 到 $VERSION（上一版：${prev:-无}）"
for s in "${SERVICES[@]}"; do
    docker tag "$REGISTRY/zhul-erp-$s:$VERSION" "zhul-erp-$s:prod"
done
install -m 644 "$stage/docker-compose.yaml" "$stage/docker-compose.prod.yaml" "$APP_DIR/"

log "启动服务"
docker compose -f docker-compose.yaml -f docker-compose.prod.yaml --profile app up -d --remove-orphans

log "等待健康检查（最长 ${HEALTH_TIMEOUT}s）"
deadline=$((SECONDS + HEALTH_TIMEOUT))
while :; do
    pending=0
    for c in "${CONTAINERS[@]}"; do
        st=$(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$c" 2>/dev/null || echo missing)
        case "$st" in
            healthy) ;;
            starting) pending=1 ;;
            *)
                echo "$(date '+%F %T') $VERSION 失败 $c=$st 上一版=${prev:-无}" >> "$APP_DIR/releases.log"
                fail "$c 状态为 $st；在服务器上用 docker logs $c 查看原因，回滚可手动部署上一版 ${prev:-（无）}"
                ;;
        esac
    done
    [ "$pending" = 0 ] && break
    if [ "$SECONDS" -ge "$deadline" ]; then
        echo "$(date '+%F %T') $VERSION 超时 上一版=${prev:-无}" >> "$APP_DIR/releases.log"
        fail "等待健康检查超时；回滚可手动部署上一版 ${prev:-（无）}"
    fi
    sleep 10
done
done_at=$(date '+%F %T')
echo "$done_at $VERSION 成功 上一版=${prev:-无}" >> "$APP_DIR/releases.log"
log "全部服务健康，$VERSION 部署完成"
notify_success "$prev" "$done_at" || log "发送部署通知邮件失败（不影响部署结果）"

# 只保留最近 KEEP_VERSIONS 个版本的镜像，当前版本始终保留。
for s in "${SERVICES[@]}"; do
    docker images --format '{{.Tag}}' "$REGISTRY/zhul-erp-$s" | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -rV \
        | tail -n +$((KEEP_VERSIONS + 1)) | while read -r old; do
            [ "$old" = "$VERSION" ] || docker rmi "$REGISTRY/zhul-erp-$s:$old" >/dev/null || true
        done
done
