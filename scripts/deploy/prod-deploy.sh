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

log() { echo "[$(date '+%F %T')] $*"; }
fail() { echo "[$(date '+%F %T')] 失败：$*" >&2; exit 1; }

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
for s in "${SERVICES[@]}"; do
    docker pull -q "$REGISTRY/zhul-erp-$s:$VERSION" >/dev/null || fail "拉取 zhul-erp-$s:$VERSION 失败"
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
echo "$(date '+%F %T') $VERSION 成功 上一版=${prev:-无}" >> "$APP_DIR/releases.log"
log "全部服务健康，$VERSION 部署完成"

# 只保留最近 KEEP_VERSIONS 个版本的镜像，当前版本始终保留。
for s in "${SERVICES[@]}"; do
    docker images --format '{{.Tag}}' "$REGISTRY/zhul-erp-$s" | grep -E '^v[0-9]+\.[0-9]+\.[0-9]+$' | sort -rV \
        | tail -n +$((KEEP_VERSIONS + 1)) | while read -r old; do
            [ "$old" = "$VERSION" ] || docker rmi "$REGISTRY/zhul-erp-$s:$old" >/dev/null || true
        done
done
